package com.example.audiovideox.util.cache;

import com.example.audiovideox.primary.view.MyPreviewVideoImage;

public class MyBitmapCache extends MemoryCache<MyPreviewVideoImage> {
    @Override
    protected int sizeOf(String key, MyPreviewVideoImage value) {
        return value.getBitmap().getWidth() * value.getBitmap().getHeight() / 1024;

    }

    @Override
    public void put(String name, MyPreviewVideoImage image) {
        lruCache.put(name, image);
    }

    @Override
    public MyPreviewVideoImage get(String name) {
        return lruCache.get(name);
    }
}
