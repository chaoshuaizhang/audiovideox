package com.example.audiovideox.primary;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.example.audiovideox.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class MediaRecorderActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    //当前操作的这一条音频文件
    private File mediaFile = null;
    //存放音频文件的目录
    private File mountedDir = null;
    private MediaRecorder mediaRecorder;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> list = new ArrayList<>();
    private int selectedPoi = -1;
    private String TAG = "MediaRecorderActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_recorder);
        mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "mediarecords");
        if (!mountedDir.exists()) {
            mountedDir.mkdir();
        }
        listView = findViewById(R.id.listview);
        getMediaRecordsList();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 1001);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void mediaClick(View view) {
        switch (view.getId()) {
            case R.id.btn_start:
                //创建空白录音文件
                try {
                    mediaFile = File.createTempFile(System.currentTimeMillis() + "", ".amr", mountedDir);
                    mediaRecorder = new MediaRecorder();
                    // TODO: 2019/9/14/014 注意下边的几行代码的顺序一定要控制好（可以看源码注释，里边写了哪行必须先执行，后执行）
                    //设置录音来源为麦克风
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                    //设置MediaRecorder输出格式+
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                    //设置使用的编码
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                    mediaRecorder.setOutputFile(mediaFile.getAbsolutePath());
                    //
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_stop:
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    list.add(mediaFile.getName());
                    adapter.notifyDataSetChanged();
                }
                break;
            case R.id.btn_play:
                if (selectedPoi == -1) {
                    Toast.makeText(this, "请选中录音文件", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        // TODO: 2019/9/14/014 这块儿播放时因为涉及到跨进程，所以需要FileProvider，注意其写法
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setAction(Intent.ACTION_VIEW);
                        String type = getMIMEType(null);
                        File file = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED) + "/mediarecords", mediaFile.getName());
                        // /storage/emulated/0/Android/data/com.example.audiovideox/files/mounted/mediarecords/15684421584001911819872.mp3
                        Log.d(TAG, "mediaClick: " + file.getAbsolutePath());
                        Uri contentUri = FileProvider.getUriForFile(this, "com.example.audiovideox.fileprovider", file);
                        // /medias/mounted/mediarecords/15684421584001911819872.mp3
                        Log.d(TAG, "mediaClick: " + contentUri.getPath());
                        intent.setDataAndType(contentUri, type);
                        // FIXME: 2019/9/14/014 不加这一块的赋值权限，会提示无法播放该文件
                        List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        for (ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn_play_mediaplayer:
                MediaPlayer mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(mountedDir.getAbsolutePath() + File.separator + list.get(selectedPoi));
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.stop();
                            mp.release();
                        }
                    });
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_delete:
                if (selectedPoi == -1) {
                    break;
                }
                File file = new File(mountedDir.getAbsolutePath(), list.get(selectedPoi));
                file.delete();
                list.remove(selectedPoi--);
                adapter.notifyDataSetChanged();
                break;
        }
    }

    private String getMIMEType(String s) {

        return "audio/*";
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, MediaRecorderActivity.class);
        context.startActivity(starter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selectedPoi = position;
        if (mediaFile == null) {
            mediaFile = new File(mountedDir.getAbsolutePath(), list.get(selectedPoi));
        }
    }

    private void getMediaRecordsList() {
        if (mountedDir != null && mountedDir.exists()) {
            list.addAll(Arrays.asList(mountedDir.list()));
        }
    }

}
