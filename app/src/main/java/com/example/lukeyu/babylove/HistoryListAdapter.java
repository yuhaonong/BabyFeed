package com.example.lukeyu.babylove;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by lukeyu on 2017/1/2.
 */

public class HistoryListAdapter extends BaseAdapter {
    private Context mContext;
    private List<String> mListItems;

    public HistoryListAdapter(Context context, List<String> items) {
        mContext = context;
        mListItems = items;
    }

    @Override
    public int getCount() {
        return mListItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mListItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (convertView == null) ? new TextView(mContext) : (TextView) convertView;
        view.setText((String) getItem(position));
        return view;
    }
}