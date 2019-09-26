package com.example.audiovideox.primary;

public interface BasePresenter<V extends BaseActivity, P extends BasePresenter> {
    P attachView(V v);
}
