package com.example.audiovideox.primary;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audiovideox.BaseRecyclerAdapter;
import com.example.audiovideox.R;
import com.example.audiovideox.primary.view.MyPreviewVideoImage;
import com.example.audiovideox.util.cache.ImageLoaderUtil;
import com.example.audiovideox.util.cache.MemoryCache;
import com.example.audiovideox.util.cache.MyBitmapCache;

import java.util.List;

public class VideosAdapter extends BaseRecyclerAdapter<String[], VideosAdapter.MyViewHolder> {

    private String TAG = "VideosAdapter";
    private RadioButton selectedRB;
    private int selectedPosition = -1;
    private ImageLoaderUtil imageLoader;
    private String selectedPath;
    private String selectedPath2;

    public String getSelectedPath() {
        return selectedPath;
    }

    public String getSelectedPath2() {
        return selectedPath2;
    }

    public VideosAdapter(List<String[]> datas, Context context, int resId) {
        super(datas, context, resId);
        imageLoader = new ImageLoaderUtil(new MyBitmapCache());
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder holder, final int position) {
        holder.textView.setText(datas.get(position)[0]);
        holder.radioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, position + "  onCheckedChanged: " + isChecked);
                if (isChecked) {
                    selectedPosition = position;
                    if (selectedRB != null) {
                        //这块儿执行后会回掉onCheckedChanged方法
                        selectedRB.setChecked(false);
                    }
                    selectedRB = holder.radioButton;
                    //但是这块儿不会回掉onCheckedChanged方法
                    selectedRB.setChecked(true);
                    selectedPath = datas.get(selectedPosition)[1];
                    selectedPath2 = datas.get(selectedPosition + 1)[1];
                }
            }
        });
        holder.radioButton.setChecked(position == selectedPosition);
        MyPreviewVideoImage image = imageLoader.displayVideoFrameByPath(holder.imageView, datas.get(position)[1], 10);
        holder.timeTv.setText(String.valueOf(image.getTime()));
    }

    @Override
    protected MyViewHolder getViewHolder(View view) {
        return new MyViewHolder(view);
    }

    class MyViewHolder extends BaseRecyclerAdapter.BaseRecyclerHolder {
        private TextView textView;
        private TextView timeTv;
        private ImageView imageView;
        private RadioButton radioButton;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv);
            radioButton = itemView.findViewById(R.id.radio_btn);
            imageView = itemView.findViewById(R.id.img_preview);
            timeTv = itemView.findViewById(R.id.tv_time);
        }
    }

}
