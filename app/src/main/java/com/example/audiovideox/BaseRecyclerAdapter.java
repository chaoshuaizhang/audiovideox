package com.example.audiovideox;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public abstract class BaseRecyclerAdapter<T, VH extends BaseRecyclerAdapter.BaseRecyclerHolder> extends RecyclerView.Adapter<VH> {

    protected List<T> datas;
    protected Context context;
    protected int resId;

    public BaseRecyclerAdapter(List<T> datas, Context context, int resId) {
        this.datas = datas;
        this.context = context;
        this.resId = resId;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(resId, parent, false);
        return getViewHolder(view);
    }

    @Override
    public abstract void onBindViewHolder(@NonNull VH holder, int position);

    @Override
    public int getItemCount() {
        return datas.size();
    }

    protected abstract VH getViewHolder(View view);

    public class BaseRecyclerHolder extends RecyclerView.ViewHolder {
        public BaseRecyclerHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
