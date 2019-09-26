package com.example.audiovideox.primary;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.AnimatorRes;
import androidx.annotation.Nullable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.audiovideox.R;

import java.io.FileNotFoundException;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class OpenCameraResActivity extends AppCompatActivity {

    private static final String TAG = OpenCameraResActivity.class.getName();
    private ConstraintLayout constraintLayout;
    private AlbumFrag albumFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_camera_res);
        constraintLayout = findViewById(R.id.constraint_bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);
        }
    }

    public void btnClick(View view) {
        if (view.getId() == R.id.cameras_native) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 1001);
        } else if (view.getId() == R.id.cameras_custom) {
            //打开自定义的相册
            if(albumFrag == null){
                albumFrag = AlbumFrag.getInstance();
            }
            if(albumFrag.isAdded()){
                if(!albumFrag.isHidden()){
                    Toast.makeText(this, "已经显示了", Toast.LENGTH_SHORT).show();
                }else{
                    getSupportFragmentManager().beginTransaction().show(albumFrag).commit();
                }
            }else {
                getSupportFragmentManager().beginTransaction().add(R.id.frame_container,albumFrag).commit();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 1001) {
            //调用相册后
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    //此处获取刚刚拍摄的图片，也可以用getContentResolver().query方法，不过会复杂
                    Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()));
                    Log.d(TAG, "onActivityResult: " + bitmap.getWidth() + " --- " + bitmap.getHeight());
                    float width = constraintLayout.getWidth();
                    int bW = bitmap.getWidth();
                    int bH = bitmap.getHeight();
                    float height = bH / (bW / width);
                    ViewGroup.LayoutParams layoutParams = constraintLayout.getLayoutParams();
                    layoutParams.width = (int) width;
                    layoutParams.height = (int) height;
                    constraintLayout.setLayoutParams(layoutParams);
                    constraintLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if(albumFrag != null){
            if(!albumFrag.isHidden()){
                getSupportFragmentManager().beginTransaction().hide(albumFrag).commit();
                return;
            }
        }
        super.onBackPressed();
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, OpenCameraResActivity.class);
        context.startActivity(starter);
    }

}
