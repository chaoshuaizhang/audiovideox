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
import android.util.AttributeSet;
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
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaExtractor mediaExtractor = new MediaExtractor();
                mediaExtractor.setDataSource(path);
                int trackCount = mediaExtractor.getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                    String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                    if (mime.contains("audio")) {
                        mediaExtractor.selectTrack(i);
                        int channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                                channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT);
                        //这种方式被舍弃了
//                        AudioTrack audioTrack1 = new AudioTrack(
//                                AudioManager.STREAM_MUSIC/*流类型*/,
//                                44100/*采样率*/,
//                                AudioFormat.CHANNEL_OUT_STEREO/*通道配置*/,
//                                AudioFormat.ENCODING_PCM_16BIT/*音频格式*/,
//                                minBufferSize/*存储音频的缓冲区大小*/,
//                                AudioTrack.MODE_STREAM
//                        );
                        //推荐使用以下方式创建AudioTrack
                        AudioAttributes attributes = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build();
                        AudioFormat format = new AudioFormat.Builder()
                                .setSampleRate(44100)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build();
                        int maxInputSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                        int mInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 : maxInputSize;
                        int frameSizeInBytes = channelCount * 2;
                        mInputBufferSize = (mInputBufferSize / frameSizeInBytes) * frameSizeInBytes;
                        AudioTrack audioTrack = new AudioTrack(attributes, format, mInputBufferSize,
                                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                        audioTrack.play();
                        MediaCodec mediaCodec = MediaCodec.createDecoderByType(mime);
                        mediaCodec.configure(trackFormat, null, null, 0);
                        mediaCodec.start();
                        //得到缓冲区的容量
                        int capacity = mediaCodec.getOutputBuffer(0).capacity();
                        capacity = capacity <= 0 ? minBufferSize : capacity;
                        byte[] audioOutTempBuf = new byte[capacity];
                        boolean isSourceEOS = false;
                        long timeMillis = System.currentTimeMillis();
                        while (true) {
                            //未读到文件结尾
                            if (!isSourceEOS) {
                                //继续读
                                isSourceEOS = decodeMediaData(mediaExtractor, mediaCodec);
                            }
                            int index = mediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
                            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                // no output available yet
                            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                // not important for us, since we're using Surface
                            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat newFormat = mediaCodec.getOutputFormat();
                            } else if (index < 0) {
                                throw new RuntimeException(
                                        "unexpected result from decoder.dequeueOutputBuffer: " +
                                                index);
                            } else {
                                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                                decodeDelay(mBufferInfo, timeMillis);
                                if (mBufferInfo.size > 0) {
                                    if (audioOutTempBuf.length < mBufferInfo.size) {
                                        audioOutTempBuf = new byte[mBufferInfo.size];
                                    }
                                    //设置buffer的位置
                                    outputBuffer.position(0);
                                    outputBuffer.get(audioOutTempBuf, 0, mBufferInfo.size);
                                    outputBuffer.clear();
                                    if (audioTrack != null) {
                                        audioTrack.write(audioOutTempBuf, 0, mBufferInfo.size);
                                    }
                                }
                                mediaCodec.releaseOutputBuffer(index, false);
                                break;
                            }
                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean decodeMediaData(MediaExtractor extractor, MediaCodec mediaCodec) {
        boolean isSourceEOS = false;
        //返回缓冲区有效的索引
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            int bufferIndex = mediaCodec.dequeueInputBuffer(10000);
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(bufferIndex);
            int sampleData = extractor.readSampleData(inputBuffer, 0);
            if (sampleData < 0) {
                mediaCodec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isSourceEOS = true;
            } else {
                mediaCodec.queueInputBuffer(bufferIndex, 0, sampleData, extractor.getSampleTime(), 0);
                extractor.advance();
            }
        }
        return isSourceEOS;
    }

    private static void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMillis) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public interface IVideoSize {
        void getWidthHeight(int width, int height);
    }

}