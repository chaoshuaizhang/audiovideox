package com.example.audiovideox.primary;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.audiovideox.R;
import com.example.audiovideox.primary.view.MySurfaceView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 通过三种方式显示一张图片
 */
public class ThreeWayDrawImgActivity extends AppCompatActivity {

    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_three_way_draw_img);
        imageView = findViewById(R.id.img);
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, ThreeWayDrawImgActivity.class);
        context.startActivity(starter);
    }

    public void btnClick(View view) {
        switch (view.getId()) {
            case R.id.btn_withimg:
                //直接使用ImageView
                drawWithImg();
                break;
            case R.id.btn_withsurfaceview:
                //使用SurfaceView
                drawWithSurfaceView();
            case R.id.btn_withcustomview:
                drawWithCustomView();
                break;
        }
    }

    void drawWithImg() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round);
        imageView.setImageBitmap(bitmap);
    }

    void drawWithSurfaceView() {
        FrameLayout frameLayout = findViewById(R.id.framelayout);
        frameLayout.addView(new MySurfaceView(this));
    }

    void drawWithCustomView() {
    }

}
