package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.example.audiovideox.SpeedControlCallback;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

public class VideoUtil {

    private static final String TAG = "VideoUtil";
    private static MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    /**
     * 得到指定路径视频的某一帧
     *
     * @param path
     * @param time
     * @return
     */
    public static Bitmap getFrameAtTime(String path, long time) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        Bitmap frameAtTime = retriever.getFrameAtTime(time);
        return frameAtTime;
    }

    public static void playVideo(String path, TextureView textureView, IVideoSize videoSize) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            MediaExtractor mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(path);
                //得到资源的通道数
                int trackCount = mediaExtractor.getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    //得到通道的一些参数
                    MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                    int width = trackFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    videoSize.getWidthHeight(width, height);
                    String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                    Log.d(TAG, "playVideo: " + mime);
                    if (mime.contains("video")) {
                        mediaExtractor.selectTrack(i);
                        doExtract(mediaExtractor, trackFormat, mime, textureView, i);
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
        SpeedControlCallback callback = new SpeedControlCallback();
        callback.setFixedPlaybackRate(20);
        int inputChunk = 0;
        long firstInputTimeNsec = -1;
        MediaCodec mediaCodec = null;
        try {
            //创建解码器
            mediaCodec = MediaCodec.createDecoderByType(mime);
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            Surface surface = new Surface(surfaceTexture);
            mediaCodec.configure(trackFormat, surface, null, 0);
            //启动解码器MediaCodec
            mediaCodec.start();
            //得到输入缓冲区的集合
            ByteBuffer[] decoderInputBuffers = mediaCodec.getInputBuffers();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                //输入缓冲区是否已满
                boolean inBufferDone = false;
                //输出缓冲区是否已满
                boolean outBufferDone = false;

                while (!outBufferDone) {
                    if (!inBufferDone) {
                        int dequeueInputBuffer = mediaCodec.dequeueInputBuffer(10000);
                        if (dequeueInputBuffer >= 0) {
                            if (firstInputTimeNsec == -1) {
                                firstInputTimeNsec = System.nanoTime();
                            }
                            ByteBuffer inputBuf = decoderInputBuffers[dequeueInputBuffer];
                            int chunkSize = mediaExtractor.readSampleData(inputBuf, 0);
                            if (chunkSize < 0) {
                                mediaCodec.queueInputBuffer(dequeueInputBuffer, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inBufferDone = true;
                            } else {
                                if (mediaExtractor.getSampleTrackIndex() != trackIndex) {
                                    Log.w(TAG, "WEIRD: got sample from track " +
                                            mediaExtractor.getSampleTrackIndex() + ", expected " + trackIndex);
                                }
                                long presentationTimeUs = mediaExtractor.getSampleTime();
                                mediaCodec.queueInputBuffer(dequeueInputBuffer, 0, chunkSize,
                                        presentationTimeUs, 0);
                                inputChunk++;
                                mediaExtractor.advance();
                            }
                        }
                    }

                    if (!outBufferDone) {
                        int decoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            // not important for us, since we're using Surface
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = mediaCodec.getOutputFormat();
                        } else if (decoderStatus < 0) {
                            throw new RuntimeException(
                                    "unexpected result from decoder.dequeueOutputBuffer: " +
                                            decoderStatus);
                        } else { // decoderStatus >= 0
                            if (firstInputTimeNsec != 0) {
                                // Log the delay from the first buffer of input to the first buffer
                                // of output.
                                long nowNsec = System.nanoTime();
                                Log.d(TAG, "startup lag " + ((nowNsec - firstInputTimeNsec) / 1000000.0) + " ms");
                                firstInputTimeNsec = 0;
                            }
                            boolean doLoop = false;
                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outBufferDone = true;
                            }

                            boolean doRender = (mBufferInfo.size != 0);
                            if (doRender && callback != null) {
                                callback.preRender(mBufferInfo.presentationTimeUs);
                            }
                            mediaCodec.releaseOutputBuffer(decoderStatus, doRender);
                            if (doRender && callback != null) {
                                callback.postRender();
                            }

                            if (doLoop) {
                                Log.d(TAG, "Reached EOS, looping");
                                mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                                inBufferDone = false;
                                mediaCodec.flush();    // reset decoder state
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
                mediaExtractor = null;
            }
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
                        audioCodec = MediaCodec.createDecoderByType(mime);
                        audioCodec.configure(mediaFormat, null, null, 0);
                        break;
                    }
                }
                audioCodec.start();
                MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
                boolean isAudioEOS = false;
                long startMs = System.currentTimeMillis();
                //int i = audioCodec.dequeueInputBuffer(10000);
                //ByteBuffer buffer = audioCodec.getInputBuffer(i);
                //int capacity = buffer.capacity();
                //if (capacity <= 0) {
                //    capacity = minBufferSize;
                //}
                while (true) {
                    // 解码
                    if (!isAudioEOS) {
                        isAudioEOS = decodeMediaData(audioExtractor, audioCodec);
                    }
                    // 获取解码后的数据
                    int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, 10000);
                    //缓冲区大小相差很大，但是都可以播放，区别是？
                    //byte[] mAudioOutTempBuf = new byte[capacity];
                    byte[] mAudioOutTempBuf = new byte[minBufferSize];
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
                                if (mAudioOutTempBuf.length < audioBufferInfo.size) {
                                    mAudioOutTempBuf = new byte[audioBufferInfo.size];
                                }
                                outputBuffer.position(0);
                                outputBuffer.get(mAudioOutTempBuf, 0, audioBufferInfo.size);
                                outputBuffer.clear();
                                if (audioTrack != null)
                                    audioTrack.write(mAudioOutTempBuf, 0, audioBufferInfo.size);
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
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    public interface IVideoSize {
        void getWidthHeight(int width, int height);
    }

}