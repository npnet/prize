/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;

import android.content.res.AssetManager;
//========================lovelyfonts start========================
import android.graphics.Canvas;
import android.os.SystemProperties;
//========================lovelyfonts end========================
import android.util.Log;
import android.util.LongSparseArray;
import android.util.LruCache;
import android.util.SparseArray;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
///================founder font add start================
import android.os.SystemProperties;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
///================founder font add end================
/**
 * The Typeface class specifies the typeface and intrinsic style of a font.
 * This is used in the paint, along with optionally Paint settings like
 * textSize, textSkewX, textScaleX to specify
 * how text appears when drawn (and measured).
 */
public class Typeface {

    private static String TAG = "Typeface";

    /** The default NORMAL typeface object */
    public static /*final*/ Typeface DEFAULT;
    /**
     * The default BOLD typeface object. Note: this may be not actually be
     * bold, depending on what fonts are installed. Call getStyle() to know
     * for sure.
     */
	 ///================founder font modify start================
    public static /*final*/ Typeface DEFAULT_BOLD;
    /** The NORMAL style of the default sans serif typeface. */
    public static /*final*/ Typeface SANS_SERIF;
    /** The NORMAL style of the default serif typeface. */
    public static /*final*/ Typeface SERIF;
    /** The NORMAL style of the default monospace typeface. */
    public static /*final*/ Typeface MONOSPACE;
	///================founder font modify end================

    static Typeface[] sDefaults;
    private static final LongSparseArray<SparseArray<Typeface>> sTypefaceCache =
            new LongSparseArray<SparseArray<Typeface>>(3);

    /**
     * Cache for Typeface objects dynamically loaded from assets. Currently max size is 16.
     */
    private static final LruCache<String, Typeface> sDynamicTypefaceCache = new LruCache<>(16);

    static Typeface sDefaultTypeface;
    static Map<String, Typeface> sSystemFontMap;
    static FontFamily[] sFallbackFonts;

    static final String FONTS_CONFIG = "fonts.xml";

    /**
     * @hide
     */
    public long native_instance;

    // Style
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    private int mStyle = 0;

    private static void setDefault(Typeface t) {
        sDefaultTypeface = t;
        nativeSetDefault(t.native_instance);
    }

    /** Returns the typeface's intrinsic style attributes */
    public int getStyle() {
        return mStyle;
    }

    /** Returns true if getStyle() has the BOLD bit set. */
    public final boolean isBold() {
        return (mStyle & BOLD) != 0;
    }

    /** Returns true if getStyle() has the ITALIC bit set. */
    public final boolean isItalic() {
        return (mStyle & ITALIC) != 0;
    }

    /**
     * Create a typeface object given a family name, and option style information.
     * If null is passed for the name, then the "default" font will be chosen.
     * The resulting typeface object can be queried (getStyle()) to discover what
     * its "real" style characteristics are.
     *
     * @param familyName May be null. The name of the font family.
     * @param style  The style (normal, bold, italic) of the typeface.
     *               e.g. NORMAL, BOLD, ITALIC, BOLD_ITALIC
     * @return The best matching typeface.
     */
    public static Typeface create(String familyName, int style) {
        if (sSystemFontMap != null) {
            return create(sSystemFontMap.get(familyName), style);
        }
        return null;
    }
    
	///================founder font add start================
    public static void initTypeface() {
    	boolean changed = hasFontChanged();
    	if(changed) {
    		sTypefaceCache.clear();
    		init();
    		initDef();
    	}
    }
	///================founder font add start================

    /**
     * Create a typeface object that best matches the specified existing
     * typeface and the specified Style. Use this call if you want to pick a new
     * style from the same family of an existing typeface object. If family is
     * null, this selects from the default font's family.
     *
     * @param family May be null. The name of the existing type face.
     * @param style  The style (normal, bold, italic) of the typeface.
     *               e.g. NORMAL, BOLD, ITALIC, BOLD_ITALIC
     * @return The best matching typeface.
     */
    public static Typeface create(Typeface family, int style) {
        if (style < 0 || style > 3) {
            style = 0;
        }
        long ni = 0;
        if (family != null) {
            // Return early if we're asked for the same face/style
            if (family.mStyle == style) {
                return family;
            }

            ni = family.native_instance;
        }

        Typeface typeface;
        SparseArray<Typeface> styles = sTypefaceCache.get(ni);

        if (styles != null) {
            typeface = styles.get(style);
            if (typeface != null) {
                return typeface;
            }
        }

        typeface = new Typeface(nativeCreateFromTypeface(ni, style));
        if (styles == null) {
            styles = new SparseArray<Typeface>(4);
            sTypefaceCache.put(ni, styles);
        }
        styles.put(style, typeface);

        return typeface;
    }

    /**
     * Returns one of the default typeface objects, based on the specified style
     *
     * @return the default typeface that corresponds to the style
     */
    public static Typeface defaultFromStyle(int style) {
        return sDefaults[style];
    }

    /**
     * Create a new typeface from the specified font data.
     *
     * @param mgr  The application's asset manager
     * @param path The file name of the font data in the assets directory
     * @return The new typeface.
     */
    public static Typeface createFromAsset(AssetManager mgr, String path) {
        if (sFallbackFonts != null) {
            synchronized (sDynamicTypefaceCache) {
                ///================founder font add start================
//                String fp = SystemProperties.get("persist.sys.founder.fontpath", "system_fonts");
 //               if(!"system_fonts".equals(fp) && (new File(fp).canRead())) {
                    //path = fp;
 //                   return DEFAULT;
  //              }
                ///================founder font end start================
                final String key = createAssetUid(mgr, path);
                Typeface typeface = sDynamicTypefaceCache.get(key);
                if (typeface != null) return typeface;

                FontFamily fontFamily = new FontFamily();
                if (fontFamily.addFontFromAsset(mgr, path)) {
                    FontFamily[] families = { fontFamily };

                    typeface = createFromFamiliesWithDefault(families);
                    //========================lovelyfonts start========================
                    if ("1".equals(SystemProperties.get("ro.lovelyfonts_support", "0"))) {
                        fontFamily.setCanBeReplaced(false);
                        nativeSetCanReplace(typeface.native_instance, false);
                    }
                    //========================lovelyfonts end==========================
                    sDynamicTypefaceCache.put(key, typeface);
                    return typeface;
                }
            }
        }
        throw new RuntimeException("Font asset not found " + path);
    }

    /**
     * Creates a unique id for a given AssetManager and asset path.
     *
     * @param mgr  AssetManager instance
     * @param path The path for the asset.
     * @return Unique id for a given AssetManager and asset path.
     */
    private static String createAssetUid(final AssetManager mgr, String path) {
        final SparseArray<String> pkgs = mgr.getAssignedPackageIdentifiers();
        final StringBuilder builder = new StringBuilder();
        final int size = pkgs.size();
        for (int i = 0; i < size; i++) {
            builder.append(pkgs.valueAt(i));
            builder.append("-");
        }
        builder.append(path);
        return builder.toString();
    }

    /**
     * Create a new typeface from the specified font file.
     *
     * @param path The path to the font data.
     * @return The new typeface.
     */
    public static Typeface createFromFile(File path) {
        return createFromFile(path.getAbsolutePath());
    }

    /**
     * Create a new typeface from the specified font file.
     *
     * @param path The full path to the font data.
     * @return The new typeface.
     */
    public static Typeface createFromFile(String path) {
        if (sFallbackFonts != null) {
            FontFamily fontFamily = new FontFamily();
            ///================founder font add start================
 //           String fp = SystemProperties.get("persist.sys.founder.fontpath", "system_fonts");
 //         if(!"system_fonts".equals(fp) && (new File(fp).canRead())) {
 //               //path = fp;
 //              return DEFAULT;
 //           }
            ///================founder font end start================
            if (fontFamily.addFont(path, 0 /* ttcIndex */)) {
                FontFamily[] families = { fontFamily };
                //========================lovelyfonts start========================
                if ("1".equals(SystemProperties.get("ro.lovelyfonts_support", "0"))) {
                    fontFamily.setCanBeReplaced(false);
                    Typeface typeface = createFromFamiliesWithDefault(families);
                    nativeSetCanReplace(typeface.native_instance, false);
                    return typeface;
                } else {
                return createFromFamiliesWithDefault(families);
            }
                //========================lovelyfonts end==========================
            }
        }
        throw new RuntimeException("Font not found " + path);
    }

    /**
     * Create a new typeface from an array of font families.
     *
     * @param families array of font families
     * @hide
     */
    public static Typeface createFromFamilies(FontFamily[] families) {
        long[] ptrArray = new long[families.length];
        for (int i = 0; i < families.length; i++) {
            ptrArray[i] = families[i].mNativePtr;
        }
        return new Typeface(nativeCreateFromArray(ptrArray));
    }

    /**
     * Create a new typeface from an array of font families, including
     * also the font families in the fallback list.
     *
     * @param families array of font families
     * @hide
     */
    public static Typeface createFromFamiliesWithDefault(FontFamily[] families) {
        long[] ptrArray = new long[families.length + sFallbackFonts.length];
        for (int i = 0; i < families.length; i++) {
            ptrArray[i] = families[i].mNativePtr;
        }
        for (int i = 0; i < sFallbackFonts.length; i++) {
            ptrArray[i + families.length] = sFallbackFonts[i].mNativePtr;
        }
        return new Typeface(nativeCreateFromArray(ptrArray));
    }

    // don't allow clients to call this directly
    private Typeface(long ni) {
        if (ni == 0) {
            throw new RuntimeException("native typeface cannot be made");
        }

        native_instance = ni;
        mStyle = nativeGetStyle(ni);
    }

    private static FontFamily makeFamilyFromParsed(FontListParser.Family family,
            Map<String, ByteBuffer> bufferForPath) {
        FontFamily fontFamily = new FontFamily(family.lang, family.variant);
        for (FontListParser.Font font : family.fonts) {
            ByteBuffer fontBuffer = bufferForPath.get(font.fontName);
            if (fontBuffer == null) {
                try (FileInputStream file = new FileInputStream(font.fontName)) {
                    FileChannel fileChannel = file.getChannel();
                    long fontSize = fileChannel.size();
                    fontBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fontSize);
                    bufferForPath.put(font.fontName, fontBuffer);
                } catch (IOException e) {
                    Log.e(TAG, "Error mapping font file " + font.fontName);
                    continue;
                }
            }
            if (!fontFamily.addFontWeightStyle(fontBuffer, font.ttcIndex, font.axes,
                    font.weight, font.isItalic)) {
                Log.e(TAG, "Error creating font " + font.fontName + "#" + font.ttcIndex);
            }
        }
        return fontFamily;
    }

    /*
     * (non-Javadoc)
     *
     * This should only be called once, from the static class initializer block.
     */
    private static void init() {
        // Load font config and initialize Minikin state
        File systemFontConfigLocation = getSystemFontConfigLocation();
        File configFilename = new File(systemFontConfigLocation, FONTS_CONFIG);
        try {
            FileInputStream fontsIn = new FileInputStream(configFilename);
            FontListParser.Config fontConfig = FontListParser.parse(fontsIn);

            Map<String, ByteBuffer> bufferForPath = new HashMap<String, ByteBuffer>();

            List<FontFamily> familyList = new ArrayList<FontFamily>();
            // Note that the default typeface is always present in the fallback list;
            // this is an enhancement from pre-Minikin behavior.
            for (int i = 0; i < fontConfig.families.size(); i++) {
                FontListParser.Family f = fontConfig.families.get(i);
                if (i == 0 || f.name == null) {
                    familyList.add(makeFamilyFromParsed(f, bufferForPath));
                }
            }
            sFallbackFonts = familyList.toArray(new FontFamily[familyList.size()]);
            setDefault(Typeface.createFromFamilies(sFallbackFonts));

            Map<String, Typeface> systemFonts = new HashMap<String, Typeface>();
            for (int i = 0; i < fontConfig.families.size(); i++) {
                Typeface typeface;
                FontListParser.Family f = fontConfig.families.get(i);
                if (f.name != null) {
                    if (i == 0) {
                        // The first entry is the default typeface; no sense in
                        // duplicating the corresponding FontFamily.
                        typeface = sDefaultTypeface;
                    } else {
                        FontFamily fontFamily = makeFamilyFromParsed(f, bufferForPath);
                        FontFamily[] families = { fontFamily };
                        typeface = Typeface.createFromFamiliesWithDefault(families);
                    }
                    systemFonts.put(f.name, typeface);
                }
            }
            for (FontListParser.Alias alias : fontConfig.aliases) {
                Typeface base = systemFonts.get(alias.toName);
                Typeface newFace = base;
                int weight = alias.weight;
                if (weight != 400) {
                    newFace = new Typeface(nativeCreateWeightAlias(base.native_instance, weight));
                }
                systemFonts.put(alias.name, newFace);
            }
            sSystemFontMap = systemFonts;

        } catch (RuntimeException e) {
            Log.w(TAG, "Didn't create default family (most likely, non-Minikin build)", e);
            // TODO: normal in non-Minikin case, remove or make error when Minikin-only
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening " + configFilename, e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading " + configFilename, e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "XML parse exception for " + configFilename, e);
        }
    }

    static {
		///================founder font add start================
    	hasFontChanged();
		///================founder font add end================
        init();
        // Set up defaults and typefaces exposed in public API
        DEFAULT         = create((String) null, 0);
        DEFAULT_BOLD    = create((String) null, Typeface.BOLD);
        SANS_SERIF      = create("sans-serif", 0);
        SERIF           = create("serif", 0);
        MONOSPACE       = create("monospace", 0);

        sDefaults = new Typeface[] {
            DEFAULT,
            DEFAULT_BOLD,
            create((String) null, Typeface.ITALIC),
            create((String) null, Typeface.BOLD_ITALIC),
        };

    }
    ///================founder font add start================
    private static void initDef() {
    	DEFAULT         = create((String) null, 0);
        DEFAULT_BOLD    = create((String) null, Typeface.BOLD);
        SANS_SERIF      = create("sans-serif", 0);
        SERIF           = create("serif", 0);
        MONOSPACE       = create("monospace", 0);

        sDefaults = new Typeface[] {
            DEFAULT,
            DEFAULT_BOLD,
            create((String) null, Typeface.ITALIC),
            create((String) null, Typeface.BOLD_ITALIC),
        };
    }
	///================founder font add end================
    private static File getSystemFontConfigLocation() {
        return new File("/system/etc/");
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            nativeUnref(native_instance);
            native_instance = 0;  // Other finalizers can still call us.
        } finally {
            super.finalize();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Typeface typeface = (Typeface) o;

        return mStyle == typeface.mStyle && native_instance == typeface.native_instance;
    }

    @Override
    public int hashCode() {
        /*
         * Modified method for hashCode with long native_instance derived from
         * http://developer.android.com/reference/java/lang/Object.html
         */
        int result = 17;
        result = 31 * result + (int) (native_instance ^ (native_instance >>> 32));
        result = 31 * result + mStyle;
        return result;
    }

    private static native long nativeCreateFromTypeface(long native_instance, int style);
    private static native long nativeCreateWeightAlias(long native_instance, int weight);
    private static native void nativeUnref(long native_instance);
    private static native int  nativeGetStyle(long native_instance);
    private static native long nativeCreateFromArray(long[] familyArray);
    private static native void nativeSetDefault(long native_instance);
//========================lovelyfonts start========================
     /**
     * @hide
     */
    public static void updateLovelyFonts() {
        nativeUpdateLovelyfonts();
        // Ask graphics to free up as much as possible (font/image caches)
        Canvas.freeCaches();
        // Ask text layout engine to free also as much as possible
        Canvas.freeTextLayoutCaches();
    }
    /**
     * @hide
     */
    public static void updateLovelyEmojis() {
        nativeClearEmojiFonts();
        nativeUpdateEmojiFonts(null);
        // Ask graphics to free up as much as possible (font/image caches)
        Canvas.freeCaches();
        // Ask text layout engine to free also as much as possible
        Canvas.freeTextLayoutCaches();
    }
    /**
     * @hide
     */
    private  static native void nativeUpdateLovelyfonts();
    /**
     * @hide
     */
    private static native long nativeUpdateEmojiFonts(String[] familyArray);
    /**
     * @hide
     */
    private static native void nativeClearEmojiFonts();
     /**
     * @hide
     */
     /**
     * @hide
     */
    private static native void nativeSetCanReplace(long nativeInstance, boolean can);
      /**
     * @hide
     */
    public void setCanBeReplaced(boolean can) {
        nativeSetCanReplace(native_instance, can);
    }
    /**
     * @hide
     */
    private static native boolean nativeGetCanReplace(long nativeIinstance);
//========================lovelyfonts end  ========================
    ///================founder font add start================

	private static String mFontMD5;
	private static boolean hasFontChanged() {
		String path = SystemProperties.get("persist.sys.founder.fontpath", "system_fonts");
		String md5 = getMd5ByFile(path);
		if(mFontMD5 == null && md5 ==null) {
			return false;
		} else if(mFontMD5 == null || md5 ==null) {
			mFontMD5 = md5;
			return true;
		} else {
			if(mFontMD5.equals(md5)) {
				return false;
			}
			mFontMD5 = md5;
			return true;
		}
	}
	
	private static String getMd5ByFile(String file) {
		String value = null;
		FileInputStream in = null;
		try {
			in = new FileInputStream(new File(file));
			MappedByteBuffer byteBuffer = in.getChannel().map(
					FileChannel.MapMode.READ_ONLY, 0, file.length());
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(byteBuffer);
			BigInteger bi = new BigInteger(1, md5.digest());
			value = bi.toString(16);
		} catch (Exception e) {
		} finally {
			if (null != in) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
		return value;
	}
	///================founder font add end================

}
