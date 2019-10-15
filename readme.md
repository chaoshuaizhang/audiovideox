# 开始图片、音视频的学习
>希望可以坚持！

## 音频

* 录制
    1. MediaRecorder
    2. Audio

* 播放
    1. 使用系统自带播放器
    2. AudioTrack播放（播放原生音频-由AudioRecord录制的）
    3. MediaPlayer播放（可以播放视频、音频）
        1. 关于MediaPlayer的create方法
        ```java
        //这个方法用了，但是返回的mediaPlayer实例是null
        MediaPlayer.create();
        ```

#### MediaRecord
>MediaRecorder可以直接把麦克风的声音转存为本地文件，并且能够直接编码为mp3、amr等。

* 采样率设置（清晰度等）


#### AudioRecord
>AudioRecord直接读取麦克风的音频流：
* 然后用AudioTrack播放流，做到边读边播放。
* 把流写入到文件，实现MediaRecorder同样的功能。
 
1. 直接通过AudioRecord录制的音频是无法直接播放的。
2. AudioTrack的声道选择：选择单声道发现变声了，并且速度变快了，改为双通道即可。

## 视频

### Camera拍照

* 直接调用系统的拍照服务会发现得到的图片清晰度很差，为什么？Binder底层传值要求最大不超过1M，所以图片会被压缩。
    解决办法：拍照后不直接返回图片，而是存起来，然后我们去只当存储目录去拿。
    
### Camera2
>关于自定义拍照的参考
https://www.jianshu.com/p/73fed068a795
https://www.jianshu.com/p/7f766eb2f4e7
https://www.cnblogs.com/wnpp/articles/7816962.html
https://www.cnblogs.com/renhui/p/7472778.html
https://github.com/WangYantao/android-camera-demos/blob/master/app/src/main/java/com/demo/demos/utils/CameraUtils.java

>ContentProvider的参考
https://juejin.im/post/5c778523e51d45063631172d

### MediaExtractor
>从音视频资源文件分离音频和视频

### MediaMuxer
>目前理解的大致意思就是合成音频、视频文件，组成新的资源
### MediaCodec

### MediaExtractor结合MediaCodec播放音视频
https://www.jianshu.com/p/ec5fd369c518

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
3. 使用ConstraintLayout时布局遮挡
>使用wracontent后产生布局遮挡问题（本来是想实现linearlayout权重一样的效果）。解决办法：wrapcontent改为0dp。



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