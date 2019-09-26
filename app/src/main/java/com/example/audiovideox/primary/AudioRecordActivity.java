package com.example.audiovideox.primary;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import com.example.audiovideox.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AppCompatActivity;

public class AudioRecordActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    private SeekBar seekBar;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private File mountedDir = null;
    private String TAG = "AudioRecordActivity";
    private ExecutorService executorService = Executors.newFixedThreadPool(1);
    private volatile boolean isRecording = false;
    private volatile File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record);
        seekBar = findViewById(R.id.seekbar);
        seekBar.setOnSeekBarChangeListener(this);
        mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "audiorecord");
        if (!mountedDir.exists()) {
            mountedDir.mkdir();
        }
    }

    public void btnClick(View view) {
        switch (view.getId()) {
            case R.id.btn_start:
                isRecording = true;
                executorService.execute(new RecordRunnable());
                break;
            case R.id.btn_stop:
                isRecording = false;
                audioRecord.stop();
                break;
            case R.id.btn_play:
                executorService.execute(new TrackRunnable());
                break;
        }
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, AudioRecordActivity.class);
        context.startActivity(starter);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    class RecordRunnable implements Runnable {
        @Override
        public void run() {
            try {
                //minBufferSize个字节
                int minBufferSize = AudioRecord.getMinBufferSize(44100,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT);
                //声源、采样率、音频通道、音频格式、缓冲区大小（一句前边几个参数来设置）
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        44100,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize);
                //定义一个存放流的文件
                file = File.createTempFile(SystemClock.elapsedRealtime() + "", ".pcm", mountedDir);
                byte[] byteBuffer = new byte[minBufferSize];
                audioRecord.startRecording();
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                while (isRecording) {
                    //读进去多少，写出来多少
                    int i = audioRecord.read(byteBuffer, 0, byteBuffer.length);
                    Log.d(TAG, "run: " + i);
                    dos.write(byteBuffer, 0, i);
                }
                Log.d(TAG, "录制完毕: " + file.length());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class TrackRunnable implements Runnable {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void run() {
            AudioFormat audioFormat = new AudioFormat.Builder().build();
            AudioAttributes audioAttributes = new AudioAttributes.Builder().build();
            int minBufferSize = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack(audioAttributes, audioFormat, minBufferSize,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            byte[] byteBuffer = new byte[minBufferSize];
            try {
                DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
                audioTrack.play();
                int i;
                do {
                    i = dis.read(byteBuffer);
                    audioTrack.write(byteBuffer, 0, i);
                }while (i != -1);
                Log.d(TAG, "run: 播放完成");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                audioTrack.stop();
                audioTrack.release();
                Log.d(TAG, "run: 释放完成");
            }
        }
    }


}
