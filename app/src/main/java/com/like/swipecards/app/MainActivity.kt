package com.like.swipecards.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.like.swipecards.*
import com.like.swipecards.app.databinding.ActivityMainBinding
import com.like.swipecards.app.databinding.ItemCardviewBinding
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
        with(mBinding.swipeCardsAdapterView) {
            config = SwipeCardsAdapterView.Config(
                maxChildCount = 5,
                prefetchCount = 5,
                yOffsetStep = 100,
                scaleStep = 0.08f,
                scaleMax = 0.75f,
                animDuration = 3000,
                maxRotationAngle = 20f,
                borderPercent = 0.5f,
                isNeedSwipe = true,
                maxUndoCacheSize = 2,
            )
            onSwipeListener = object : OnSwipeListener {
                override fun onLoadData() {
                    loadData()
                }

                override fun onUndoChange(size: Int) {
                    mBinding.undo.isEnabled = size > 0
                }

                override fun onScroll(direction: Int, absProgress: Float) {
//                Log.v("TAG", "onScroll direction=$direction absProgress=$absProgress")
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
            setAdapter(myAdapter)
        }
        mBinding.swipeLeft.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeLeft()
        }
        mBinding.swipeRight.setOnClickListener {
            mBinding.swipeCardsAdapterView.swipeRight()
        }
        mBinding.clear.setOnClickListener {
            mBinding.swipeCardsAdapterView.clearUndoCache()
            myAdapter.clear()
        }
        mBinding.refresh.setOnClickListener {
            refresh()
        }
        mBinding.undo.isEnabled = false
        mBinding.undo.setOnClickListener {
            mBinding.swipeCardsAdapterView.undo()
        }
        mBinding.tvSetting.setOnClickListener {
            startActivityForResult(
                Intent(this, SettingActivity::class.java).apply {
                    putExtra("config", mBinding.swipeCardsAdapterView.config)
                },
                0
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 0) {
            data?.getParcelableExtra<SwipeCardsAdapterView.Config>("config")?.let {
                mBinding.swipeCardsAdapterView.config = it
                refresh()
            }
        }
    }

    private var page = 1
    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = getData(page++)
            withContext(Dispatchers.Main) {
                myAdapter.addAll(list)
            }
        }
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            page = 1
            val list = getData(page++)
            withContext(Dispatchers.Main) {
                mBinding.swipeCardsAdapterView.clearUndoCache()
                myAdapter.clearAndAddAll(list)
            }
        }
    }

    private suspend fun getData(page: Int = 1, pageSize: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        if (page == 1) {
            for (i in 0 until pageSize) {
                list.add(i.toString())
            }
        } else if (page == 2) {
            for (i in pageSize until pageSize + 5) {
                list.add(i.toString())
            }
        }
        list
    }

}

private class MyAdapter : SwipeCardsAdapterView.Adapter<SwipeCardsAdapterView.ViewHolder<ItemCardviewBinding>>() {
    private val list = mutableListOf<String>()
    private val multiViewType = true

    fun print() {
        list.forEachIndexed { index, s ->
            Log.v("TAG", "index=$index item=$s")
        }
    }

    fun addAll(collection: Collection<String>) {
        list.addAll(collection)
        notifyDataSetChanged()
    }

    fun clear() {
        list.clear()
        notifyDataSetChanged()
    }

    fun clearAndAddAll(collection: Collection<String>) {
        list.clear()
        list.addAll(collection)
        notifyDataSetInvalidated()
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

    override fun getItemViewType(position: Int): Int {
        return if (multiViewType) {
            if (list[position].toInt() < 3) 100 else 200
        } else {
            0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwipeCardsAdapterView.ViewHolder<ItemCardviewBinding> {
        val binding = DataBindingUtil.inflate<ItemCardviewBinding>(
            LayoutInflater.from(parent.context),
            R.layout.item_cardview,
            parent,
            false
        )
        return SwipeCardsAdapterView.ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SwipeCardsAdapterView.ViewHolder<ItemCardviewBinding>, data: Any?) {
        val item = data as? String ?: return
        holder.binding.tv.text = item
        if (multiViewType) {
            if (item.toInt() < 3) {
                holder.binding.tv.setTextColor(Color.RED)
            } else {
                holder.binding.tv.setTextColor(Color.BLUE)
            }
        } else {
            holder.binding.tv.setTextColor(Color.BLUE)
        }
        holder.binding.tvViewType.apply {
            val position = list.indexOf(item)
            val viewType = getItemViewType(position)
            text = "viewType：$viewType"
            setOnClickListener {
                Toast.makeText(context, "$viewType", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onUndo(data: Any?) {
        print()
        Log.v("TAG", "--------------------------")
        list.add(0, data as String)
        print()
    }

}
