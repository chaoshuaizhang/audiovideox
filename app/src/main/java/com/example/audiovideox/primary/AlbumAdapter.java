package com.example.audiovideox.primary;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.audiovideox.R;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumVH> {

    private static final String TAG = AlbumAdapter.class.getName();
    private List<String> mData;
    private Context mContext;

    public AlbumAdapter(List<String> data, Context context) {
        mData = data;
        mContext = context;
    }

    @NonNull
    @Override
    public AlbumVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_album_layout, parent, false);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = params.width = parent.getMeasuredWidth() / 4;
        view.setLayoutParams(params);
        return new AlbumVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumVH holder, int position) {
        Uri uri = Uri.fromFile(new File(mData.get(position)));
        Glide.with(mContext).load(uri).centerCrop().into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

}

class AlbumVH extends RecyclerView.ViewHolder {
    ImageView imageView;

    public AlbumVH(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.image_view);
    }
}
