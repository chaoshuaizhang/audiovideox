package com.example.audiovideox;

import android.os.Bundle;
import android.view.View;

import com.example.audiovideox.primary.AudioRecordActivity;
import com.example.audiovideox.primary.CameraRecordAudioActivity;
import com.example.audiovideox.primary.MediaRecorderActivity;
import com.example.audiovideox.primary.OpenCameraResActivity;
import com.example.audiovideox.primary.ThreeWayDrawImgActivity;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void tvClick(View view) {
        switch (view.getId()) {
            case R.id.tv_3_ways_img:
                ThreeWayDrawImgActivity.start(this);
                break;
            case R.id.tv_media:
                MediaRecorderActivity.start(this);
                break;
            case R.id.tv_audio:
                AudioRecordActivity.start(this);
                break;
            case R.id.tv_camera_record_video:
                CameraRecordAudioActivity.start(this);
                break;
            case R.id.tv_open_camera_resources:
                OpenCameraResActivity.start(this);
                break;
            case R.id.tv_camera_take_video:
                CameraRecordVideoActivity.start(this);
                break;
        }
    }
}
