package com.like.swipecards.app;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.like.swipecards.SwipeCardsAdapterView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SwipeCardsActivity extends AppCompatActivity implements SwipeCardsAdapterView.OnFlingListener,
        SwipeCardsAdapterView.OnItemClickListener, View.OnClickListener {

    int[] headerIcons = {
            R.drawable.i1,
            R.drawable.i2,
            R.drawable.i3,
            R.drawable.i4,
            R.drawable.i5,
            R.drawable.i6
    };

    private SwipeCardsAdapterView swipeCardsAdapterView;
    private InnerAdapter adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipe_cards);

        initView();
        loadData();
    }

    private void initView() {
        swipeCardsAdapterView = findViewById(R.id.swipeCardsAdapterView);
        if (swipeCardsAdapterView != null) {
            swipeCardsAdapterView.setNeedSwipe(true);
            swipeCardsAdapterView.setOnFlingListener(this);
            swipeCardsAdapterView.setOnItemClickListener(this);

            adapter = new InnerAdapter();
            swipeCardsAdapterView.setAdapter(adapter);
        }

        View v = findViewById(R.id.swipeLeft);
        if (v != null) {
            v.setOnClickListener(this);
        }
        v = findViewById(R.id.swipeRight);
        if (v != null) {
            v.setOnClickListener(this);
        }

    }


    @Override
    public void onItemClick(MotionEvent event, View v, Object dataObject) {
        Toast.makeText(this, "onItemClick", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void removeFirstObjectInAdapter() {
        adapter.remove(0);
    }

    @Override
    public void onExitFromLeft(Object dataObject) {
        Toast.makeText(this, "onExitFromLeft", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onExitFromRight(Object dataObject) {
        Toast.makeText(this, "onExitFromRight", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAdapterAboutToEmpty(int itemsInAdapter) {
        if (itemsInAdapter == 0) {
            loadData();
        }
    }

    @Override
    public void onScroll(float progress) {
        Log.e("TAG", "progress=" + progress);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.swipeLeft:
                swipeCardsAdapterView.swipeLeft();
                //swipeView.swipeLeft(250);
                break;
            case R.id.swipeRight:
                swipeCardsAdapterView.swipeRight();
                //swipeView.swipeRight(250);
        }
    }

    private void loadData() {
        new AsyncTask<Void, Void, List<Talent>>() {
            @Override
            protected List<Talent> doInBackground(Void... params) {
                ArrayList<Talent> list = new ArrayList<>(10);
                Talent talent;
                for (int i = 0; i < 6; i++) {
                    talent = new Talent();
                    talent.headerIcon = headerIcons[i % headerIcons.length];
                    list.add(talent);
                }
                return list;
            }

            @Override
            protected void onPostExecute(List<Talent> list) {
                super.onPostExecute(list);
                adapter.addAll(list);
            }
        }.execute();
    }


    private class InnerAdapter extends BaseAdapter {

        ArrayList<Talent> objs;

        public InnerAdapter() {
            objs = new ArrayList<>();
        }

        public void addAll(Collection<Talent> collection) {
            if (isEmpty()) {
                objs.addAll(collection);
                notifyDataSetChanged();
            } else {
                objs.addAll(collection);
            }
        }

        public void clear() {
            objs.clear();
            notifyDataSetChanged();
        }

        public boolean isEmpty() {
            return objs.isEmpty();
        }

        public void remove(int index) {
            if (index > -1 && index < objs.size()) {
                objs.remove(index);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return objs.size();
        }

        @Override
        public Talent getItem(int position) {
            if (objs == null || objs.size() == 0) return null;
            return objs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        // TODO: getView
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            Talent talent = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cardview, parent, false);
                holder = new ViewHolder();
                convertView.setTag(holder);
                holder.portraitView = convertView.findViewById(R.id.portrait);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.portraitView.setImageResource(talent.headerIcon);
            return convertView;
        }

    }

    private static class ViewHolder {
        ImageView portraitView;
    }

    public static class Talent {
        public int headerIcon;
        public String nickname;
        public String cityName;
        public String educationName;
        public String workYearName;
    }

}
