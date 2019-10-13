package com.example.audiovideox.primary.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.audiovideox.R;

public class MyCaptureSurfaceView extends SurfaceView implements SurfaceHolder.Callback {


    public MyCaptureSurfaceView(Context context) {
        this(context, null);
    }

    public MyCaptureSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyCaptureSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }


}
