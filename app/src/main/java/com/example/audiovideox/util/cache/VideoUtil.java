package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoUtil {

    private static final String TAG = "VideoUtil";

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

    public static void playVideo(String path) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            MediaExtractor mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(path);
                //得到资源的通道数
                int trackCount = mediaExtractor.getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    //得到通道的一些参数
                    MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                    String format = trackFormat.getString(MediaFormat.KEY_MIME);
                    Log.d(TAG, "playVideo: " + format);
                    if (format.contains("video")) {
                        int width = trackFormat.getInteger(MediaFormat.KEY_WIDTH);
                        int height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);
                        //单位是微秒，需要转换为秒
                        long time = trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000 / 1000;
                        mediaExtractor.selectTrack(i);

                        break;
                    }
                }
                ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);
                while ((mediaExtractor.readSampleData(buffer, 0)) > 0) {
                    mediaExtractor.advance();
                }
                mediaExtractor.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
