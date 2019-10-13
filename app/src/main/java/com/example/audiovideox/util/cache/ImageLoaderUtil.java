package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

public class ImageLoaderUtil {

    private static final String TAG = "ImageLoaderUtil";

    private static Handler handler = new Handler(Looper.getMainLooper());

    private ImageCache imageCache;

    public ImageLoaderUtil(ImageCache imageCache) {
        this.imageCache = imageCache;
    }

    /**
     * view显示
     *
     * @param view
     * @param bitmap
     */
    public void displayImage(final ImageView view, final Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.setImageBitmap(bitmap);
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    view.setImageBitmap(bitmap);
                }
            });
        }
    }

    /**
     * 加载本地图片
     */
    public void displayVideoFrameByPath(ImageView view, String path, long time) {
        Bitmap bitmap = imageCache.get(path);
        if (bitmap == null) {
            bitmap = VideoUtil.getFrameAtTime(path, time);
            Log.d(TAG, "displayVideoFrameByPath: " + (bitmap.getWidth() * bitmap.getHeight() / 1024));
            imageCache.put(path, bitmap);
        }
        displayImage(view, bitmap);
    }

    /**
     * 加载本地图片
     */
    public void displayImageByFilePath(ImageView view, String path) {
        Bitmap bitmap = imageCache.get(path);
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeFile(path);
            imageCache.put(path, bitmap);
        }
        displayImage(view, bitmap);
    }

    /**
     * 加载网络图片
     */
    public void displayImageByNetUrl(ImageView view, String url) {
    }
}
