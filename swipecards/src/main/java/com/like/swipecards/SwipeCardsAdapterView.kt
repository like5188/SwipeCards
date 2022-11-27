package com.like.swipecards

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/*
ViewGroup的职能为：给childView计算出建议的宽和高和测量模式 ；决定childView的位置；为什么只是建议的宽和高，而不是直接确定呢，别忘了childView宽和高可以设置为wrap_content，这样只有childView才能计算出自己的宽和高。
View的职责，根据测量模式和ViewGroup给出的建议的宽和高，计算出自己的宽和高；同时还有个更重要的职责是：在ViewGroup为其指定的区域内绘制自己的形态。
 */
/**
 * 滑动卡片集合视图
 */
class SwipeCardsAdapterView<T : Adapter> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyle, defStyleRes) {
    private val viewCaches = mutableListOf<View?>()
    private var mAdapter: T? = null
    private val dataSetObserver: DataSetObserver by lazy {
        object : DataSetObserver() {
            override fun onChanged() {
                requestLayout()
            }

            override fun onInvalidated() {
                requestLayout()
            }
        }
    }

    /**
     * 视觉看到的"最外层"的那个视图。
     */
    private var topView: View? = null
        set(value) {
            if (originTopViewLeft == 0 && originTopViewTop == 0 && value != null) {
                originTopViewTop = value.top
                originTopViewLeft = value.left
            }
            field = value
        }

    /**
     * topView 的索引。
     * 注意：最底层（屏幕最深处）的索引是0。
     */
    private val topViewIndex: Int
        get() = Math.min(mAdapter?.count ?: 0, maxCount) - 1

    // 记录 TopView 原始位置的left、top
    private var originTopViewLeft = 0
    private var originTopViewTop = 0

    private var onCardViewTouchListener: OnCardViewTouchListener? = null

    /**
     * 存在屏幕中的最大 cardView 数量。包括"最底层那一个被遮住的视图"。
     */
    var maxCount = 5

    /**
     * 预取数量。
     * 当数量等于此值时，触发加载数据的操作。建议 >=[maxCount]，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。
     */
    var prefetchCount = 5

    /**
     * 缩放层叠时的垂直偏移量步长
     */
    var yOffsetStep = 100

    /**
     *缩放层叠时的缩放步长
     */
    var scaleStep = 0.08f

    /**
     * 当滑动进度为0.5时，缩放到最大。[0f,1f]
     */
    var scaleMax = 0.5f

    /**
     * 最大旋转角度。
     * 比如左滑时：就是滑动使得右上角的点滑动到原始左上角点的位置的角度
     */
    var rotationDegrees = 20f

    /**
     * 是否支持滑动
     */
    var isNeedSwipe: Boolean = true

    var onSwipeListener: OnSwipeListener? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        makeAndAddView()
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0) return
        Log.w("TAG", "measureChildren")
        measureChildren(widthMeasureSpec, heightMeasureSpec)
    }

    private fun makeAndAddView() {
        val adapterCount = mAdapter?.count ?: 0
        if (adapterCount == 0) {
            if (childCount > 0) {// 清除adapter中的所有数据时触发
                Log.w("TAG", "1 adapterCount=$adapterCount childCount=$childCount topViewIndex=$topViewIndex")
                removeAndAddToCache(0)
            }
        } else if (topView == null) {
            if (childCount == 0) {// 初始化时触发
                Log.w("TAG", "2 adapterCount=$adapterCount childCount=$childCount topViewIndex=$topViewIndex")
                addChildren(0)
            }
        } else {
            if (childCount <= topViewIndex) {// 删除topView后触发
                Log.w("TAG", "3 adapterCount=$adapterCount childCount=$childCount topViewIndex=$topViewIndex")
                addChildren(childCount)
            }
        }
    }

    /**
     * 从最底层开始移除视图并保存到缓存中，需要保留指定数量的视图。
     */
    private fun removeAndAddToCache(remain: Int) {
        while (childCount - remain > 0) {
            getChildAt(0)?.apply {
                removeViewInLayout(this)
                viewCaches.add(this)
            }
        }
    }

    /**
     * @param startIndex    添加数据的开始索引。注意：这个索引是数据集合中的位置，和屏幕视图的索引是相反的。
     */
    private fun addChildren(startIndex: Int) {
        (startIndex..topViewIndex).forEach { index ->
            var convertView: View? = null
//            if (viewCaches.isNotEmpty()) {
//                convertView = viewCaches.removeAt(0)
//            }
            mAdapter?.getView(index, convertView, this)?.let {
                Log.d("TAG", "addView=${(it as? TextView)?.text} position=$index topViewIndex=$topViewIndex ${it == convertView}")
                // 添加child，并且不触发requestLayout()方法，性能比addView更好。index为0代表往屏幕最底层插入。
                addViewInLayout(it, 0, it.layoutParams, true)
            }
        }
    }

    // 此处重写此方法，而不使用FrameLayout自带的测量方法，是因为它把margin也计算到了child的宽中，这样会导致滑动判断边界的时候更麻烦。
    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParams as LayoutParams
        val childWidthSpec = getChildMeasureSpec(
            parentWidthMeasureSpec,
            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
            lp.width
        )
        val childHeightSpec = getChildMeasureSpec(
            parentHeightMeasureSpec,
            paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
            lp.height
        )
        child.measure(childWidthSpec, childHeightSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (childCount == 0) return
        super.onLayout(changed, left, top, right, bottom)
        adjustChildren()
        resetTopView()
    }

    // 设置OnCardViewTouchListener监听必须在layout完成后，否则OnCardViewTouchListener中获取不到cardView的相关参数。
    private fun resetTopView() {
        Log.e(
            "TAG",
            "onLayout-resetTopView adapterCount=${mAdapter?.count} childCount=$childCount topViewIndex=$topViewIndex topView=${
                (getChildAt(
                    topViewIndex
                ) as? TextView)?.text
            }"
        )
        onCardViewTouchListener = null
        topView = getChildAt(topViewIndex)?.apply {
            // 设置OnCardViewTouchListener监听必须在layout完成后，否则OnCardViewTouchListener中获取不到cardView的相关参数。
            onCardViewTouchListener = OnCardViewTouchListener(this, mAdapter?.getItem(0), rotationDegrees,
                object : OnSwipeListener {
                    override fun onCardExited(direction: Int, dataObject: Any?) {
                        removeViewInLayout(topView)
                        viewCaches.add(topView)
                        onSwipeListener?.onCardExited(direction, dataObject)
                        // 通知加载数据
                        if ((mAdapter?.count ?: 0) == prefetchCount) {
                            onSwipeListener?.onLoadData()
                        }
                    }

                    override fun onClick(v: View?, dataObject: Any?) {
                        onSwipeListener?.onClick(v, dataObject)
                    }

                    override fun onScroll(direction: Int, absProgress: Float) {
                        var rate = absProgress / scaleMax// 修正系数
                        if (rate > 1f) {
                            rate = 1f
                        }
                        adjustChildren(rate, false)
                        onSwipeListener?.onScroll(direction, absProgress)
                    }

                    override fun onLoadData() {
                    }

                }
            ).also {
                // 设置是否支持左右滑
                it.isNeedSwipe = isNeedSwipe
                this.setOnTouchListener(it)
            }
        }
    }

    /**
     * 调整视图达到缩放层叠效果
     * 注意：
     * 1、如果是初始化，那么就调整TopView 之下的所有视图。
     * 2、如果不是初始化，那么就调整TopView 之下的所有"可见"视图。"可见"是指最底层那一个被遮住的视图不需要处理，因为它不需要缩放和位移。它只是在其上层的视图缩放或者位移时显现出来而已。
     * @param rate      滑动进度
     * @param isInit    是否初始化
     */
    private fun adjustChildren(rate: Float = 0f, isInit: Boolean = true) {
        val absRate = abs(rate)
        // index代表可见的最底层视图的索引。所有视图的最底层的视图为0。topViewIndex-1代表TopView 之下
        for (index in (topViewIndex - 1 downTo 0)) {
            // 如果不是初始化，并且"最底层那一个被遮住的视图"存在
            if (!isInit && childCount >= maxCount) {
                if (index == 0) {// 排除"最底层那一个被遮住的视图"
                    return
                }
            }
            // 层级。最外层（topView）的层级为 0，向底层递增
            var level = topViewIndex - index
            if (level > maxCount - 2) {
                level = maxCount - 2
            }
            // 进行缩放和垂直平移
            getChildAt(index)?.apply {
                val yOffset = (yOffsetStep * (level - absRate)).toInt()
                val curYOffset = if (isInit) 0 else top - originTopViewTop
                offsetTopAndBottom(yOffset - curYOffset)
                val scale = 1 - scaleStep * level + scaleStep * absRate
                scaleX = scale
                scaleY = scale
            }
        }
    }

    /**
     * 单击触发往左滑出
     */
    fun swipeLeft() {
        onCardViewTouchListener?.swipeLeft()
    }

    /**
     * 单击触发往右滑出
     */
    fun swipeRight() {
        onCardViewTouchListener?.swipeRight()
    }

    fun setAdapter(adapter: T) {
        mAdapter?.unregisterDataSetObserver(dataSetObserver)
        mAdapter = adapter
        mAdapter?.registerDataSetObserver(dataSetObserver)
    }

}
