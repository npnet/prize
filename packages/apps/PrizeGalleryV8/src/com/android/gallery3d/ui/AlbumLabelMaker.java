/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataSourceType;
import com.android.photos.data.GalleryBitmapPool;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;

import com.mediatek.gallery3d.layout.FancyHelper;
import com.mediatek.gallery3d.util.TraceHelper;

public class AlbumLabelMaker {
    private static final int BORDER_SIZE = 0;

    private final AlbumSetSlotRenderer.LabelSpec mSpec;
    private final TextPaint mTitlePaint;
    private final TextPaint mCountPaint;
    private final Context mContext;

    private int mLabelWidth;
    private int mBitmapWidth;
    private int mBitmapHeight;

    private final LazyLoadedBitmap mLocalSetIcon;
    private final LazyLoadedBitmap mPicasaIcon;
    private final LazyLoadedBitmap mCameraIcon;
    private int mPadding;

    public AlbumLabelMaker(Context context, AlbumSetSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor, false);
        mCountPaint = getTextPaint(spec.countFontSize, spec.countColor, false);
		/* PRIZE-bug 17626 AlbumSetPage: Gallery folder in the picture more than 999, the number of the thumbnail does not show -wanzhijuan-2016-6-22-start*/
//        mCountPaint.setTextAlign(Align.RIGHT);
		mPadding = context.getResources().getDimensionPixelSize(
                R.dimen.albumset_count_padding);
		/* PRIZE-bug 17626 AlbumSetPage: Gallery folder in the picture more than 999, the number of the thumbnail does not show -wanzhijuan-2016-6-22-end*/

        mLocalSetIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_folder);
        mPicasaIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_picasa);
        mCameraIcon = new LazyLoadedBitmap(R.drawable.frame_overlay_gallery_camera);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    private Bitmap getOverlayAlbumIcon(int sourceType) {
//        switch (sourceType) {
//            case DataSourceType.TYPE_CAMERA:
//                return mCameraIcon.get();
//            case DataSourceType.TYPE_LOCAL:
//                return mLocalSetIcon.get();
//            case DataSourceType.TYPE_PICASA:
//                return mPicasaIcon.get();
//        }
        return null;
    }

    private static TextPaint getTextPaint(int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        //paint.setShadowLayer(2f, 0f, 0f, Color.LTGRAY);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    private class LazyLoadedBitmap {
        private Bitmap mBitmap;
        private int mResId;

        public LazyLoadedBitmap(int resId) {
            mResId = resId;
        }

        public synchronized Bitmap get() {
            if (mBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                mBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(), mResId, options);
            }
            return mBitmap;
        }
    }

    public synchronized void setLabelWidth(int width) {
        if (mLabelWidth == width) return;
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapWidth = width + borders;
        mBitmapHeight = mSpec.labelBackgroundHeight + borders;
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType) {
        return new AlbumLabelJob(title, count, sourceType);
    }

    static void drawText(Canvas canvas,
            int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(
                    text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
        private final String mTitle;
        private final String mCount;
        private final int mSourceType;
        /// M: [FEATURE.ADD] fancy layout @{
        private final boolean mIsLandCamera;
        private final boolean mIsFancyLayout;
        public AlbumLabelJob(String title, String count, int sourceType, boolean isLandCamera,
                boolean isFancy) {
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
            mIsLandCamera = isLandCamera;
            mIsFancyLayout = isFancy;
        }
        /// @}
        public AlbumLabelJob(String title, String count, int sourceType) {
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
            /// M: [FEATURE.ADD] fancy layout @{
            mIsLandCamera = false;
            mIsFancyLayout = false;
            /// @}
        }

        @Override
        public Bitmap run(JobContext jc) {
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceBegin(">>>>AlbumLabelMaker-AlbumLabelJob.run");
            /// @}
            AlbumSetSlotRenderer.LabelSpec s = mSpec;

            String title = mTitle;
            String count = mCount;
            Bitmap icon = getOverlayAlbumIcon(mSourceType);

            Bitmap bitmap = null;
            int labelWidth;

            synchronized (this) {
                /// M: [FEATURE.MODIFY] fancy layout @{
                /*
                labelWidth = mLabelWidth;
                bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
                */
                if (FancyHelper.isFancyLayoutSupported() && mIsFancyLayout && mIsLandCamera) {
                    labelWidth = FancyHelper.getScreenWidthAtFancyMode();
                } else {
                    labelWidth = mLabelWidth;
                    bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
                }
                /// @}
            }

            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(labelWidth + borders,
                        s.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);

            int translateY = (s.labelBackgroundHeight - s.labelCountTop - s.countFontSize - s.titleFontSize) / 2;
            canvas.translate(BORDER_SIZE, BORDER_SIZE + translateY);

            // draw title
            /// M: [DEBUG.MODIFY] @{
            /*if (jc.isCancelled()) return null;*/
            if (jc.isCancelled()) {
                TraceHelper.traceEnd();
                return null;
            }
            /// @}
            int x = s.leftMargin;
            // TODO: is the offset relevant in new reskin?
            // int y = s.titleOffset;
            int y = 0;//(s.labelBackgroundHeight - s.titleFontSize) / 2;
            drawText(canvas, x + mPadding, y, title, labelWidth - s.leftMargin - x - 
                    s.titleRightMargin, mTitlePaint);


            // draw count
            /// M: [DEBUG.MODIFY] @{
            /*if (jc.isCancelled()) return null;*/
            if (jc.isCancelled()) {
                TraceHelper.traceEnd();
                return null;
            }
            /// @}
//            x = labelWidth - s.titleRightMargin;
            x = s.leftMargin;
            y = s.countFontSize + s.labelCountTop;
            /// M: [FEATURE.MODIFY] fancy layout @{
            /*
            drawText(canvas, x, y, count,
                    labelWidth - x , mCountPaint);
            */
            if (FancyHelper.isFancyLayoutSupported()) {
                // use the same font for title and count
                drawText(canvas, x, y, count,
                        labelWidth - x , mTitlePaint);
            } else {
            	/* PRIZE-bug 17626 AlbumSetPage: Gallery folder in the picture more than 999, the number of the thumbnail does not show -wanzhijuan-2016-6-22-start*/
                drawText(canvas, x + mPadding, y, count,
                        labelWidth, mCountPaint);
				/* PRIZE-bug 17626 AlbumSetPage: Gallery folder in the picture more than 999, the number of the thumbnail does not show -wanzhijuan-2016-6-22-end*/
            }
            /// @}

            // draw the icon
            if (icon != null) {
                /// M: [DEBUG.MODIFY] @{
                /*if (jc.isCancelled()) return null;*/
                if (jc.isCancelled()) {
                    TraceHelper.traceEnd();
                    return null;
                }
                /// @}
                float scale = (float) s.iconSize / icon.getWidth();
                canvas.translate(s.leftMargin, (s.labelBackgroundHeight -
                        Math.round(scale * icon.getHeight()))/2f);
                canvas.scale(scale, scale);
                canvas.drawBitmap(icon, 0, 0, null);
            }
            /// M: [DEBUG.ADD] @{
            TraceHelper.traceEnd();
            /// @}
            return bitmap;
        }
    }

    public void recycleLabel(Bitmap label) {
        GalleryBitmapPool.getInstance().put(label);
    }

//********************************************************************
//*                              MTK                                 *
//********************************************************************

    /// M: [FEATURE.ADD] fancy layout @{
    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType, boolean isLandCamera, boolean isFancy) {
        return new AlbumLabelJob(title, count, sourceType, isLandCamera, isFancy);
    }
    /// @}
}
