package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import com.example.audiovideox.primary.view.MyPreviewVideoImage;

public class ImageLoaderUtil {

    private static final String TAG = "ImageLoaderUtil";

    private static Handler handler = new Handler(Looper.getMainLooper());

    private ImageCache<MyPreviewVideoImage> imageCache;

    public ImageLoaderUtil(ImageCache<MyPreviewVideoImage> imageCache) {
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
    public MyPreviewVideoImage displayVideoFrameByPath(ImageView view, String path, long time) {
        MyPreviewVideoImage videoImage = imageCache.get(path);
        if (videoImage == null) {
            videoImage = VideoUtil.getFrameAtTime(path, time);
            if (videoImage == null || videoImage.getBitmap() == null) {
                return null;
            }
            Log.d(TAG, "displayVideoFrameByPath: " + (videoImage.getBitmap().getWidth() *
                    videoImage.getBitmap().getHeight() / 1024));
            imageCache.put(path, videoImage);
        }
        displayImage(view, videoImage.getBitmap());
        return videoImage;
    }

    /**
     * 加载本地图片
     */
    public void displayImageByFilePath(ImageView view, String path) {
        Bitmap bitmap = imageCache.get(path).getBitmap();
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeFile(path);
            imageCache.put(path, new MyPreviewVideoImage(bitmap));
        }
        displayImage(view, bitmap);
    }

    /**
     * 加载网络图片
     */
    public void displayImageByNetUrl(ImageView view, String url) {

    }

}
