package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import com.example.audiovideox.primary.view.MyPreviewVideoImage;

public class ImageLoaderUtil {

    private static final String TAG = "ImageLoaderUtil";

    private static Handler handler = new Handler(Looper.getMainLooper());

    private static ImageCache<MyPreviewVideoImage> imageCacheInstance;

    public ImageLoaderUtil(ImageCache<MyPreviewVideoImage> imageCache) {
        if(imageCacheInstance == null){
            imageCacheInstance = imageCache;
        }
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
        long l = System.currentTimeMillis();
        MyPreviewVideoImage videoImage = imageCacheInstance.get(path);
        if (videoImage == null) {
            videoImage = VideoUtil.getFrameAtTime(path, time);
            if (videoImage == null || videoImage.getBitmap() == null) {
                return null;
            }
            Log.d(TAG, "displayVideoFrameByPath: " + (videoImage.getBitmap().getWidth() *
                    videoImage.getBitmap().getHeight() / 1024));
            imageCacheInstance.put(path, videoImage);
        }
        displayImage(view, videoImage.getBitmap());
        l = System.currentTimeMillis() - l;
        System.out.println("displayVideoFrameByPath  "+l);
        return videoImage;
    }

    /**
     * 加载本地图片
     */
    public void displayVideoFrameByPathAsync(final ImageView view, final String path, final long time, final Handler handler) {
        new Thread(){
            @Override
            public void run() {
                MyPreviewVideoImage videoImage = imageCacheInstance.get(path);
                if (videoImage == null) {
                    videoImage = VideoUtil.getFrameAtTime(path, time);
                    if (videoImage == null || videoImage.getBitmap() == null) {
                        //异步的时候，为null就先不校验了
                    }
                    Log.d(TAG, "displayVideoFrameByPath: " + (videoImage.getBitmap().getWidth() *
                            videoImage.getBitmap().getHeight() / 1024));
                    imageCacheInstance.put(path, videoImage);
                }
                Message message = handler.obtainMessage();
                message.obj = videoImage;
                handler.sendMessage(message);
                displayImage(view, videoImage.getBitmap());
            }
        }.start();
    }


    /**
     * 加载本地图片
     */
    public void displayImageByFilePath(ImageView view, String path) {
        Bitmap bitmap = imageCacheInstance.get(path).getBitmap();
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeFile(path);
            imageCacheInstance.put(path, new MyPreviewVideoImage(bitmap));
        }
        displayImage(view, bitmap);
    }

    /**
     * 加载网络图片
     */
    public void displayImageByNetUrl(ImageView view, String url) {

    }

}
