package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.example.audiovideox.SpeedControlCallback;
import com.example.audiovideox.primary.view.MyPreviewVideoImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static java.lang.Thread.sleep;

public class VideoUtil {

    private static final String TAG = "VideoUtil";

    /**
     * 得到指定路径视频的某一帧
     *
     * @param path
     * @param time
     * @return
     */
    public static MyPreviewVideoImage getFrameAtTime(String path, long time) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        if (path.startsWith("http")) {
            HashMap<String, String> params = new HashMap<>();
            params.put("Accept-Encoding", "gzip, deflate, sdch");
            params.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            retriever.setDataSource(path, params);
        } else {
            retriever.setDataSource(path);
        }
        Bitmap frameAtTime = retriever.getFrameAtTime(time);
        MyPreviewVideoImage image = new MyPreviewVideoImage(frameAtTime);
        String s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        image.setTime(Long.parseLong(s));
        retriever.release();
        return image;
    }

    public static JSONObject getWidthHeight(String path) {
        JSONObject obj = new JSONObject();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path, new HashMap<String, String>());
        int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        try {
            obj.put("width", width);
            obj.put("height", height);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static void playVideo(String path, TextureView textureView, IVideoSize videoSize) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            MediaExtractor mediaExtractor = new MediaExtractor();
            try {
                //设置数据的来源（本地资源文件或网络url）
                mediaExtractor.setDataSource(path);
                //得到资源的轨道数（音频轨道、视频轨道）
                int trackCount = mediaExtractor.getTrackCount();
                for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                    //选择自己需要的轨道的一些元数据信息(信息包括：采样率、声道数量、资源宽高、资源类型MIME、缓冲区大小、比特率)
                    MediaFormat trackFormat = mediaExtractor.getTrackFormat(trackIndex);
                    if (path.startsWith("http")) {
                        //网络资源
                        JSONObject widthHeight = getWidthHeight(path);
                        videoSize.getWidthHeight(widthHeight.getInt("width"), widthHeight.getInt("height"));
                    } else {
                        //本地资源
                        int width = trackFormat.getInteger(MediaFormat.KEY_WIDTH);
                        int height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);
                        videoSize.getWidthHeight(width, height);
                    }
                    String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                    Log.d(TAG, "playVideo: " + mime);
                    if (mime.contains("video")) {
                        mediaExtractor.selectTrack(trackIndex);
                        doExtract(mediaExtractor, trackFormat, mime, textureView, trackIndex);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * MediaCodecAPI的所有用法都遵循一种基本模式：
     * 1. 创建和配置MediaCodec对象
     * 2. 循环直到完成：
     * 2.1. 如果输入缓冲区已准备就绪：
     * 2.1.1. 读取大量输入，并将其复制到缓冲区中
     * 2.2. 如果输出缓冲区准备就绪：
     * 2.2.1. 复制缓冲区的输出
     * 3. 释放MediaCodec对象
     *
     * @param mediaExtractor
     * @param trackFormat
     * @param mime
     * @param textureView
     * @param trackIndex
     */
    private static void doExtract(MediaExtractor mediaExtractor, MediaFormat trackFormat, String mime, TextureView textureView, int trackIndex) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //创建mediaCodec
                MediaCodec mediaCodec = MediaCodec.createDecoderByType(mime);
                Surface surface = new Surface(textureView.getSurfaceTexture());
                mediaCodec.configure(trackFormat, surface, null, 0);
                mediaCodec.start();
                boolean isVideoEOS = false;
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                long timeMillis = System.currentTimeMillis();
                while (true) {
                    if (!isVideoEOS) {
                        isVideoEOS = decodeMediaData(mediaExtractor, mediaCodec);
                    }
                    int decodedBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    switch (decodedBufferIndex) {
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            break;
                        default:
                            decodeDelay(bufferInfo, timeMillis);
                            mediaCodec.releaseOutputBuffer(decodedBufferIndex, true);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void playAudio(String path) {
        try {
            //audioTrack需要的缓冲区大小
            int minBufferSize = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioTrack audioTrack = null;
                MediaCodec audioCodec = null;
                MediaExtractor audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(path);
                for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                    MediaFormat mediaFormat = audioExtractor.getTrackFormat(i);
                    //得到media类型
                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        //选择音频通道
                        audioExtractor.selectTrack(i);
                        //频道数量（）
                        int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate,
                                (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                                AudioFormat.ENCODING_PCM_16BIT);
                        AudioAttributes attributes = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build();
                        AudioFormat format = new AudioFormat.Builder()
                                .setSampleRate(audioSampleRate)
                                //单声道和双声道区别真的很大
                                .setChannelMask(audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build();
                        audioTrack = new AudioTrack(attributes, format, minBufferSize,
                                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                        //之前的Api可以同时设置左右声道的声音，但是现在被废弃了
                        //audioTrack.setStereoVolume(0.1f,1);
                        audioTrack.setVolume(1);
                        audioTrack.play();
                        //根据资源类型实例化一个MediaCodec
                        audioCodec = MediaCodec.createDecoderByType(mime);
                        /*
                         * 配置编码器，四个参数
                         * format：为MediaCodec配置的编码、解码器
                         * surface：是否需要渲染（播放视频时需要有一个surface来承接输出的数据，只播放音频时传null）
                         * crypto：加密对象-保证数据解码的安全（对于非安全编解码器，传null）
                         * flags：
                         * */
                        audioCodec.configure(mediaFormat, null, null, 0);
                        break;
                    }
                }
                //
                MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
                audioCodec.start();
                //音频资源读取完成
                boolean isAudioEOS = false;
                long startMs = System.currentTimeMillis();
                while (true) {
                    // 解码
                    if (!isAudioEOS) {
                        isAudioEOS = decodeMediaData(audioExtractor, audioCodec);
                    }
                    // 获取解码后的数据
                    int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, 10000);
                    //缓冲区大小相差很大，但是都可以播放，区别是？
                    //byte[] audioOutTempBuf = new byte[capacity];
                    byte[] audioOutTempBuf = new byte[minBufferSize];
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            //
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            //调用超时
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            //虽然这里是被丢弃的，但是依然会得到这个值，所以需要写一下，否则会执行到default中
                            break;
                        default:
                            ByteBuffer outputBuffer = audioCodec.getOutputBuffer(outputBufferIndex);
                            // 延时解码，跟视频时间同步
                            decodeDelay(audioBufferInfo, startMs);
                            // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                            if (audioBufferInfo.size > 0) {
                                if (audioOutTempBuf.length < audioBufferInfo.size) {
                                    audioOutTempBuf = new byte[audioBufferInfo.size];
                                }
                                outputBuffer.position(0);
                                outputBuffer.get(audioOutTempBuf, 0, audioBufferInfo.size);
                                outputBuffer.clear();
                                if (audioTrack != null)
                                    audioTrack.write(audioOutTempBuf, 0, audioBufferInfo.size);
                            }
                            // 释放资源
                            audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                            break;
                    }

                    // 结尾了
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }

                // 释放MediaCode 和AudioTrack
                audioCodec.stop();
                audioCodec.release();
                audioExtractor.release();
                audioTrack.stop();
                audioTrack.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean decodeMediaData(MediaExtractor extractor, MediaCodec decoder) {
        boolean isMediaEOS = false;
        int inputBufferIndex;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            inputBufferIndex = decoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                int sampleSize = extractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    isMediaEOS = true;
                } else {
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }
        }
        return isMediaEOS;
    }

    private static void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMillis) {
                try {
                    Log.d(TAG, "decodeDelay: " + Thread.currentThread().getName());
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    /**
     * 使用MediaMuxer来实现将视频流和音频流合成输入到mp4容器中
     *
     * @param videoPath
     * @param audioPath
     */
    public static void composeVideoAudio(String videoPath, String audioPath, String composePath) {
        MediaFormat videoFormat = getTrack(videoPath, "video");
        MediaFormat audioFormat = getTrack(audioPath, "audio");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                MediaMuxer muxer = new MediaMuxer(composePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int videoTrackIndex = muxer.addTrack(videoFormat);
                int audioTrackIndex = muxer.addTrack(audioFormat);
                muxer.start();
                ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static MediaFormat getTrack(String path, String mime) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(path);
                int videoTrackCount = extractor.getTrackCount();
                for (int i = 0; i < videoTrackCount; i++) {
                    MediaFormat trackFormat = extractor.getTrackFormat(i);
                    //选择需要的轨道
                    if (trackFormat.getString(MediaFormat.KEY_MIME).contains(mime)) {
                        extractor.selectTrack(i);
                        return trackFormat;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface IVideoSize {
        void getWidthHeight(int width, int height);
    }

}