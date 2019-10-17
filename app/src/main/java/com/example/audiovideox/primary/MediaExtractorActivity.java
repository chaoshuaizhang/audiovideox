package com.example.audiovideox.primary;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.example.audiovideox.R;
import com.example.audiovideox.util.cache.VideoUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * MediaExtractor可以读取到媒体文件的信息，比如读取出来视频，音频，然后用对应的播放器播放
 */
public class MediaExtractorActivity extends AppCompatActivity {

    private MediaExtractor mediaExtractor;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private VideosAdapter adapter;
    private List<String[]> list = new ArrayList<>();
    private TextureView textureView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_extractor);
        recyclerView = findViewById(R.id.recycler_view_videos);
        textureView = findViewById(R.id.texture_view);
        layoutManager = new LinearLayoutManager(this);
        getVideos();
        adapter = new VideosAdapter(list, this, R.layout.item_layout_tv);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);
    }

    private void getVideos() {
        File mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "mediavideos");
        if (mountedDir.exists()) {
            File[] files = mountedDir.listFiles();
            for (int i = 0; i < files.length; i++) {
                String[] arr = {files[i].getName(), files[i].getAbsolutePath()};
                this.list.add(arr);
            }
        }
        list.add(new String[]{"test_video.mp4", "https://shopin-images.oss-cn-beijing.aliyuncs.com/2015305/test_video.mp4"});
    }

    private void initMediaExtractor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mediaExtractor = new MediaExtractor();
        }
    }

    public void btnClick(View view) {
        switch (view.getId()) {
            case R.id.playvideo_btn:
                //只播放视频不播放音频
                playVideo(adapter.getSelectedPath());
                break;
            case R.id.play_btn:
                //播放完整视频
                playVideoAudio(adapter.getSelectedPath());
                break;
            case R.id.playaudio_btn:
                //只播放音频
                playAudio(adapter.getSelectedPath());
                break;
            case R.id.compose_video_audio_btn:
                composeVideoAudio(adapter.getSelectedPath(), adapter.getSelectedPath2());
                break;
            default:
                break;
        }
    }

    private void composeVideoAudio(String videoPath, String audioPath) {
        File mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "mediavideos");
        File file = new File(mountedDir, "compose-" + new Random().nextInt(10000) + ".mp4");
        VideoUtil.composeVideoAudio(videoPath, audioPath, file.getAbsolutePath());
    }

    void playVideo(final String path) {
        textureView.setVisibility(View.VISIBLE);
        new Thread() {
            @Override
            public void run() {
                VideoUtil.playVideo(path, textureView, new VideoUtil.IVideoSize() {
                    @Override
                    public void getWidthHeight(final int width, final int height) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                //设置surface大小（这里参考下grafika-master中的设置方式）
                                ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
                                float scale = height / width;
                                layoutParams.height = (int) (scale * layoutParams.width);
                                textureView.setLayoutParams(layoutParams);
                            }
                        });
                    }
                });
            }
        }.start();
    }

    void playAudio(final String path) {
        new Thread() {
            @Override
            public void run() {
                VideoUtil.playAudio(path);
            }
        }.start();
    }

    void playVideoAudio(final String path) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        executor.submit(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("播放音频-----:");
                VideoUtil.playAudio(path);
            }
        });
        executor.submit(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("播放视频+++++:");
                VideoUtil.playVideo(path, textureView, new VideoUtil.IVideoSize() {
                    @Override
                    public void getWidthHeight(final int width, final int height) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                //设置surface大小（这里参考下grafika-master中的设置方式）
                                ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
                                float scale = height / width;
                                layoutParams.height = (int) (scale * layoutParams.width);
                                textureView.setLayoutParams(layoutParams);
                            }
                        });
                    }
                });
            }
        });
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, MediaExtractorActivity.class);
        context.startActivity(starter);

        //构造一个Builder实例
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        //为这个Builder实例设置一系列属性
        builder.setTitle("")
                .setIcon(0)
                .setCancelable(false)
                .setMessage(0);
        //依据builder的一系列属性创建一个dialog（在create方法中完成）
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
