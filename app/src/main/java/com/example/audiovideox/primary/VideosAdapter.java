package com.example.audiovideox.primary;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audiovideox.BaseRecyclerAdapter;
import com.example.audiovideox.R;

import java.util.List;

public class VideosAdapter extends BaseRecyclerAdapter<String, VideosAdapter.MyViewHolder> {

    private String TAG = "VideosAdapter";
    private RadioButton selectedRB;
    private int selectedPosition = -1;

    public VideosAdapter(List<String> datas, Context context, int resId) {
        super(datas, context, resId);
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder holder, final int position) {
        holder.textView.setText(datas.get(position));
        holder.radioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, position+"  onCheckedChanged: "+isChecked);
                if (isChecked) {
                    selectedPosition = position;
                    if (selectedRB != null) {
                        //这块儿执行后会回掉onCheckedChanged方法
                        selectedRB.setChecked(false);
                    }
                    selectedRB = holder.radioButton;
                    //但是这块儿不会回掉onCheckedChanged方法
                    selectedRB.setChecked(true);
                }
            }
        });
        holder.radioButton.setChecked(position == selectedPosition);
    }

    @Override
    protected MyViewHolder getViewHolder(View view) {
        return new MyViewHolder(view);
    }

    class MyViewHolder extends BaseRecyclerAdapter.BaseRecyclerHolder {
        private TextView textView;
        private RadioButton radioButton;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv);
            radioButton = itemView.findViewById(R.id.radio_btn);
        }
    }

}
