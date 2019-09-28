package com.example.audiovideox.primary;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.audiovideox.R;
import com.example.audiovideox.primary.view.MyTextureView;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class CameraRecordAudioActivity extends BaseActivity<CameraPresenter> {

    private ConstraintLayout constraintLayout;
    private String TAG = "CameraRecordAudioActivity";
    private File currentFile = null;
    private MyTextureView surfaceView;
    private String authority = "com.example.audiovideox.fileprovider";
    private SurfaceHolder surfaceHolder;
    private CameraManager cameraManager;
    //相机标识-前置、后置
    private String cameraId = null;
    //
    private ImageReader imageReader;
    //相机底层设备
    private CameraDevice cameraDevice;
    //
    private Size photoSize;
    private Handler mainHandler = new Handler();
    //硬件相机 状态变更时的回调函数（打开、关闭相机等）
    private CameraDevice.StateCallback stateCallback;
    private CameraCaptureSession cameraCaptureSession;
    private TextView tvCameraParams;
    private Uri imageUri;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    //为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_record_audio);
        constraintLayout = findViewById(R.id.camera_bg);
        tvCameraParams = findViewById(R.id.tv_camera_params);
        initCamera();
        stateCallback = getStateCallback();
        initImageReader();
        //初始化serviceView
        initServiceView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "onCreate: " + photoSize.getHeight() + "     " + photoSize.getWidth());
            surfaceView.setAspectRation(photoSize.getHeight(), photoSize.getWidth());
        }
    }

    @Override
    protected CameraPresenter getPresenter() {
        return new CameraPresenter().attachView(this);
    }

    /**
     * 初始化StateCallback
     *
     * @return
     */
    private CameraDevice.StateCallback getStateCallback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            return new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    takePreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    if (null != cameraDevice) {
                        cameraDevice.close();
                        cameraDevice = null;
                    }
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Toast.makeText(CameraRecordAudioActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
                }
            };
        } else {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initServiceView() {
        surfaceView = findViewById(R.id.surface_view);
        //由自定义SurfaceView改为自定义TextureView后，不需要设置Callback
//        surfaceHolder = surfaceView.getHolder();
//        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//                initImageReader();
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//                Log.d(TAG, "surfaceChanged: " + width + "     " + height);
//            }
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                if (cameraDevice != null) {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                        cameraDevice.close();
//                    }
//                    cameraDevice = null;
//                }
//            }
//        });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void btnClick(View view) {
        switch (view.getId()) {
            case R.id.camera_native:
                if (checkCallingPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
                        && checkCallingPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
                } else {
                    systemCameraTakePicture();
                }
                break;
            case R.id.camera_custom:
                if (checkCallingPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 1003);
                } else {
                    openNativeCamera();
                }
                break;
            case R.id.camera_record_native:

                break;
        }
    }

    private String getCameraId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    //前置/后置摄像头判断
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }
                    Log.d(TAG, "getCamersIdList: " + cameraId);
                    this.cameraId = cameraId;
                    Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                    Arrays.sort(outputSizes, new Comparator<Size>() {
                        @Override
                        public int compare(Size o1, Size o2) {
                            return o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight();
                        }
                    });
                    Point point = new Point();
                    getWindowManager().getDefaultDisplay().getSize(point);
                    String params = "参数为：" + point.x + "  " + point.y + "\n";
                    for (int i = 0; i < outputSizes.length; i++) {
                        params += (outputSizes[i].getWidth() + "  " + outputSizes[i].getHeight() + "\n");
                    }
                    tvCameraParams.setText(params);
                    //此处得到最接近的大小
                    photoSize = outputSizes[outputSizes.length - 1];
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cameraId;
    }

    private void initCamera() {
        if (cameraManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            } else {
                Toast.makeText(this, "版本过低！", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        getCameraId();
    }

    private void openNativeCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    cameraManager.openCamera(cameraId, stateCallback, mainHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 调用系统服务拍照
     */
    private void systemCameraTakePicture() {
        //getExternalCacheDir应用关联缓存目录-zaiSD卡中单独开辟缓存区域存放当前应用的数据，引用卸载后会被清除
        //getExternalCacheDir时，下边执行同步时，根本同步不过去，跟是不是再xml中配置path无关
        File file = new File(Environment.getExternalStorageDirectory(), "sys_camera_take_pic");
        if (!file.exists()) {
            file.mkdir();
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        currentFile = new File(file, System.currentTimeMillis() + ".jpg");
        Log.d(TAG, "systemCameraTakePicture: "+currentFile.getAbsolutePath());
        //系统版本低于7.0时把file转为Uri对象（这个Uri标示着这个对象的真实路径），高于7.0时会认为此方式不安全，需要使用内容提供者
        if (Build.VERSION.SDK_INT >= 24) {
            Log.d(TAG, "systemCameraTakePicture: >= 24");
            imageUri = FileProvider.getUriForFile(this, authority, currentFile);
        } else {
            Log.d(TAG, "systemCameraTakePicture: < 24");
            imageUri = Uri.fromFile(currentFile);
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case 1001:
                Log.d(TAG, "onActivityResult: " + currentFile.getAbsolutePath());
                Bitmap bitmap = null;
                //拍完后存在本地，然后再获取
                //方式1
                //bitmap = BitmapFactory.decodeFile(currentFile.getAbsolutePath());
                try {
                    //方式2
                    bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                //方式3：直接从系统相机返回数据（会被压缩）
//                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    surfaceView.setVisibility(View.GONE);
                    constraintLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
                }

                //注意：上述步骤完成后会发现虽然拍完照了，图片回显了，但是系统相册里边没有（但是我们设置的那个文件夹里有）
                //接下来需要把图片插入到系统相册（如果需要的话）
                //这里又分发广播同步/使用MediaScanner同步
                String imagePath = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap,
                        "无名：" + new Random().nextInt(100), "描述");
                //但是这里发广播不起作用
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                Uri uri = FileProvider.getUriForFile(this, authority, currentFile);
                Uri uri = Uri.fromFile(currentFile);
                intent.setData(uri);
                sendBroadcast(intent);
////                Log.d(TAG, "onActivityResult---imagePath: "+imagePath);
                MyMediaScanner scanner = new MyMediaScanner(this);
                scanner.scanFileAndType(new String[]{currentFile.getAbsolutePath()},new String[]{"image/jpeg"});
                //还有一个办法就是：直接把文件路径写在相册目录下-DCIM/Camera/xxx.jpg，然后再扫描更新
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1002 || requestCode == 1003) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == 1002) {
                    systemCameraTakePicture();
                } else if (requestCode == 1003) {
                    openNativeCamera();
                }
            } else {
                Toast.makeText(this, "没有开启相机权限！", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 初始化相机
     */
    private void initImageReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 1);
            } else {
                imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
            }
            //设置ImageReader回调---拍照后回调，可从中获取到数据流
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cameraDevice.close();
                    }
                    //拿到拍照照片数据
                    Image image = reader.acquireNextImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    //由缓冲区存入字节数组
                    buffer.get(bytes);
                    image.close();
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    //隐藏预览界面，直接展示拍过的照片
                    surfaceView.setVisibility(View.GONE);

                    float width = constraintLayout.getWidth();
                    int bW = bitmap.getWidth();
                    int bH = bitmap.getHeight();
                    float height = bH / (bW / width);
                    ViewGroup.LayoutParams layoutParams = constraintLayout.getLayoutParams();
                    layoutParams.width = (int) width;
                    layoutParams.height = (int) height;
                    Log.d(TAG, "onActivityResult: " + width + " +++ " + height);
                    constraintLayout.setLayoutParams(layoutParams);

                    constraintLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
                }
            }, mainHandler);
        }
    }

    /**
     * 开始预览
     */
    private void takePreview() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                // 创建预览需要的CaptureRequest.Builder
                final CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                // 将SurfaceView的surface作为CaptureRequest.Builder的目标
                SurfaceTexture surfaceTexture = surfaceView.getSurfaceTexture();
                //很重要，不然预览界面会很坑（图片被拉伸、预览区域大小和实际拍摄区域大小不一致）
                surfaceTexture.setDefaultBufferSize(photoSize.getWidth(), photoSize.getHeight());
                Surface surface = new Surface(surfaceTexture);
                previewRequestBuilder.addTarget(surface);
                //previewRequestBuilder.addTarget(imageReader.getSurface());
                // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
                cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        if (null == cameraDevice)
                            return;
                        // 当摄像头已经准备好时，开始显示预览
                        CameraRecordAudioActivity.this.cameraCaptureSession = cameraCaptureSession;
                        try {
                            //自动对焦
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            // 自动对焦
                            //previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_TRIGGER_START);
                            // 打开闪光灯
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                            // 显示预览
                            CaptureRequest previewRequest = previewRequestBuilder.build();
                            //CameraCaptureSession调用captuer开始拍照，调用setRepeatingRequest进行预览
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, mainHandler);
                            surfaceView.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    takePicture();
                                    return true;
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(CameraRecordAudioActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                    }
                }, mainHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 拍照
     */
    private void takePicture() {
        if (cameraDevice == null)
            return;
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                // 将imageReader的surface作为CaptureRequest.Builder的目标
                captureRequestBuilder.addTarget(imageReader.getSurface());
                // 自动对焦
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_TRIGGER_START);
                // 自动曝光
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 获取手机方向
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                // 根据设备方向计算设置照片的方向
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
                // 拍照
                CaptureRequest mCaptureRequest = captureRequestBuilder.build();
                //CameraCaptureSession调用captuer开始拍照，调用setRepeatingRequest进行预览
                cameraCaptureSession.capture(mCaptureRequest, null, mainHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, CameraRecordAudioActivity.class);
        context.startActivity(starter);
    }
}
