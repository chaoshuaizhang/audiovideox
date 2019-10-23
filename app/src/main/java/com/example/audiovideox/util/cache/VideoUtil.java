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
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
                while (!isVideoEOS && !Thread.interrupted()) {
                    isVideoEOS = decodeMediaData(mediaExtractor, mediaCodec);
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
                            STextureRender sTextureRender = new STextureRender();
                            sTextureRender.drawFrame(textureView.getSurfaceTexture(), false);
                            File mountedDir = new File("mediavideos");
                            saveFrame(mountedDir.getName());
                            break;
                    }
                    Log.d(TAG, "doExtract: INFO_OUTPUT_BUFFERS_CHANGED");
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
                while (true && !Thread.interrupted()) {
                    // 解码
                    if (!isAudioEOS) {
                        isAudioEOS = decodeMediaData(audioExtractor, audioCodec);
                    }
                    // 获取解码后的数据索引
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
                            decodeDelay(audioBufferInfo, startMs);
                            // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                            if (audioBufferInfo.size > 0) {
                                if (audioOutTempBuf.length < audioBufferInfo.size) {
                                    audioOutTempBuf = new byte[audioBufferInfo.size];
                                }
                                //没有用到（由下边的日志看出本身它每次都是0）
                                //outputBuffer.position(0);
                                Log.d(TAG, "playAudio: position=0 " + outputBuffer.position());
                                outputBuffer.get(audioOutTempBuf, 0, audioBufferInfo.size);
                                outputBuffer.clear();
                                Log.d(TAG, "playAudio: position=0 " + outputBuffer.position());
                                if (audioTrack != null)
                                    audioTrack.write(audioOutTempBuf, 0, audioBufferInfo.size);
                            }
                            // 释放资源(会在这块儿进行播放)
                            audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                            break;
                    }

                    // 结尾了-跳出循环
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                    Log.d(TAG, "playAudio: BUFFER_FLAG_END_OF_STREAM");
                }

                // 释放MediaCode 和 AudioTrack
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

    /**
     * 延迟解码
     *
     * @param bufferInfo
     * @param startMillis
     */
    private static void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            long delayMills;
            //当系统已经解码的视频时间与当前已经过去的系统时间，长度不匹配时需要延迟一会儿解码
            while ((delayMills = bufferInfo.presentationTimeUs / 1000 - (System.currentTimeMillis() - startMillis)) > 0) {
                try {
                    Log.d(TAG, "decodeDelay: " + delayMills);
                    sleep(delayMills);
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
        TMap videoMap = getTrack(videoPath, "video");
        TMap audioMap = getTrack(audioPath, "audio");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                MediaMuxer muxer = new MediaMuxer(composePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int videoTrackIndex = muxer.addTrack(videoMap.format);
                int audioTrackIndex = muxer.addTrack(audioMap.format);
                muxer.start();
                ByteBuffer videoBuffer = ByteBuffer.allocate(500 * 1024);
                ByteBuffer audioBuffer = ByteBuffer.allocate(500 * 1024);
                MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
                MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
                long interval = 0;
                long beforeAdvanceTime = 0;
                long afterAdvanceTime = 0;
                //开始合成视频
                while (true && !Thread.interrupted()) {
                    int videoSampleDataSize = videoMap.extractor.readSampleData(videoBuffer, 0);
                    if (videoSampleDataSize < 0) {
                        break;
                    }
                    videoBufferInfo.size = videoSampleDataSize;
                    videoBufferInfo.offset = 0;
                    videoBufferInfo.flags = videoMap.extractor.getSampleFlags();
                    videoBufferInfo.presentationTimeUs += interval < 0 ? 0 : interval;
                    muxer.writeSampleData(videoTrackIndex, videoBuffer, videoBufferInfo);
                    beforeAdvanceTime = videoMap.extractor.getSampleTime();
                    videoMap.extractor.advance();
                    afterAdvanceTime = videoMap.extractor.getSampleTime();
                    interval = afterAdvanceTime - beforeAdvanceTime;
                }
                //开始合成音频
                interval = 0;
                while (true && !Thread.interrupted()) {
                    int audioSampleDataSize = audioMap.extractor.readSampleData(audioBuffer, 0);
                    if (audioSampleDataSize < 0) {
                        //读取完成，跳出循环
                        break;
                    }
                    audioBufferInfo.size = audioSampleDataSize;
                    audioBufferInfo.offset = 0;
                    audioBufferInfo.flags = audioMap.extractor.getSampleFlags();
                    audioBufferInfo.presentationTimeUs += interval < 0 ? 0 : interval;
                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo);
                    beforeAdvanceTime = audioMap.extractor.getSampleTime();
                    audioMap.extractor.advance();
                    afterAdvanceTime = audioMap.extractor.getSampleTime() - beforeAdvanceTime;
                    interval = afterAdvanceTime - beforeAdvanceTime;
                }
                //合成视频完成
                muxer.stop();
                muxer.release();
                videoMap.extractor.release();
                audioMap.extractor.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static TMap getTrack(String path, String mime) {
        try {
            TMap tMap = new TMap();
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(path);
                int videoTrackCount = extractor.getTrackCount();
                for (int i = 0; i < videoTrackCount; i++) {
                    MediaFormat trackFormat = extractor.getTrackFormat(i);
                    //选择需要的轨道
                    if (trackFormat.getString(MediaFormat.KEY_MIME).contains(mime)) {
                        extractor.selectTrack(i);
                        tMap.extractor = extractor;
                        tMap.format = trackFormat;
                        tMap.track = i;
                        return tMap;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static class TMap {
        public MediaExtractor extractor;
        public MediaFormat format;
        public int track = -1;
    }

    public interface IVideoSize {
        void getWidthHeight(int width, int height);
    }

    private static ByteBuffer mPixelBuf;

    /**
     * Saves the current frame to disk as a PNG image.
     */
    public static void saveFrame(String filename) throws IOException {
        mPixelBuf.rewind();
        GLES20.glReadPixels(0, 0, 100, 100, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filename));
            Bitmap bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            mPixelBuf.rewind();
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.copyPixelsFromBuffer(mPixelBuf);
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }
    }

    /**
     * Code for rendering a texture onto a surface using OpenGL ES 2.0.
     */
    private static class STextureRender {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 0.f,
                1.0f, -1.0f, 0, 1.f, 0.f,
                -1.0f, 1.0f, 0, 0.f, 1.f,
                1.0f, 1.0f, 0, 1.f, 1.f,
        };

        private FloatBuffer mTriangleVertices;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uSTMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                        "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                        "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +      // highp here doesn't seem to matter
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n";

        private float[] mMVPMatrix = new float[16];
        private float[] mSTMatrix = new float[16];

        private int mProgram;
        private int mTextureID = -12345;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        public STextureRender() {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);

            Matrix.setIdentityM(mSTMatrix, 0);
        }

        public int getTextureId() {
            return mTextureID;
        }

        /**
         * Draws the external texture in SurfaceTexture onto the current EGL surface.
         */
        public void drawFrame(SurfaceTexture st, boolean invert) {
            checkGlError("onDrawFrame start");
            st.getTransformMatrix(mSTMatrix);
            if (invert) {
                mSTMatrix[5] = -mSTMatrix[5];
                mSTMatrix[13] = 1.0f - mSTMatrix[13];
            }

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES20.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        public void surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }

            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkLocation(maPositionHandle, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkLocation(maTextureHandle, "aTextureCoord");

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkLocation(muMVPMatrixHandle, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkLocation(muSTMatrixHandle, "uSTMatrix");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            mTextureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture mTextureID");

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        public void changeFragmentShader(String fragmentShader) {
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER;
            }
            GLES20.glDeleteProgram(mProgram);
            mProgram = createProgram(VERTEX_SHADER, fragmentShader);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Could not create program");
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        public void checkGlError(String op) {
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public static void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }
    }


}