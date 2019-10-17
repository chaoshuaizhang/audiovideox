//package com.example.audiovideox.primary;
//
//public class MainActivity extends AppCompatActivity {
//    AudioTrack mAudioTrack;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        setContentView(R.layout.content_main);
//
//        Thread thread = new Thread() {//耗时的操作应该另外开一个线程
//            @Override
//            public void run() {
////创建分离器
//                MediaExtractor extractor = new MediaExtractor();
//                try {
//                    extractor.setDataSource("/sdcard/mo.mp3");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
////选择视频流 创建解码器
//                MediaCodec mediaCodec = null;
//                try {
//                    int tackNum = extractor.getTrackCount();
//                    for (int i = 0; i < tackNum; i++) {
//                        MediaFormat format = extractor.getTrackFormat(i);
//                        String mime = format.getString(MediaFormat.KEY_MIME);
//                        if (mime.startsWith("audio/")) {
//
//                            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                            int audioMinBufSize = AudioTrack.getMinBufferSize(sampleRate,
//                                    AudioFormat.CHANNEL_CONFIGURATION_STEREO,
//                                    AudioFormat.ENCODING_PCM_16BIT);
////创建音频输出环境
//                            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, audioMinBufSize, AudioTrack.MODE_STREAM);
//                            mAudioTrack.play();
//
//                            mediaCodec = MediaCodec.createDecoderByType(mime);
//                            mediaCodec.configure(format, null, null, 0);
//                            extractor.selectTrack(i);
//                        }
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
////启动解码器
//                mediaCodec.start();
//                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
//                ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
//                boolean inputEnd = false;
//                boolean outputEnd = false;
//                final long kTimeOutUs = 10000;
//                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//                while (!outputEnd) {
//                    try {
//                        if (!inputEnd) {//输入到解码器 进行解码
//                            int inputBufIndex = mediaCodec.dequeueInputBuffer(kTimeOutUs);
//                            if (inputBufIndex >= 0) {
//                                ByteBuffer dstBuf = inputBuffers[inputBufIndex];
//
//                                int sampleSize = extractor.readSampleData(dstBuf, 0);//从分离器拿数据
//                                if (sampleSize < 0) {
//                                    mediaCodec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                                    inputEnd = true;
//                                } else {
//                                    long mediatime = extractor.getSampleTime();
////将数据送入解码器
//                                    mediaCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, mediatime, inputEnd ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
//                                    extractor.advance();
//                                }
//                            }
//                        }
////从解码器输出
//                        int res = mediaCodec.dequeueOutputBuffer(info, kTimeOutUs); //将数据从解码器拿出来
//                        if (res >= 0) {
//                            int outputBufIndex = res;
//                            ByteBuffer buf = outputBuffers[outputBufIndex];
//                            final byte[] pcmData = new byte[info.size];
//                            buf.get(pcmData);
//                            buf.clear();
//                            if (pcmData.length > 0) {
////对音频数据pcm进行输出
//                                mAudioTrack.write(pcmData, 0, pcmData.length);
//                            }
//                            try {
//                                sleep(16);//多长时间刷新
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                                break;
//                            }
////告诉显示器释放并显示这个内容
//                            mediaCodec.releaseOutputBuffer(outputBufIndex, true);
//                        }
//
//                    } catch (RuntimeException e) {
//                        e.printStackTrace();
//                    }
//                }
//                if (mediaCodec != null) {
//                    mediaCodec.stop();
//                    mediaCodec.release();
//                    mediaCodec = null;
//                }
//                if (extractor != null) {
//                    extractor.release();
//                    extractor = null;
//                }
//
//            }
//        };
//        thread.start();
//    }
//}
