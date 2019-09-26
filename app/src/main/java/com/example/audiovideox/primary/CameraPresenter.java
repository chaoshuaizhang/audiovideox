package com.example.audiovideox.primary;

public class CameraPresenter implements BasePresenter<CameraRecordVideoActivity, CameraPresenter> {

    private BaseActivity activity;

    @Override
    public CameraPresenter attachView(CameraRecordVideoActivity cameraRecordVideoActivity) {
        this.activity = cameraRecordVideoActivity;
        return this;
    }
}