package com.example.audiovideox.primary;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.os.Bundle;

import com.example.audiovideox.R;

/**
 * @author changePosition
 */
public class ExtractorMuxerActivity extends AppCompatActivity {

    private MediaExtractor mediaExtractor;
    private MediaMuxer mediaMuxer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extractor_muxer);
    }
}
