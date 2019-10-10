package com.example.audiovideox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.audiovideox.primary.view.MyCaptureSurfaceView;
import com.example.audiovideox.primary.view.MyTextureView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author changePosition
 */
public class CameraRecordVideoActivity extends AppCompatActivity {

    private static final String TAG = CameraRecordVideoActivity.class.getName();
    //相机管理器
    private CameraManager cameraManager;
    //前置后置标识
    private String cameraId;
    //相机硬件设备
    private CameraDevice cameraDevice;
    //相机设备状态改变回调函数
    private CameraDevice.StateCallback stateCallback;
    //
    private CameraCaptureSession preViewSession;
    //这个size用来设置预览界面ServiceView的大小
    private Size photoSize;
    private Size videoSize;
    private MyTextureView textureView;
    private MediaRecorder mediaRecorder;
    //视频文件
    private File videoFile;
    //文件挂载的目录
    private File mountedDir;
    private CaptureRequest.Builder builder;
    private boolean isRecording = false;
    private ScheduledExecutorService service = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long recordTime = 0;
    private MyRunnable runnable = new MyRunnable();
    private Button btnRecordTime;
    private boolean FACING_FRONT = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_record_video);
        textureView = findViewById(R.id.texture_view_video);
        btnRecordTime = findViewById(R.id.btn_record);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO}, 1002);
        }
        //初始化相机
        service = new ScheduledThreadPoolExecutor(1);
        initCamera();
    }

    private void initMediaRecorder() {
        //创建挂载目录
        mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "mediavideos");
        if (!mountedDir.exists()) {
            mountedDir.mkdir();
        }
        try {
            mediaRecorder = new MediaRecorder();
            //设置音频源为麦克风
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            //这块儿报错
            //mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
            //设置输出文件格式
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //构造输出文件
            videoFile = File.createTempFile(System.currentTimeMillis() + "", ".mp4", mountedDir);
            mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            //设置视频编码比特率
            mediaRecorder.setVideoEncodingBitRate(10000000);
            //设置视频帧速率（每秒钟捕获的视频帧数）
            mediaRecorder.setVideoFrameRate(30);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //如果不设置videoSize的话，拍出的视频会很模糊
                mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            }
            //设置视频编码
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //设置音频编码
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 初始化相机相关实例
     */
    private void initCamera() {
        //初始化cameraManager
        if (cameraManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            } else {
                Toast.makeText(this, "版本过低！", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //得到cameraId
                for (int i = 0, length = cameraManager.getCameraIdList().length; i < length; i++) {
                    //得到相机ID
                    cameraId = cameraManager.getCameraIdList()[i];
                    //得到此ID对应的相关属性
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                    int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    StreamConfigurationMap map = null;
                    //判断是否是后置摄像头
                    if (!FACING_FRONT && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        //得到相机支持的宽高集合
                        map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    } else if (FACING_FRONT && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    }
                    if(map == null){
                        continue;
                    }
                    Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                    photoSize = getPreViewSize(sizes);
                    videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        stateCallback = getStateCallback();
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    public CameraDevice.StateCallback getStateCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    //相机打开时回调，即调用openCamera后，回调到这里---开始预览
                    takePreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    //相机断开连接时回调
                    cameraDevice.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.d(TAG, "onError: ");
                }
            };
            return stateCallback;
        }
        return null;
    }

    public void videoClick(View view) {
        switch (view.getId()) {
            case R.id.btn_preview:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                //三个参数的意义：
                                cameraManager.openCamera(cameraId, stateCallback, null);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn_record:
                if (isRecording) {
                    service.shutdownNow();
                    try {
                        mediaRecorder.stop();
                        mediaRecorder.reset();
                        recordTime = 0;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    btnRecordTime.setText("录制");
                    isRecording = false;
                    break;
                }
                isRecording = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    preViewSession.close();
                    preViewSession = null;
                }
                initMediaRecorder();
                takeVideo();
                break;
            case R.id.btn_reverse:
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    preViewSession.close();
//                    preViewSession = null;
//                }
                FACING_FRONT = !FACING_FRONT;
                //重新初始化
                initCamera();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                //三个参数的意义：
                                cameraManager.openCamera(cameraId, stateCallback, null);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * 预览
     */
    private void takePreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(1500, 1500);
                Surface surface = new Surface(surfaceTexture);
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(surface);
                cameraDevice.createCaptureSession(Collections.singletonList(surface),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                preViewSession = session;
                                //自动对焦
                                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                //开启闪光灯
                                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                CaptureRequest captureRequest = builder.build();
                                try {
                                    //listener：每次请求完成处理时通知的回调对象。如果为空，则不会为此请求流生成元数据，尽管仍将生成图像数据
                                    preViewSession.setRepeatingRequest(captureRequest, null, null);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void takeVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                List<Surface> surfaces = new ArrayList<>();
                SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(1500, 1500);
                Surface surface = new Surface(surfaceTexture);
                builder.addTarget(surface);
                builder.addTarget(mediaRecorder.getSurface());
                surfaces.add(surface);
                surfaces.add(mediaRecorder.getSurface());
                cameraDevice.createCaptureSession(surfaces,
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                preViewSession = session;
                                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                try {
                                    preViewSession.setRepeatingRequest(builder.build(), null, null);
                                    service.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.SECONDS);
                                    mediaRecorder.start();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.d(TAG, "onConfigureFailed: ");
                            }
                        }, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param sizes
     * @return
     */
    private Size getPreViewSize(Size[] sizes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //sizes本身是按照屏幕分辨率排序的（注意是w * h的和来排序的）
            for (Size size : sizes) {
                Log.d(TAG, "getPreViewSize: " + size.getWidth() + "     " + size.getHeight() + "     " + (size.getWidth() * size.getHeight()));
                /**
                 * 宽高比例3：4，注意因为w和h是int类型，所以比较时注意，比如直接4/3=1，3/4=0
                 * 对于1080p、720p的含义是：假设分辨率是x * y，则较小的那个就表示多少p
                 * */
                if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() < 1080) {
                    return size;
                }
            }
        }
        return sizes[0];
    }

    private Size getViewoSize() {
        return null;
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, CameraRecordVideoActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.shutdown();
    }

    class MyRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: "+recordTime);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    btnRecordTime.setText((recordTime++)+" s");
                }
            });
        }
    }

}
