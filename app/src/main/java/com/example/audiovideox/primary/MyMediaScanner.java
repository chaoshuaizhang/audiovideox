package com.example.audiovideox.primary;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

public class MyMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

    private static final String TAG = MyMediaScanner.class.getName();
    private MediaScannerConnection scanner;
    private String[] filesDir;
    private String[] types;

    public MyMediaScanner(Context context) {
        scanner = new MediaScannerConnection(context, this);
    }

    /**
     * 扫描某个文件夹下的某种类型的文件
     *
     * @param filesDir
     * @param types
     */
    public void scanFileAndType(String[] filesDir, String[] types) {
        this.filesDir = filesDir;
        this.types = types;
        scanner.connect();
    }

    @Override
    public void onMediaScannerConnected() {
        for (int i = 0; i < filesDir.length; i++) {
            scanner.scanFile(filesDir[i], types[i]);
        }
        //扫描结束
        filesDir = null;
        types = null;
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        Log.d(TAG, "onScanCompleted: " + path);
    }
}
