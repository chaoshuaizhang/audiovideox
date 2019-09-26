package com.example.audiovideox.primary;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.audiovideox.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AlbumFrag extends Fragment {

    private View view;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private AlbumAdapter albumAdapter;
    private List<String> list = new ArrayList<>();
    private boolean inited = false;
    private static String TAG = AlbumFrag.class.getName();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: ");
        view = inflater.inflate(R.layout.frag_layout, container, false);
        recyclerView = view.findViewById(R.id.recycler_view_album);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated: ");
        super.onViewCreated(view, savedInstanceState);
        layoutManager = new GridLayoutManager(getActivity(), 4);
        recyclerView.setLayoutManager(layoutManager);
        albumAdapter = new AlbumAdapter(list, getActivity());
        recyclerView.setAdapter(albumAdapter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onActivityCreated: ");
        super.onActivityCreated(savedInstanceState);
        if (!inited) {
            Log.d(TAG, "onHiddenChanged: 开始加载");
            loadPictures();
            inited = true;
        }
    }

    private void loadPictures() {
        /*
         * 解释一下几个参数
         * [id,name,desc]projection：需要查出那几列，数组中是列明
         * selection：类似where语句后面的条件，id=?d,name=?s
         * selectionArgs：前边的?d,?s对应的值，123，zcs
         * sortOrder：升序/降序
         * */
        Cursor cursor = MediaStore.Images.Media.query(getActivity().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, null, null,
                MediaStore.Images.Media.DATE_TAKEN + " desc");
        Log.d(TAG, "loadPictures: " + cursor.getCount());
        while (cursor.moveToNext()) {
            //得到某一列的值时很不友好，只能通过第几列来得到，无法直接通过列名
            Log.d(TAG, "loadPictures: date = " + cursor.getString(cursor.getColumnIndex("date_added")));
            //得到图片的路径
            String picPath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            Log.d(TAG, "loadPictures: path = " + picPath);
            list.add(picPath);
        }
        albumAdapter.notifyDataSetChanged();
    }

    public static AlbumFrag getInstance() {
        return new AlbumFrag();
    }

}
