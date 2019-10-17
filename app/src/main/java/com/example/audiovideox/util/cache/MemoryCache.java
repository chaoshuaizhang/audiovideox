package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

public abstract class MemoryCache<T> implements ImageCache<T> {
    protected LruCache<String, T> lruCache;

    public MemoryCache() {
        lruCache = new LruCache<String, T>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
            @Override
            protected int sizeOf(String key, T value) {
                return MemoryCache.this.sizeOf(key, value);
            }
        };
    }

    protected abstract int sizeOf(String key, T t);
}
