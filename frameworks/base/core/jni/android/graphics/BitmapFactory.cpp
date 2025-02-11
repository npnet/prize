#define LOG_TAG "BitmapFactory"

#include "BitmapFactory.h"
#include "CreateJavaOutputStreamAdaptor.h"
#include "GraphicsJNI.h"
#include "NinePatchPeeker.h"
#include "SkAndroidCodec.h"
#include "SkBRDAllocator.h"
#include "SkFrontBufferedStream.h"
#include "SkImageDecoder.h"
#include "SkMath.h"
#include "SkPixelRef.h"
#include "SkStream.h"
#include "SkUtils.h"
#include "Utils.h"
#include "core_jni_helpers.h"

#include <JNIHelp.h>
#include <androidfw/Asset.h>
#include <androidfw/ResourceTypes.h>
#include <cutils/compiler.h>
#include <memory>
#include <netinet/in.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>

#include <fcntl.h>
jfieldID gOptions_justBoundsFieldID;
jfieldID gOptions_sampleSizeFieldID;
jfieldID gOptions_configFieldID;
jfieldID gOptions_premultipliedFieldID;
jfieldID gOptions_mutableFieldID;
jfieldID gOptions_ditherFieldID;
jfieldID gOptions_preferQualityOverSpeedFieldID;
jfieldID gOptions_scaledFieldID;
jfieldID gOptions_densityFieldID;
jfieldID gOptions_screenDensityFieldID;
jfieldID gOptions_targetDensityFieldID;
jfieldID gOptions_widthFieldID;
jfieldID gOptions_heightFieldID;
jfieldID gOptions_mimeFieldID;
jfieldID gOptions_mCancelID;
jfieldID gOptions_bitmapFieldID;

jfieldID gBitmap_ninePatchInsetsFieldID;

jclass gInsetStruct_class;
jmethodID gInsetStruct_constructorMethodID;
#ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
jfieldID gOptions_postprocFieldID;
jfieldID gOptions_postprocflagFieldID;
#endif

using namespace android;

jstring encodedFormatToString(JNIEnv* env, SkEncodedFormat format) {
    const char* mimeType;
    switch (format) {
        case SkEncodedFormat::kBMP_SkEncodedFormat:
            mimeType = "image/bmp";
            break;
        case SkEncodedFormat::kGIF_SkEncodedFormat:
            mimeType = "image/gif";
            break;
        case SkEncodedFormat::kICO_SkEncodedFormat:
            mimeType = "image/x-ico";
            break;
        case SkEncodedFormat::kJPEG_SkEncodedFormat:
            mimeType = "image/jpeg";
            break;
        case SkEncodedFormat::kPNG_SkEncodedFormat:
            mimeType = "image/png";
            break;
        case SkEncodedFormat::kWEBP_SkEncodedFormat:
            mimeType = "image/webp";
            break;
        case SkEncodedFormat::kWBMP_SkEncodedFormat:
            mimeType = "image/vnd.wap.wbmp";
            break;
        case SkEncodedFormat::kRAW_SkEncodedFormat:
            mimeType = "image/x-adobe-dng";
            break;
        default:
            mimeType = nullptr;
            break;
    }

    jstring jstr = nullptr;
    if (mimeType) {
        // NOTE: Caller should env->ExceptionCheck() for OOM
        // (can't check for nullptr as it's a valid return value)
        jstr = env->NewStringUTF(mimeType);
    }
    return jstr;
}

static void scaleDivRange(int32_t* divs, int count, float scale, int maxValue) {
    for (int i = 0; i < count; i++) {
        divs[i] = int32_t(divs[i] * scale + 0.5f);
        if (i > 0 && divs[i] == divs[i - 1]) {
            divs[i]++; // avoid collisions
        }
    }

    if (CC_UNLIKELY(divs[count - 1] > maxValue)) {
        // if the collision avoidance above put some divs outside the bounds of the bitmap,
        // slide outer stretchable divs inward to stay within bounds
        int highestAvailable = maxValue;
        for (int i = count - 1; i >= 0; i--) {
            divs[i] = highestAvailable;
            if (i > 0 && divs[i] <= divs[i-1]){
                // keep shifting
                highestAvailable = divs[i] - 1;
            } else {
                break;
            }
        }
    }
}

static void scaleNinePatchChunk(android::Res_png_9patch* chunk, float scale,
        int scaledWidth, int scaledHeight) {
    chunk->paddingLeft = int(chunk->paddingLeft * scale + 0.5f);
    chunk->paddingTop = int(chunk->paddingTop * scale + 0.5f);
    chunk->paddingRight = int(chunk->paddingRight * scale + 0.5f);
    chunk->paddingBottom = int(chunk->paddingBottom * scale + 0.5f);

    scaleDivRange(chunk->getXDivs(), chunk->numXDivs, scale, scaledWidth);
    scaleDivRange(chunk->getYDivs(), chunk->numYDivs, scale, scaledHeight);
}

static SkColorType colorTypeForScaledOutput(SkColorType colorType) {
    switch (colorType) {
        case kUnknown_SkColorType:
        case kIndex_8_SkColorType:
            return kN32_SkColorType;
        default:
            break;
    }
    return colorType;
}

class ScaleCheckingAllocator : public SkBitmap::HeapAllocator {
public:
    ScaleCheckingAllocator(float scale, int size)
            : mScale(scale), mSize(size) {
    }

    virtual bool allocPixelRef(SkBitmap* bitmap, SkColorTable* ctable) {
        // accounts for scale in final allocation, using eventual size and config
        const int bytesPerPixel = SkColorTypeBytesPerPixel(
                colorTypeForScaledOutput(bitmap->colorType()));
        const int requestedSize = bytesPerPixel *
                int(bitmap->width() * mScale + 0.5f) *
                int(bitmap->height() * mScale + 0.5f);
        if (requestedSize > mSize) {
            ALOGW("bitmap for alloc reuse (%d bytes) can't fit scaled bitmap (%d bytes)",
                    mSize, requestedSize);
            return false;
        }
        return SkBitmap::HeapAllocator::allocPixelRef(bitmap, ctable);
    }
private:
    const float mScale;
    const int mSize;
};

class RecyclingPixelAllocator : public SkBitmap::Allocator {
public:
    RecyclingPixelAllocator(android::Bitmap* bitmap, unsigned int size)
            : mBitmap(bitmap), mSize(size) {
    }

    ~RecyclingPixelAllocator() {
    }

    virtual bool allocPixelRef(SkBitmap* bitmap, SkColorTable* ctable) {
        const SkImageInfo& info = bitmap->info();
        if (info.colorType() == kUnknown_SkColorType) {
            ALOGW("unable to reuse a bitmap as the target has an unknown bitmap configuration");
            return false;
        }

        const int64_t size64 = info.getSafeSize64(bitmap->rowBytes());
        if (!sk_64_isS32(size64)) {
            ALOGW("bitmap is too large");
            return false;
        }

        const size_t size = sk_64_asS32(size64);
        if (size > mSize) {
            ALOGW("bitmap marked for reuse (%u bytes) can't fit new bitmap "
                  "(%zu bytes)", mSize, size);
            return false;
        }

        mBitmap->reconfigure(info, bitmap->rowBytes(), ctable);
        bitmap->setPixelRef(mBitmap->refPixelRef())->unref();

        // since we're already allocated, we lockPixels right away
        // HeapAllocator/JavaPixelAllocator behaves this way too
        bitmap->lockPixels();
        return true;
    }

private:
    android::Bitmap* const mBitmap;
    const unsigned int mSize;
};

// Necessary for decodes when the native decoder cannot scale to appropriately match the sampleSize
// (for example, RAW). If the sampleSize divides evenly into the dimension, we require that the
// scale matches exactly. If sampleSize does not divide evenly, we allow the decoder to choose how
// best to round.
static bool needsFineScale(const int fullSize, const int decodedSize, const int sampleSize) {
    if (fullSize % sampleSize == 0 && fullSize / sampleSize != decodedSize) {
        return true;
    } else if ((fullSize / sampleSize + 1) != decodedSize &&
               (fullSize / sampleSize) != decodedSize) {
        return true;
    }
    return false;
}

static bool needsFineScale(const SkISize fullSize, const SkISize decodedSize,
                           const int sampleSize) {
    return needsFineScale(fullSize.width(), decodedSize.width(), sampleSize) ||
           needsFineScale(fullSize.height(), decodedSize.height(), sampleSize);
}

#define BUFSIZE 255


int cmp_cmdline(const char *command, int pid){
    char buf[BUFSIZE];
    char filename[BUFSIZE];

    memset(buf, '\0', BUFSIZE);

    /* first read the proc entry */
    sprintf(filename, "/proc/%d/cmdline", pid);

    //ALOGW("bitmap marked  pid:%d, filename:%s",pid,filename);
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        return 0;
    }
    const int len = read(fd, buf, BUFSIZE);
    close(fd);

    if (len < 0) {
      return 0;
    }

    buf[len] = 0;
    for (int i=0; i<len; i++) {
        if (buf[i] == ' ') {
            buf[i] = 0;
            break;
        }
    }
    //ALOGW("bitmap marked  pid:%d, buf:%s",pid,buf);

    /* check if this is the command we're looking for. */
    if (strcmp(command, buf) == 0)
    return 1;
    else
    return 0;
}
static jobject doDecode(JNIEnv* env, SkStreamRewindable* stream, jobject padding, jobject options) {
    // This function takes ownership of the input stream.  Since the SkAndroidCodec
    // will take ownership of the stream, we don't necessarily need to take ownership
    // here.  This is a precaution - if we were to return before creating the codec,
    // we need to make sure that we delete the stream.
    std::unique_ptr<SkStreamRewindable> streamDeleter(stream);

    // Set default values for the options parameters.
    int sampleSize = 1;
    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
    int postproc = 0;
    int postprocflag = 0;
    #endif
    bool onlyDecodeSize = false;
    SkColorType prefColorType = kN32_SkColorType;
    bool isMutable = false;
    float scale = 1.0f;
    bool requireUnpremultiplied = false;
    jobject javaBitmap = NULL;

    // Update with options supplied by the client.
    if (options != NULL) {
        sampleSize = env->GetIntField(options, gOptions_sampleSizeFieldID);
        // Correct a non-positive sampleSize.  sampleSize defaults to zero within the
        // options object, which is strange.
        if (sampleSize <= 0) {
            sampleSize = 1;
        }

        if (env->GetBooleanField(options, gOptions_justBoundsFieldID)) {
            onlyDecodeSize = true;
        }

        // initialize these, in case we fail later on
        env->SetIntField(options, gOptions_widthFieldID, -1);
        env->SetIntField(options, gOptions_heightFieldID, -1);
        env->SetObjectField(options, gOptions_mimeFieldID, 0);

        jobject jconfig = env->GetObjectField(options, gOptions_configFieldID);
        prefColorType = GraphicsJNI::getNativeBitmapColorType(env, jconfig);
        isMutable = env->GetBooleanField(options, gOptions_mutableFieldID);
        requireUnpremultiplied = !env->GetBooleanField(options, gOptions_premultipliedFieldID);
        javaBitmap = env->GetObjectField(options, gOptions_bitmapFieldID);
        #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        postproc = env->GetBooleanField(options, gOptions_postprocFieldID);
        postprocflag = env->GetIntField(options, gOptions_postprocflagFieldID);
        #endif

        if (env->GetBooleanField(options, gOptions_scaledFieldID)) {
            const int density = env->GetIntField(options, gOptions_densityFieldID);
            const int targetDensity = env->GetIntField(options, gOptions_targetDensityFieldID);
            const int screenDensity = env->GetIntField(options, gOptions_screenDensityFieldID);
            if (density != 0 && targetDensity != 0 && density != screenDensity) {
                scale = (float) targetDensity / density;
            }
        }
    }

    // Create the codec.
    NinePatchPeeker peeker;
    std::unique_ptr<SkAndroidCodec> codec(SkAndroidCodec::NewFromStream(streamDeleter.release(),
            &peeker));
    if (!codec.get()) {
        return nullObjectReturn("SkAndroidCodec::NewFromStream returned null");
    }

    // Do not allow ninepatch decodes to 565.  In the past, decodes to 565
    // would dither, and we do not want to pre-dither ninepatches, since we
    // know that they will be stretched.  We no longer dither 565 decodes,
    // but we continue to prevent ninepatches from decoding to 565, in order
    // to maintain the old behavior.
    if (peeker.mPatch && kRGB_565_SkColorType == prefColorType) {
        prefColorType = kN32_SkColorType;
    }

    // Determine the output size.
    SkISize size = codec->getSampledDimensions(sampleSize);

    int scaledWidth = size.width();
    int scaledHeight = size.height();
    bool willScale = false;
    int bitSize = 2;
    long memKbSize;
    int tencent_mm = cmp_cmdline("com.tencent.mm",getpid());
    if(prefColorType == kAlpha_8_SkColorType) bitSize = 1;
    if(prefColorType == kRGB_565_SkColorType) bitSize = 2;
    if(prefColorType == kARGB_4444_SkColorType) bitSize = 2;
    if(prefColorType == kN32_SkColorType) bitSize = 4;
    if(tencent_mm && bitSize > 0){
        memKbSize = scaledWidth * scaledHeight * bitSize / 1024;
        if(memKbSize > 102400){ //102400 = 100MB
            if(sampleSize < 2){
                sampleSize = 2;
            }

            if(bitSize == 4){
                prefColorType = kRGB_565_SkColorType;
            }

            if (peeker.mPatch && kRGB_565_SkColorType == prefColorType) {
                prefColorType = kN32_SkColorType;
            }

        }

        ALOGW("bitmap marked  scaledWidth:%d, scaledHeight:%d,  bit:%d,memSize:%dKB", scaledWidth, scaledHeight,bitSize,memKbSize);
    }

    // Apply a fine scaling step if necessary.
    if (needsFineScale(codec->getInfo().dimensions(), size, sampleSize)) {
        willScale = true;
        scaledWidth = codec->getInfo().width() / sampleSize;
        scaledHeight = codec->getInfo().height() / sampleSize;
    }

    // Set the options and return if the client only wants the size.
    if (options != NULL) {
        jstring mimeType = encodedFormatToString(env, codec->getEncodedFormat());
        if (env->ExceptionCheck()) {
            return nullObjectReturn("OOM in encodedFormatToString()");
        }
        env->SetIntField(options, gOptions_widthFieldID, scaledWidth);
        env->SetIntField(options, gOptions_heightFieldID, scaledHeight);
        env->SetObjectField(options, gOptions_mimeFieldID, mimeType);

        #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        if (codec->getEncodedFormat() == SkEncodedFormat::kJPEG_SkEncodedFormat) {
            codec->setPostProcFlag(postproc | (postprocflag << 4));
        }
        #endif
        if (onlyDecodeSize) {
            return nullptr;
        }
    }

    // Scale is necessary due to density differences.
    if (scale != 1.0f) {
        willScale = true;
        scaledWidth = static_cast<int>(scaledWidth * scale + 0.5f);
        scaledHeight = static_cast<int>(scaledHeight * scale + 0.5f);
    }

    android::Bitmap* reuseBitmap = nullptr;
    unsigned int existingBufferSize = 0;
    if (javaBitmap != NULL) {
        reuseBitmap = GraphicsJNI::getBitmap(env, javaBitmap);
        if (reuseBitmap->peekAtPixelRef()->isImmutable()) {
            ALOGW("Unable to reuse an immutable bitmap as an image decoder target.");
            javaBitmap = NULL;
            reuseBitmap = nullptr;
        } else {
            existingBufferSize = GraphicsJNI::getBitmapAllocationByteCount(env, javaBitmap);
        }
    }

    JavaPixelAllocator javaAllocator(env);
    RecyclingPixelAllocator recyclingAllocator(reuseBitmap, existingBufferSize);
    ScaleCheckingAllocator scaleCheckingAllocator(scale, existingBufferSize);
    SkBitmap::HeapAllocator heapAllocator;
    SkBitmap::Allocator* decodeAllocator;
    if (javaBitmap != nullptr && willScale) {
        // This will allocate pixels using a HeapAllocator, since there will be an extra
        // scaling step that copies these pixels into Java memory.  This allocator
        // also checks that the recycled javaBitmap is large enough.
        decodeAllocator = &scaleCheckingAllocator;
    } else if (javaBitmap != nullptr) {
        decodeAllocator = &recyclingAllocator;
    } else if (willScale) {
        // This will allocate pixels using a HeapAllocator, since there will be an extra
        // scaling step that copies these pixels into Java memory.
        decodeAllocator = &heapAllocator;
    } else {
        decodeAllocator = &javaAllocator;
    }

    // Set the decode colorType.  This is necessary because we can't always support
    // the requested colorType.
    SkColorType decodeColorType = codec->computeOutputColorType(prefColorType);

    // Construct a color table for the decode if necessary
    SkAutoTUnref<SkColorTable> colorTable(nullptr);
    SkPMColor* colorPtr = nullptr;
    int* colorCount = nullptr;
    int maxColors = 256;
    SkPMColor colors[256];
    if (kIndex_8_SkColorType == decodeColorType) {
        colorTable.reset(new SkColorTable(colors, maxColors));

        // SkColorTable expects us to initialize all of the colors before creating an
        // SkColorTable.  However, we are using SkBitmap with an Allocator to allocate
        // memory for the decode, so we need to create the SkColorTable before decoding.
        // It is safe for SkAndroidCodec to modify the colors because this SkBitmap is
        // not being used elsewhere.
        colorPtr = const_cast<SkPMColor*>(colorTable->readColors());
        colorCount = &maxColors;
    }

    // Set the alpha type for the decode.
    SkAlphaType alphaType = codec->computeOutputAlphaType(requireUnpremultiplied);

    const SkImageInfo decodeInfo = SkImageInfo::Make(size.width(), size.height(), decodeColorType,
            alphaType);
    SkImageInfo bitmapInfo = decodeInfo;
    if (decodeColorType == kGray_8_SkColorType) {
        // The legacy implementation of BitmapFactory used kAlpha8 for
        // grayscale images (before kGray8 existed).  While the codec
        // recognizes kGray8, we need to decode into a kAlpha8 bitmap
        // in order to avoid a behavior change.
        bitmapInfo = SkImageInfo::MakeA8(size.width(), size.height());
    }
    SkBitmap decodingBitmap;
    if (!decodingBitmap.setInfo(bitmapInfo) ||
            !decodingBitmap.tryAllocPixels(decodeAllocator, colorTable)) {
        // SkAndroidCodec should recommend a valid SkImageInfo, so setInfo()
        // should only only fail if the calculated value for rowBytes is too
        // large.
        // tryAllocPixels() can fail due to OOM on the Java heap, OOM on the
        // native heap, or the recycled javaBitmap being too small to reuse.
        return nullptr;
    }

    // Use SkAndroidCodec to perform the decode.
    SkAndroidCodec::AndroidOptions codecOptions;
    codecOptions.fZeroInitialized = (decodeAllocator == &javaAllocator) ?
            SkCodec::kYes_ZeroInitialized : SkCodec::kNo_ZeroInitialized;
    codecOptions.fColorPtr = colorPtr;
    codecOptions.fColorCount = colorCount;
    codecOptions.fSampleSize = sampleSize;
    SkCodec::Result result = codec->getAndroidPixels(decodeInfo, decodingBitmap.getPixels(),
            decodingBitmap.rowBytes(), &codecOptions);
    switch (result) {
        case SkCodec::kSuccess:
        case SkCodec::kIncompleteInput:
            break;
        default:
            return nullObjectReturn("codec->getAndroidPixels() failed.");
    }

    jbyteArray ninePatchChunk = NULL;
    if (peeker.mPatch != NULL) {
        if (willScale) {
            scaleNinePatchChunk(peeker.mPatch, scale, scaledWidth, scaledHeight);
        }

        size_t ninePatchArraySize = peeker.mPatch->serializedSize();
        ninePatchChunk = env->NewByteArray(ninePatchArraySize);
        if (ninePatchChunk == NULL) {
            return nullObjectReturn("ninePatchChunk == null");
        }

        jbyte* array = (jbyte*) env->GetPrimitiveArrayCritical(ninePatchChunk, NULL);
        if (array == NULL) {
            return nullObjectReturn("primitive array == null");
        }

        memcpy(array, peeker.mPatch, peeker.mPatchSize);
        env->ReleasePrimitiveArrayCritical(ninePatchChunk, array, 0);
    }

    jobject ninePatchInsets = NULL;
    if (peeker.mHasInsets) {
        ninePatchInsets = env->NewObject(gInsetStruct_class, gInsetStruct_constructorMethodID,
                peeker.mOpticalInsets[0], peeker.mOpticalInsets[1], peeker.mOpticalInsets[2], peeker.mOpticalInsets[3],
                peeker.mOutlineInsets[0], peeker.mOutlineInsets[1], peeker.mOutlineInsets[2], peeker.mOutlineInsets[3],
                peeker.mOutlineRadius, peeker.mOutlineAlpha, scale);
        if (ninePatchInsets == NULL) {
            return nullObjectReturn("nine patch insets == null");
        }
        if (javaBitmap != NULL) {
            env->SetObjectField(javaBitmap, gBitmap_ninePatchInsetsFieldID, ninePatchInsets);
        }
    }

    SkBitmap outputBitmap;
    if (willScale) {
        // This is weird so let me explain: we could use the scale parameter
        // directly, but for historical reasons this is how the corresponding
        // Dalvik code has always behaved. We simply recreate the behavior here.
        // The result is slightly different from simply using scale because of
        // the 0.5f rounding bias applied when computing the target image size
        const float sx = scaledWidth / float(decodingBitmap.width());
        const float sy = scaledHeight / float(decodingBitmap.height());

        // Set the allocator for the outputBitmap.
        SkBitmap::Allocator* outputAllocator;
        if (javaBitmap != nullptr) {
            outputAllocator = &recyclingAllocator;
        } else {
            outputAllocator = &javaAllocator;
        }

        SkColorType scaledColorType = colorTypeForScaledOutput(decodingBitmap.colorType());
        // FIXME: If the alphaType is kUnpremul and the image has alpha, the
        // colors may not be correct, since Skia does not yet support drawing
        // to/from unpremultiplied bitmaps.
        outputBitmap.setInfo(SkImageInfo::Make(scaledWidth, scaledHeight,
                scaledColorType, decodingBitmap.alphaType()));
        if (!outputBitmap.tryAllocPixels(outputAllocator, NULL)) {
            // This should only fail on OOM.  The recyclingAllocator should have
            // enough memory since we check this before decoding using the
            // scaleCheckingAllocator.
            return nullObjectReturn("allocation failed for scaled bitmap");
        }

        SkPaint paint;
        // kSrc_Mode instructs us to overwrite the unininitialized pixels in
        // outputBitmap.  Otherwise we would blend by default, which is not
        // what we want.
        paint.setXfermodeMode(SkXfermode::kSrc_Mode);
        paint.setFilterQuality(kLow_SkFilterQuality);

        SkCanvas canvas(outputBitmap);
        canvas.scale(sx, sy);
        canvas.drawBitmap(decodingBitmap, 0.0f, 0.0f, &paint);
    } else {
        outputBitmap.swap(decodingBitmap);
    }

    if (padding) {
        if (peeker.mPatch != NULL) {
            GraphicsJNI::set_jrect(env, padding,
                    peeker.mPatch->paddingLeft, peeker.mPatch->paddingTop,
                    peeker.mPatch->paddingRight, peeker.mPatch->paddingBottom);
        } else {
            GraphicsJNI::set_jrect(env, padding, -1, -1, -1, -1);
        }
    }

    // If we get here, the outputBitmap should have an installed pixelref.
    if (outputBitmap.pixelRef() == NULL) {
        return nullObjectReturn("Got null SkPixelRef");
    }

    if (!isMutable && javaBitmap == NULL) {
        // promise we will never change our pixels (great for sharing and pictures)
        outputBitmap.setImmutable();
    }

    bool isPremultiplied = !requireUnpremultiplied;
    if (javaBitmap != nullptr) {
        GraphicsJNI::reinitBitmap(env, javaBitmap, outputBitmap.info(), isPremultiplied);
        outputBitmap.notifyPixelsChanged();
        // If a java bitmap was passed in for reuse, pass it back
        return javaBitmap;
    }

    int bitmapCreateFlags = 0x0;
    if (isMutable) bitmapCreateFlags |= GraphicsJNI::kBitmapCreateFlag_Mutable;
    if (isPremultiplied) bitmapCreateFlags |= GraphicsJNI::kBitmapCreateFlag_Premultiplied;

    // now create the java bitmap
    return GraphicsJNI::createBitmap(env, javaAllocator.getStorageObjAndReset(),
            bitmapCreateFlags, ninePatchChunk, ninePatchInsets, -1);
}

/// M: Add for DRM, check whether is drm image {@
static bool isDrm(char* header) {
    if (header == NULL) {
        return false;
    }
    // 1. CTA5: check magic(CTA5)
    const size_t CTA_LEN = 4;
    const char CTA_MAGIC[CTA_LEN + 1] = "CTA5";
    if (memcmp(header, CTA_MAGIC, CTA_LEN) == 0) {
        return true;
    }

    // 2. OMADRM: check version(1) and mimetpe(image/)
    int version = header[0];
    if (version != 1) {
        return false;
    }
    const size_t MAX_MIME_LEN = 64;
    size_t mimeLen = header[1];
    if (mimeLen > MAX_MIME_LEN) {
        return false;
    }
    const size_t IMAGE_MIME_LEN = 6;
    const char IMAGE_MIME[IMAGE_MIME_LEN + 1] = "image/";
    if (memcmp(header + 3, IMAGE_MIME, IMAGE_MIME_LEN) != 0) {
        return false;
    }
    return true;
}

static jobject nativeDecodeStream(JNIEnv* env, jobject clazz, jobject is, jbyteArray storage,
        jobject padding, jobject options) {

    jobject bitmap = NULL;
    std::unique_ptr<SkStream> stream(CreateJavaInputStreamAdaptor(env, is, storage));

    if (stream.get()) {
        std::unique_ptr<SkStreamRewindable> bufferedStream(
                SkFrontBufferedStream::Create(stream.release(), SkCodec::MinBufferedBytesNeeded()));
        SkASSERT(bufferedStream.get() != NULL);
        /// M: Add for DRM, read header from bufferedStream first to check whether is drm image {@
        size_t headerSize = SkCodec::MinBufferedBytesNeeded();
        char* header = new char[headerSize];
        bufferedStream.get()->read(header, headerSize);
        if (isDrm(header)) {
            jbyte* storageToByte = env->GetByteArrayElements(storage, NULL);
            /// first two byte store header size
            storageToByte[0] = (jbyte) (0xff & headerSize);
            storageToByte[1] = (jbyte) (0xff00 & (headerSize >> 8));
            memcpy(storageToByte + 2, header, headerSize);
            env->ReleaseByteArrayElements(storage, storageToByte, 0);
            /// set width, height and mimetype as failed value if need
            if (options != NULL) {
                env->SetIntField(options, gOptions_widthFieldID, -1);
                env->SetIntField(options, gOptions_heightFieldID, -1);
                env->SetObjectField(options, gOptions_mimeFieldID, 0);
            }
            delete header; header = NULL;
            return NULL;
        }
        delete header; header = NULL;
        bufferedStream.get()->rewind();
        /// @}

        bitmap = doDecode(env, bufferedStream.release(), padding, options);
    }
    return bitmap;
}

static jobject nativeDecodeFileDescriptor(JNIEnv* env, jobject clazz, jobject fileDescriptor,
        jobject padding, jobject bitmapFactoryOptions) {

    NPE_CHECK_RETURN_ZERO(env, fileDescriptor);

    int descriptor = jniGetFDFromFileDescriptor(env, fileDescriptor);

    struct stat fdStat;
    if (fstat(descriptor, &fdStat) == -1) {
        doThrowIOE(env, "broken file descriptor");
        return nullObjectReturn("fstat return -1");
    }

    // Restore the descriptor's offset on exiting this function. Even though
    // we dup the descriptor, both the original and dup refer to the same open
    // file description and changes to the file offset in one impact the other.
    AutoFDSeek autoRestore(descriptor);

    // Duplicate the descriptor here to prevent leaking memory. A leak occurs
    // if we only close the file descriptor and not the file object it is used to
    // create.  If we don't explicitly clean up the file (which in turn closes the
    // descriptor) the buffers allocated internally by fseek will be leaked.
    int dupDescriptor = dup(descriptor);

    FILE* file = fdopen(dupDescriptor, "r");
    if (file == NULL) {
        // cleanup the duplicated descriptor since it will not be closed when the
        // file is cleaned up (fclose).
        close(dupDescriptor);
        return nullObjectReturn("Could not open file");
    }

    std::unique_ptr<SkFILEStream> fileStream(new SkFILEStream(file));

    // If there is no offset for the file descriptor, we use SkFILEStream directly.
    if (::lseek(descriptor, 0, SEEK_CUR) == 0) {
        assert(isSeekable(dupDescriptor));
        return doDecode(env, fileStream.release(), padding, bitmapFactoryOptions);
    }

    // Use a buffered stream. Although an SkFILEStream can be rewound, this
    // ensures that SkImageDecoder::Factory never rewinds beyond the
    // current position of the file descriptor.
    std::unique_ptr<SkStreamRewindable> stream(SkFrontBufferedStream::Create(fileStream.release(),
            SkCodec::MinBufferedBytesNeeded()));

    return doDecode(env, stream.release(), padding, bitmapFactoryOptions);
}

static jobject nativeDecodeAsset(JNIEnv* env, jobject clazz, jlong native_asset,
        jobject padding, jobject options) {

    Asset* asset = reinterpret_cast<Asset*>(native_asset);
    // since we know we'll be done with the asset when we return, we can
    // just use a simple wrapper
    std::unique_ptr<AssetStreamAdaptor> stream(new AssetStreamAdaptor(asset));
    return doDecode(env, stream.release(), padding, options);
}

static jobject nativeDecodeByteArray(JNIEnv* env, jobject, jbyteArray byteArray,
        jint offset, jint length, jobject options) {

    AutoJavaByteArray ar(env, byteArray);
    std::unique_ptr<SkMemoryStream> stream(new SkMemoryStream(ar.ptr() + offset, length, false));
    return doDecode(env, stream.release(), NULL, options);
}

static jboolean nativeIsSeekable(JNIEnv* env, jobject, jobject fileDescriptor) {
    jint descriptor = jniGetFDFromFileDescriptor(env, fileDescriptor);
    return isSeekable(descriptor) ? JNI_TRUE : JNI_FALSE;
}

jobject decodeBitmap(JNIEnv* env, void* data, size_t size) {
    SkMemoryStream stream(data, size);
    return doDecode(env, &stream, NULL, NULL);
}

///////////////////////////////////////////////////////////////////////////////

static const JNINativeMethod gMethods[] = {
    {   "nativeDecodeStream",
        "(Ljava/io/InputStream;[BLandroid/graphics/Rect;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeStream
    },

    {   "nativeDecodeFileDescriptor",
        "(Ljava/io/FileDescriptor;Landroid/graphics/Rect;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeFileDescriptor
    },

    {   "nativeDecodeAsset",
        "(JLandroid/graphics/Rect;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeAsset
    },

    {   "nativeDecodeByteArray",
        "([BIILandroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeByteArray
    },

    {   "nativeIsSeekable",
        "(Ljava/io/FileDescriptor;)Z",
        (void*)nativeIsSeekable
    },
};

int register_android_graphics_BitmapFactory(JNIEnv* env) {
    jclass options_class = FindClassOrDie(env, "android/graphics/BitmapFactory$Options");
    gOptions_bitmapFieldID = GetFieldIDOrDie(env, options_class, "inBitmap",
            "Landroid/graphics/Bitmap;");
    gOptions_justBoundsFieldID = GetFieldIDOrDie(env, options_class, "inJustDecodeBounds", "Z");
    gOptions_sampleSizeFieldID = GetFieldIDOrDie(env, options_class, "inSampleSize", "I");
    gOptions_configFieldID = GetFieldIDOrDie(env, options_class, "inPreferredConfig",
            "Landroid/graphics/Bitmap$Config;");
    gOptions_premultipliedFieldID = GetFieldIDOrDie(env, options_class, "inPremultiplied", "Z");
    gOptions_mutableFieldID = GetFieldIDOrDie(env, options_class, "inMutable", "Z");
    gOptions_ditherFieldID = GetFieldIDOrDie(env, options_class, "inDither", "Z");
    gOptions_preferQualityOverSpeedFieldID = GetFieldIDOrDie(env, options_class,
            "inPreferQualityOverSpeed", "Z");
    gOptions_scaledFieldID = GetFieldIDOrDie(env, options_class, "inScaled", "Z");
    gOptions_densityFieldID = GetFieldIDOrDie(env, options_class, "inDensity", "I");
    gOptions_screenDensityFieldID = GetFieldIDOrDie(env, options_class, "inScreenDensity", "I");
    gOptions_targetDensityFieldID = GetFieldIDOrDie(env, options_class, "inTargetDensity", "I");
    gOptions_widthFieldID = GetFieldIDOrDie(env, options_class, "outWidth", "I");
    gOptions_heightFieldID = GetFieldIDOrDie(env, options_class, "outHeight", "I");
    gOptions_mimeFieldID = GetFieldIDOrDie(env, options_class, "outMimeType", "Ljava/lang/String;");
    gOptions_mCancelID = GetFieldIDOrDie(env, options_class, "mCancel", "Z");
    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
    gOptions_postprocFieldID = GetFieldIDOrDie(env, options_class, "inPostProc", "Z");
    gOptions_postprocflagFieldID = GetFieldIDOrDie(env, options_class, "inPostProcFlag", "I");
    #endif
    jclass bitmap_class = FindClassOrDie(env, "android/graphics/Bitmap");
    gBitmap_ninePatchInsetsFieldID = GetFieldIDOrDie(env, bitmap_class, "mNinePatchInsets",
            "Landroid/graphics/NinePatch$InsetStruct;");

    gInsetStruct_class = MakeGlobalRefOrDie(env, FindClassOrDie(env,
        "android/graphics/NinePatch$InsetStruct"));
    gInsetStruct_constructorMethodID = GetMethodIDOrDie(env, gInsetStruct_class, "<init>",
                                                        "(IIIIIIIIFIF)V");

    return android::RegisterMethodsOrDie(env, "android/graphics/BitmapFactory",
                                         gMethods, NELEM(gMethods));
}
