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

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder mHolder;
    private Canvas mCanvas;
    private boolean mIsRunning;

    public MySurfaceView(Context context) {
        this(context, null);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mIsRunning = true;
        draw();
        //并不是非得要线程，绘制量大时可以使用线程
        //new Thread(this).start();
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsRunning = false;
    }

    @Override
    public void run() {
        while (mIsRunning) {
            draw();
        }
    }

    private void draw() {
        mCanvas = mHolder.lockCanvas();
        if (mCanvas != null) {
            try {
                Paint paint = new Paint();
                paint.setColor(Color.GREEN);
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                //使用获得的Canvas做具体的绘制
                mCanvas.drawBitmap(bitmap, 0, 0, paint);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mHolder.unlockCanvasAndPost(mCanvas);
            }
        }
    }

}
