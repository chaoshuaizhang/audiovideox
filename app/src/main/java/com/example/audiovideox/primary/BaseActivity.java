package com.example.audiovideox.primary;

import android.os.Bundle;
import androidx.annotation.Nullable;

import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity<P extends BasePresenter> extends AppCompatActivity {
    protected P presenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //生成一个presenter
        presenter = getPresenter();
        //把activity传给presenter
        presenter.attachView(this);
    }

    protected abstract P getPresenter();

}
