package com.mediatek.gallery3d.ext;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.PhotoView.Picture;
import com.android.gallery3d.ui.PositionController;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.mediatek.galleryframework.base.MediaData;

/**
 * DefaultImageOptionsExt, OP01 plugin will extend it.
 */
public class DefaultImageOptionsExt implements IImageOptionsExt {
    private static final String TAG = "Gallery2/DefaultImageOptionsExt";
    private static final float MAX_SCALE = 4.0f;

    @Override
    public void setMediaItem(MediaItem mediaItem) {
    }

    @Override
    public float getImageDisplayScale(float initScale, MediaItem mediaItem) {//add prize liup 20180322 cmcc , MediaItem mediaItem
        return Math.min(MAX_SCALE, initScale);
    }

    @Override
    public float getMinScaleLimit(MediaData mediaData, float scale) {
        return scale;
    }

    @Override
    public void updateTileProviderWithScreenNail(TileImageViewAdapter adapter,
            ScreenNail screenNail) {
    }

    @Override
    public void updateMediaData(Picture picture, ScreenNail screenNail) {
    }

    @Override
    public void updateBoxMediaData(PositionController controller, int index,
            MediaData mediaData) {
    }
}