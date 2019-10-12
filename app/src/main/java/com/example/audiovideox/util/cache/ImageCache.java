package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;

public interface ImageCache {
    void put(String name, Bitmap bitmap);
    Bitmap get(String name);
}
