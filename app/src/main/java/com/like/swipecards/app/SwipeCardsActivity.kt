package com.like.swipecards.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.like.swipecards.SwipeCardsAdapterView
import com.like.swipecards.app.databinding.ActivitySwipeCardsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwipeCardsActivity : AppCompatActivity() {
    private val mBinding by lazy {
        DataBindingUtil.setContentView<ActivitySwipeCardsBinding>(
            this,
            R.layout.activity_swipe_cards
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
        mBinding.swipeCardsAdapterView.onFlingListener = object : SwipeCardsAdapterView.OnFlingListener {
            override fun removeFirstObjectInAdapter() {
                this@SwipeCardsActivity.myAdapter.remove(0)
            }

            override fun onExitFromLeft(dataObject: Any?) {
                Toast.makeText(this@SwipeCardsActivity, "onExitFromLeft", Toast.LENGTH_SHORT).show()
            }

            override fun onExitFromRight(dataObject: Any?) {
                Toast.makeText(this@SwipeCardsActivity, "onExitFromRight", Toast.LENGTH_SHORT).show()
            }

            override fun onAdapterAboutToEmpty(itemsInAdapter: Int) {
                if (itemsInAdapter == 0) {
                    loadData()
                }
            }

            override fun onScroll(progress: Float) {
                Log.e("TAG", "progress=$progress")
            }
        }
        mBinding.swipeCardsAdapterView.onItemClickListener = object : SwipeCardsAdapterView.OnItemClickListener {
            override fun onItemClick(event: MotionEvent?, v: View?, dataObject: Any?) {
                Toast.makeText(this@SwipeCardsActivity, "onItemClick", Toast.LENGTH_SHORT).show()
            }
        }
        mBinding.swipeCardsAdapterView.setAdapter(myAdapter)
        mBinding.swipeLeft.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeLeft()
        }
        mBinding.swipeRight.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeRight()
        }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Talent>()
            for (i in 0..5) {
                list.add(Talent().apply {
                    headerIcon = headerIcons[i % headerIcons.size]
                })
            }
            withContext(Dispatchers.Main) {
                myAdapter.addAll(list)
            }
        }
    }

    companion object {
        private val headerIcons = intArrayOf(
            R.drawable.i1,
            R.drawable.i2,
            R.drawable.i3,
            R.drawable.i4,
            R.drawable.i5,
            R.drawable.i6
        )
    }

}

private class Talent {
    var headerIcon = 0
}

private class MyAdapter : BaseAdapter() {
    private val list = mutableListOf<Talent>()
    fun addAll(collection: Collection<Talent>) {
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

    override fun getItem(position: Int): Talent? {
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
            holder.portraitView = cv.findViewById(R.id.portrait)
        } else {
            holder = cv.tag as ViewHolder
        }
        getItem(position)?.let {
            holder.portraitView?.setImageResource(it.headerIcon)
        }
        return cv!!
    }

    private class ViewHolder {
        var portraitView: ImageView? = null
    }

}
