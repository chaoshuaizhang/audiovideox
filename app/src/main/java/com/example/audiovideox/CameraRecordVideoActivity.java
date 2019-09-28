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
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.example.audiovideox.primary.view.MyCaptureSurfaceView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
    //得到相机获取的图片的大小
    private Size photoSize;
    private MyCaptureSurfaceView surfaceView;
    private MediaRecorder mediaRecorder;
    private SurfaceHolder surfaceHolder;
    private File videoFile;
    private File mountedDir;
    private CaptureRequest.Builder builder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_record_video);
        surfaceView = findViewById(R.id.surface_view_video);
        initSurfaceHolder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
        }
        initCamera();
    }

    private void initMediaRecorder() {
        mountedDir = new File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "mediavideos");
        if (!mountedDir.exists()) {
            mountedDir.mkdir();
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoFrameRate(30);
        try {
            videoFile = File.createTempFile(System.currentTimeMillis() + "", ".mp4", mountedDir);
            mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initSurfaceHolder() {
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
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
                    //判断是否是后置摄像头
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        //得到相机支持的宽高集合
                        StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                        //对宽高集合进行排序-升序
                        Arrays.sort(sizes, new Comparator<Size>() {
                            @Override
                            public int compare(Size o1, Size o2) {
                                return o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight();
                            }
                        });
                        Point point = new Point();
                        getWindowManager().getDefaultDisplay().getSize(point);
                        //此处得到最接近的大小
                        photoSize = sizes[sizes.length - 1];
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        stateCallback = getStateCallback();
    }

    public CameraDevice.StateCallback getStateCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    //相机打开时回调，即调用openCamera后，回调到这里---开始录制视频
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    preViewSession.close();
                    preViewSession = null;
                }
                initMediaRecorder();
                takeVideo();
                break;
            case R.id.btn_stop:
                mediaRecorder.stop();
                mediaRecorder.reset();
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
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(surfaceHolder.getSurface());
                cameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface()),
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
                builder.addTarget(surfaceHolder.getSurface());
                builder.addTarget(mediaRecorder.getSurface());
                cameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), mediaRecorder.getSurface()),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                preViewSession = session;
                                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                try {
                                    preViewSession.setRepeatingRequest(builder.build(), null, null);
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

    public static void start(Context context) {
        Intent starter = new Intent(context, CameraRecordVideoActivity.class);
        context.startActivity(starter);
    }

}
