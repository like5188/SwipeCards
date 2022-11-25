package com.like.swipecards.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.like.swipecards.*
import com.like.swipecards.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val mBinding by lazy {
        DataBindingUtil.setContentView<ActivityMainBinding>(
            this,
            R.layout.activity_main
        )
    }
    private val myAdapter: MyAdapter by lazy {
        MyAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding
        initView()
        loadData()
    }

    private fun initView() {
        mBinding.swipeCardsAdapterView.isNeedSwipe = true
        mBinding.swipeCardsAdapterView.onSwipeListener = object : OnSwipeListener {
            override fun onLoadData() {
                loadData()
            }

            override fun onScroll(direction: Int, absProgress: Float) {
                Log.v("TAG", "onScroll direction=$direction absProgress=$absProgress")
            }

            override fun onCardExited(direction: Int, dataObject: Any?) {
                myAdapter.remove(0)
                if (direction == DIRECTION_TOP_HALF_LEFT || direction == DIRECTION_BOTTOM_HALF_LEFT) {
                    Toast.makeText(this@MainActivity, "leftExit", Toast.LENGTH_SHORT).show()
                } else if (direction == DIRECTION_TOP_HALF_RIGHT || direction == DIRECTION_BOTTOM_HALF_RIGHT) {
                    Toast.makeText(this@MainActivity, "rightExit", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onClick(v: View?, dataObject: Any?) {
                Toast.makeText(this@MainActivity, "onClick", Toast.LENGTH_SHORT).show()
            }
        }
        mBinding.swipeCardsAdapterView.setAdapter(myAdapter)
        mBinding.swipeCardsAdapterView.setOnItemClickListener { parent, view, position, id ->
            Toast.makeText(this@MainActivity, "setOnItemClickListener", Toast.LENGTH_SHORT).show()
        }
        mBinding.swipeLeft.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeLeft()
        }
        mBinding.swipeRight.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeRight()
        }
    }

    private var count = 0
    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<String>()
            for (i in 0..5) {
                if (count < 10) {
                    list.add(count++.toString())
                }
            }
            withContext(Dispatchers.Main) {
                myAdapter.addAll(list)
            }
        }
    }

}

private class MyAdapter : BaseAdapter() {
    private val list = mutableListOf<String>()
    fun addAll(collection: Collection<String>) {
        if (isEmpty) {
            list.addAll(collection)
            notifyDataSetChanged()
        } else {
            list.addAll(collection)
        }
    }

    fun clear() {
        list.clear()
        notifyDataSetChanged()
    }

    override fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    fun remove(index: Int) {
        if (index > -1 && index < list.size) {
            list.removeAt(index)
            notifyDataSetChanged()
        }
    }

    override fun getCount(): Int {
        return list.size
    }

    override fun getItem(position: Int): String? {
        return try {
            list[position]
        } catch (e: Exception) {
            null
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var cv = convertView
        val holder: ViewHolder
        if (cv == null) {
            cv = LayoutInflater.from(parent.context).inflate(R.layout.item_cardview, parent, false)
            holder = ViewHolder()
            cv.tag = holder
            holder.tv = cv.findViewById(R.id.tv)
        } else {
            holder = cv.tag as ViewHolder
        }
        getItem(position)?.let {
            holder.tv?.setText(it)
        }
        return cv!!
    }

    private class ViewHolder {
        var tv: TextView? = null
    }

}
