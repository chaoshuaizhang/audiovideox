# 开始图片、音视频的学习
>希望可以坚持！

[TOC]


## Surface介绍
>后期拍摄视频、图片、播放视频都需要Surface的参与，这里先介绍一下Surface的作用。

* 每一个window都对应一个surface，surface内部有维持了一个canvas和一个屏幕数据缓冲区buffer。**surface主要是用来管理数据的，不是进行绘制的，绘制用的canvas**

#### SurfaceView是什么
>SurfaceView继承自View，内部维护了一个Surface实例，可以理解为SurfaceView就是来控制Surface的（它不是真正意义上的view）。它可以用独立的线程进行绘制，不一定非得在主线程中。

* 创建SurfaceView时，会执行一个updateWindow方法，在这个方法里会创建一个MyWindow实例。
```java
protected void updateWindow(boolean force, boolean redrawNeeded) {
	if (mWindow == null) {
		Display display = getDisplay();
		//实例化window对象
		mWindow = new MyWindow(this);
		mLayout.type = mWindowType;
		mLayout.gravity = Gravity.START|Gravity.TOP;
		//IWindowSession的实例，IWS是一个AIDL接口
		mSession.addToDisplayWithoutInputChannel(mWindow, mWindow.mSeq,
				mLayout, mVisible ? VISIBLE : GONE, display.getDisplayId(),
				mContentInsets, mStableInsets);
	}
	//mSurfaceLock是一个ReentrantLock锁
	mSurfaceLock.lock();
	//window创建完成后，开始设置Surface的一系列属性，宽、高等，
	......
	mSurfaceLock.unlock();
}
```

##### SurfaceHolder
>Holder是个接口，提供控制SurfaceView中surface的方法：lockCanvas、addCallback(callBack)等，addCallback可以为holder设置Callback，对外暴漏三个方法：surfaceChanged、surfaceCreated、surfaceDestroyed来控制绘制流程。

```java
lockCanvas：获取一个canvas对象并锁定（线程安全，防止同一时间有多个线程对canvas写入）。
lockCanvas(Rect dirty)：锁定canvas的指定的dirty区域。
surfaceChanged：surface发生变化时（大小、格式等）。
surfaceCreated：surface创建完成后（即可以开始绘制了）。
surfaceDestroyed：surface销毁前（停止绘制）。
```

## 音频
>下面分为录制和播放两部分

#### 关于采样率的介绍
>采样率：音频的采集过程要经过抽样、量化和编码三步。
>抽样需要关注抽样率。声音是机械波，其特征主要包括频率和振幅（即音调和音量），频率对应时间轴线，振幅对应电平轴线。采样是指间隔固定的时间对波形进行一次记录，采样率就是在1秒内采集样本的次数。量化过程就是用数字表示振幅的过程。编码是一个减少信息量的过程，任何数字音频编码方案都是有损的。PCM编码（脉冲编码调制）是一种保真水平较高的编码方式。在Android平台，44100Hz是唯一目前所有设备都保证支持的采样频率。但比如22050、16000、11025也在大多数设备上得到支持。8000是针对某些低质量的音频通信使用的。
>[参考](www.jianshu.com/p/ce88092fabfa)

### 录制音频
>* MediaRecorder：可以用于音频、视频的捕获。
>* AudioRecord：可以直接放访问音频流，可实现边录边播。

#### MediaRecorder
>MediaRecorder可以直接把麦克风的声音转存为本地文件，并且能够直接编码为mp3、amr等。
>具体输出格式应该如何选择，还不清楚！！！

##### MediaRecorder使用
```java
MediaRecorder recorder = new MediaRecorder();
//设置录音来源为麦克风
recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//设置MediaRecorder输出格式
recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
//设置使用的编码
recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//设置输出的文件
recorder.setOutputFile(mediaFile.getAbsolutePath());
//预开始，做一些启动前的准备，主要是设置输出文件
recorder.prepare();
//开始捕获音频
recorder.start();
```
>上边AudioSource的来源有好多：**MIC麦克风**、**VOICE_CALL电话**、CAMCORDER摄像头旁边的麦克风、VOICE_RECOGNITION语音识别、**VOICE_COMMUNICATION语音通信**，标黑的是常用的。

#### AudioRecord
>* AudioRecord录制的音频无法直接播放，需要用AudioTrack播放，同时可以做到边录边播放。
>* 如果把音频流写入到文件，可实现MediaRecorder同样的功能。
>* AudioRecord比较接近于低层，所以它的功能很少，但是可以自己封装，自由度大。

##### AudioRecord使用
```java
int minBufferSize = AudioRecord.getMinBufferSize(44100,
		AudioFormat.CHANNEL_IN_STEREO,
		AudioFormat.ENCODING_PCM_16BIT);
//通过声源、采样率、音频通道、音频格式、缓冲区大小构造Record实例
audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
		44100,
		AudioFormat.CHANNEL_IN_STEREO,
		AudioFormat.ENCODING_PCM_16BIT,
		minBufferSize);
//定义一个存放流的文件
file = File.createTempFile(SystemClock.elapsedRealtime() + "", ".pcm", mountedDir);
byte[] byteBuffer = new byte[minBufferSize];
//开始录制
audioRecord.startRecording();
DataOutputStream dos = new DataOutputStream(
			new BufferedOutputStream(new FileOutputStream(file)));
while (isRecording) {
	//读进去多少，写出来多少
	int i = audioRecord.read(byteBuffer, 0, byteBuffer.length);
	//写到文件中
	dos.write(byteBuffer, 0, i);
}
```
>生成的.pcm文件由AudioTrack进行播放（上述的编写方式也是老的API，新的API可参考下边的Track的，大致一样。新的API引入了建造者模式来构造实例）。

### 播放音频
>1. 使用系统自带播放器
>2. AudioTrack播放（播放原生音频-由AudioRecord录制的）
>3. MediaPlayer播放（可以播放视频、音频）

#### AudioTrack的使用

##### 构件实例
###### 旧的API的实现方式
1. 第一步设置最小缓冲区大小
```java
//根据音频数据的特性来确定要分配的最小缓冲区
int minBufferSize = AudioRecord.getMinBufferSize(44100,//采样率
		AudioFormat.CHANNEL_IN_STEREO,//声道数：MONO单声道、STERED双声道
		AudioFormat.ENCODING_PCM_16BIT//采样精度，一个采样点16位，两个字节);
//最终的缓冲区大小由native_get_min_buff_size决定
```
>上述关于采样率的设置有些疑问，网络上都是直接写死的8000或者44100。目前没有查到最好的配置方式，说是一般采用44.1KHz，48KHz。下边会使用一种动态获取的采样率的方式。
>通道数CHANNEL_IN和CHANNEL_OUT的区别是什么。
>缓冲区的大小是由帧Frame、ChannelCount声道数、采样精度决定的，这里音频中的Frame桢的概念：描述数据量的多少，一帧的大小等于一个采样点的字节数 * 声道数。（至于视频中的帧的概念，后期再看）

2. 第二步实例化AudioTrack
```java
AudioTrack audioTrack = new AudioTrack(
        AudioManager.STREAM_MUSIC/*音频流类型*/,
        44100/*采样率*/,
        AudioFormat.CHANNEL_IN_STEREO/*音频通道配置*/,
        AudioFormat.ENCODING_PCM_16BIT/*音频格式*/,
        minBufferSize/*存储音频的缓冲区大小*/,
        AudioTrack.MODE_STREAM/*数据加载模式*/
);
```
>* 音频流类型：有铃声、警告声、系统声音。这些类型的划分只和音频的管理策略有关（比如你可以为mp3格式的设为警告声类型，也完全可以）。
>* 音频通道：单声道、双声道
>* 音频格式：
>* 数据加载模式：有MODE_STREAM和MODE_STATIC两种。STREAM类似流的读取，一次次的读，从自己定义的buffer中读取到Track的buffer，这些在一定程度上会延时。STATIC是一次性把资源完整都过来，存在的问题就是资源不能太大，所以一般适用于铃声、通知之类的音频。

3. 播放
```java
//STREAM模式是先开启播放再write数据，STATIC模式是先write再play
audioTrack.play();
```

4. write写音频数据
```java
byte[] byteBuffer = new byte[minBufferSize];
//定义数据输入流
DataInputStream dis = new DataInputStream(
				new BufferedInputStream(new FileInputStream(file)));
//参数：缓冲区、写入的起始位置、偏移量
audioTrack.write(byteBuffer, 0, byteBuffer.length);
int length;

//使用STREAM加载模式时，需循环读取资源（上边说了，STREAM这种方式可能会造成延时）
do {
    length = dis.read(byteBuffer);
    //此时就完成了播放
    audioTrack.write(byteBuffer, 0, length);
} while (length != -1);
```
>对于STATIC加载方式，play操作需要放在write后进行。

5. 结束播放释放资源
```java
audioTrack.stop();
audioTrack.release();
```
>AudioTrack的构造过程写的比较详细，其它实例的构造过程可以参考这个，差不太多。

###### 新的API的创建方式
>之前创建的方式已被废弃
```java
AudioAttributes attributes = new AudioAttributes.Builder()
		//这个usage没什么大的作用
		.setUsage(AudioAttributes.USAGE_MEDIA)
		//设置内容格式
		.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
		.build();
AudioFormat audioFormat = new AudioFormat.Builder()
		//采样率
		.setSampleRate(audioSampleRate)
		.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
		.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
		.build();
audioTrack = new AudioTrack(
		audioAttributes,
		audioFormat,
		minBufferSize,
		AudioTrack.MODE_STREAM,
		AudioManager.AUDIO_SESSION_ID_GENERATE);
```
>新的API把旧的API做了一些封装，大致的参数设置还是一样的。

##### AudioTrack遇到的问题
1. AudioTrack的声道选择：选择单声道发现变声了，并且速度变快了，改为双通道即可。

#### MediaPlayer
>MediaPlayer是系统封装的比较好的一个播放器，内嵌的有很多功能。

##### MediaPlayer使用
```java
MediaPlayer mediaPlayer = new MediaPlayer();
//设置数据源
mediaPlayer.setDataSource(mountedDir.getAbsolutePath() + File.separator + list.get(selectedPoi));
mediaPlayer.setOnCompletionListener(
		new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				mp.stop();
				mp.release();
			}
		});
mediaPlayer.prepare();
mediaPlayer.start();
```
>暂时只演示了一个简单播放音频的功能。

## 拍照
>在介绍视频相关之前，先说一下拍照相关的知识。
>* 调用系统相机拍照
>* 自定义相机拍照

* 直接调用系统的拍照服务会发现得到的图片清晰度很差，为什么？Binder底层传值要求最大不超过1M，所以图片会被压缩。
    解决办法：拍照后不直接返回图片，而是存起来，然后我们去只当存储目录去拿。

### 调用系统相机拍照
>调用系统拍照后遇到了一些问题，就是能得到图片，但是打开系统相册里边却没有......

##### 使用流程
* 第一步：拍照
```java
/**
 * 调用系统服务拍照
 */
private void systemCameraTakePicture() {
	/**
	 * getExternalCacheDir应用关联缓存目录-在SD卡中单独开辟缓存区域存放当前应用的数
	 * 据，应用卸载后会被清除。getExternalCacheDir时，下边执行同步图片时，根本同步不
	 * 过去，跟是不是在xml中配置path无关
	 * */
	File file = new File(Environment.getExternalStorageDirectory(), "sys_camera_take_pic");
	if (!file.exists()) {
		file.mkdir();
	}
	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	currentFile = new File(file, System.currentTimeMillis() + ".jpg");
	//系统版本低于7.0时把file转为Uri对象（这个Uri标示着这个对象的真实路径），高于7.0时
	//会认为此方式不安全，需要使用内容提供者
	if (Build.VERSION.SDK_INT >= 24) {
		imageUri = FileProvider.getUriForFile(this, authority, currentFile);
	} else {
		imageUri = Uri.fromFile(currentFile);
	}
	intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
	startActivityForResult(intent, 1001);
}
```

* 第二步：得到拍摄的图片
>三种方式得到图片，但是需要同步到系统相册（比较坑），现在的实现方式在同步到相册以后，手机里会有两张同样的图片，暂时没有去解决（解决办法就是同步以后把删除一张呗）。

```java
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
	switch (requestCode) {
		case 1001:
			Log.d(TAG, "onActivityResult: " + currentFile.getAbsolutePath());
			Bitmap bitmap = null;
			//方式1：拍完后存在本地，然后再获取
			//bitmap = BitmapFactory.decodeFile(currentFile.getAbsolutePath());
			try {
				//方式2
				bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			//方式3：直接从系统相机返回数据（会被压缩）
			//Bitmap bitmap = (Bitmap) data.getExtras().get("data");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				surfaceView.setVisibility(View.GONE);
				constraintLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
			}
			/**
			 * 注意：上述步骤完成后会发现虽然拍完照了，图片回显了，但是系统相册里边没
			 * 有（但是我们设置的那个文件夹里有）。接下来需要把图片插入到系统相册（如
			 * 果需要的话），这里又分发广播同步/使用MediaScanner同步。
			 * */
			//方式1：先插入，再发广播扫描
			MediaStore.Images.Media.insertImage(getContentResolver(), bitmap,
					"无名：" + new Random().nextInt(100), "描述");
			//但是这里发广播不起作用
			Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			Uri uri;
			if (Build.VERSION.SDK_INT >= 24) {
				uri = FileProvider.getUriForFile(this, authority, currentFile);
			} else {
				uri = Uri.fromFile(currentFile);
			}
			intent.setData(uri);
			sendBroadcast(intent);
			//方式2：自定义MediaScanner扫描指定文件夹下的指定文件类型
			MyMediaScanner scanner = new MyMediaScanner(this);
			scanner.scanFileAndType(new String[]{currentFile.getAbsolutePath()}, new String[]{"image/jpeg"});
			//还有一个办法就是：直接把文件路径写在相册目录下-DCIM/Camera/xxx.jpg，然后再扫描更新
			break;
	}
}
```

### 自定义相机拍照
>目前使用自定义相机时使用的是Camera2。

#### ImageReader使用
>在拍照的过程中用到了这个类，发挥了重要角色，先看下如何使用。
>1. 实例化ImageReader
>2. 设置图片有效时的回调接口
>3. 得到有效的图片资源流
>4. 构造Bitmap，view显示Bitmap

1. 第一步：实例化ImageReader
```java
//设置reader的大小、格式、maxImages表示用户希望能同时从ImageReader中获取到的图片最大数量
ImageReader imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 1);
```
>对于maxImages的设置，拍照时一般为1，我们只需要获取一张有效的图片。

2. 第二步：设置图片有效时的回调
```java
//设置回调接口
ImageReader.OnImageAvailableListener availableListener = new
	ImageReader.OnImageAvailableListener() {
		@Override
		public void onImageAvailable(ImageReader reader) {

		}
};
//设置处理消息的线程（null的话，在哪设置回调接口就在哪个线程处理消息）
Handler handler = new Handler(Looper.getMainLooper());
imageReader.setOnImageAvailableListener(availableListener, handler)
```

3. 第三步：图片生效，开始回调
```java
public void onImageAvailable(ImageReader reader) {
	Image image = reader.acquireNextImage();
	/*
	* getPlanes获取此图像的像素平面数组，平面数组的数量由图像的格式决定。
	* 但是这里为什么获取第0个Plane的Buffer？
	* 因为得到数组中元素的个数是由实例化Reader时设置的格式决定的。
	* JPEG对应1
	* YUV_420_888对应3
	* YUV_422_888对应3
	* 这块儿不同的格式还不是很清楚，暂时用的是JPEG，所以需要获取到数组的第一个元素
	* */
	Image.Plane[] planes = image.getPlanes();
	//这个图像对应的数据
	ByteBuffer buffer = planes[0].getBuffer();
	//声明一个数组（目的是得到buffer中的数据），remaining是buffer的有效字节长度
	byte[] bytes = new byte[buffer.remaining()];
	//buffer缓冲区中的数据写入到字节数组中
	buffer.get(bytes);
	//此时图片的数据流已经得到了，就在bytes中，下一步转为Bitmap就可以使用了
	Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
}
```
>上述就是ImageReader的简单使用流程，这块儿没有难度，有难度的是**如何把camera和ImageReader扯上关系**。

#### Camera2的使用流程
>1. 得到系统相机服务
>2. 得到指定的摄像头
>3. 打开摄像头
>4. 预览
>5. 拍照
>6. 图片回显

## 视频

### 视频录制
>视频分为预览、捕获

### 视频播放

### MediaExtractor
>从音视频资源文件分离音频和视频

### MediaMuxer
>目前理解的大致意思就是合成音频、视频文件，组成新的资源，只支持合成一个视频轨道和一个音频轨道。
### MediaCodec
https://bigflake.com/mediacodec/

### MediaExtractor结合MediaCodec播放音视频
https://www.jianshu.com/p/ec5fd369c518

//这个不错
https://blog.csdn.net/zhi184816/article/details/52514138

### 编辑视频（类似抖音拍完之后编辑视频）

1. 给视频添加背景音乐
    添加背景音乐时：如果去除原来的背景音频还好说，如果不去除的话，相当于一个视频需要有多个音乐，MediaMuxer只支持一个音频、视频轨道。
2. 添加水印
3. 改变视频的色彩
4. 分别对各桢进行处理


### 遇到的问题
1. jdk问题
```java
Caused by: java.lang.ClassNotFoundException: Didn't find class "com.example.audiovideox.MainActivity" on path: DexPathList[[zip file "/data/app/com.example.audiovideox-1/base.apk"],nativeLibraryDirectories=[/data/app/com.example.audiovideox-1/lib/arm64, /system/lib64, /vendor/lib64]]
```
>原因是：默认用了android-studio自带的jre环境，改成本地的，解决。

2. 不同进程数据共享问题
```java
2019-09-14 10:35:31.211 17458-17458/com.example.audiovideox W/System.err: android.os.FileUriExposedException: file:///storage/emulated/0/Android/data/com.example.audiovideox/files/mounted/1568428475196423258879.amr exposed beyond app through Intent.getData()
```
>android 7.0发生了一些行为变化，禁止应用程序向外部公开file://的URI。尝试传递file://URI会触发FileUriExposedException。应用程序之间共享数据，应该发送content://的URI，且授予URI临时访权限。推举使用FileProvider。

3. 文件无法删除
```java
//删除文件,删除的是创建File对象时指定与之关联创建的那个文件.
file.delete()
//在JVM进程退出的时候删除文件,通常用在临时文件的删除.
//这个方法不能立即删除文件，至于什么时候删除，需要再看
file.deleteOnExit();
```
4. 使用ConstraintLayout时布局遮挡
>使用wracontent后产生布局遮挡问题（本来是想实现linearlayout权重一样的效果）。解决办法：wrapcontent改为0dp。

5. 优雅的停止线程
Thread.interrupted方法并不能每次都起作用，所以我们可以先中断，然后在for循环里判断是否中断了，如果是，跳出for循环。



#### 参考的博客
[参考](https://www.cnblogs.com/renhui/p/7452572.html)

[参考](https://www.cnblogs.com/elesos/p/7644597.html)

[参考](https://blog.51cto.com/ticktick)


##### 自定义相册时，Cursor的每一列 列名
>与MediaStore类中的Images类相对应，我们需要列名时可以直接使用Images类中的静态常量。
```java
_id
Type = 9110
_data
Type = /storage/emulated/0/DCIM/Camera/IMG_20190814_115838.jpg
_size
Type = 2483385
_display_name
Type = IMG_20190814_115838.jpg
mime_type
Type = image/jpeg
title
Type = IMG_20190814_115838
date_added
Type = 1565755118
is_hdr
Type = 0
date_modified
Type = 1565755118
description
Type = null
picasa_id
Type = null
isprivate
Type = null
latitude
Type = null
longitude
Type = null
datetaken
Type = 1565755118056
orientation
Type = 0
mini_thumb_magic
Type = null
bucket_id
Type = -1739773001
bucket_display_name
Type = Camera
width
Type = 2976
height
Type = 3968
is_hw_privacy
Type = null
hw_voice_offset
Type = null
is_hw_favorite
Type = null
hw_image_refocus
Type = null
album_sort_index
Type = null
bucket_display_name_alias
Type = null
is_hw_burst
Type = 0
hw_rectify_offset
Type = null
special_file_type
Type = 0
special_file_offset
Type = null
```

* 下边两行代码的性质是一样的，MediaStore对下边的做了更好的封装。
```java
Cursor cursor = MediaStore.Images.Media.query(...);
getContentResolver().query(.....)
```
https://juejin.im/user/5cac7dc26fb9a06885399b1c

实时Android语音对讲系统架构
https://www.jianshu.com/p/ce88092fabfa

仿抖音：
https://www.jianshu.com/p/92b4be81779b
https://www.jianshu.com/p/5bb7f2a0da90

关于自定义拍照的参考
https://www.jianshu.com/p/73fed068a795
https://www.jianshu.com/p/7f766eb2f4e7
https://www.cnblogs.com/wnpp/articles/7816962.html
https://www.cnblogs.com/renhui/p/7472778.html
https://github.com/WangYantao/android-camera-demos/blob/master/app/src/main/java/com/demo/demos/utils/CameraUtils.java
ContentProvider的参考
https://juejin.im/post/5c778523e51d45063631172d