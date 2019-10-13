package com.example.audiovideox.primary;

public class CameraPresenter implements BasePresenter<CameraRecordAudioActivity, CameraPresenter> {

    private BaseActivity activity;

    @Override
    public CameraPresenter attachView(CameraRecordAudioActivity cameraRecordAudioActivity) {
        this.activity = cameraRecordAudioActivity;
        return this;
    }
}