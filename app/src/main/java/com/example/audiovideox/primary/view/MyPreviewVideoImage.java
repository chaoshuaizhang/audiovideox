package com.example.audiovideox.primary.view;

import android.graphics.Bitmap;

public class MyPreviewVideoImage {
    private Bitmap bitmap;
    private long time;

    public MyPreviewVideoImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public MyPreviewVideoImage(Bitmap bitmap, long time) {
        this.bitmap = bitmap;
        this.time = time;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
