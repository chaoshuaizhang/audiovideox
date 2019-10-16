package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;

public interface ImageCache<T> {
    void put(String name, T t);
    T get(String name);
}
