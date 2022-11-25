package com.like.swipecards

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY

/**
 * 滑动卡片集合视图
 */
class SwipeCardsAdapterView<T : Adapter> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : BaseAdapterView<T>(context, attrs, defStyle, defStyleRes) {
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
    private var inLayout = false
    private var topView: View? = null
    private var topViewIndex = 0

    // 记录 TopView 原始位置的left、top
    private var originTopViewLeft = 0
    private var originTopViewTop = 0

    private var onCardViewTouchListener: OnCardViewTouchListener? = null

    //缩放层叠效果
    var yOffsetStep = 100 // view叠加垂直偏移量的步长
    var scaleStep = 0.08f // view叠加缩放的步长
    var scaleMax = 0.5f // 当滑动进度为0.5时，缩放到最大。[0f,1f]

    private var maxVisible = 4 // 值建议最小为4，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。
    var prefetchCount = 5// 预取数量，当数量小于此值时，触发加载数据的操作。建议为 maxVisible + 1，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。
    var rotationDegrees = 20f // 最大旋转角度
    var isNeedSwipe: Boolean = true // 是否支持滑动

    var onSwipeListener: OnSwipeListener? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val adapterCount = mAdapter?.count ?: return

        inLayout = true
        if (adapterCount == 0) {
            removeAndAddToCache(0)
        } else if (topView != null && topView == getChildAt(topViewIndex)) {// 如果 topView 存在，还没有飞出屏幕
            removeAndAddToCache(1)
            addChildren(1, adapterCount)
        } else {// 如果 topView 不存在
            removeAndAddToCache(0)
            addChildren(0, adapterCount)
            topView = getChildAt(topViewIndex)
            setOnCardViewTouchListener()
        }
        inLayout = false

        if (originTopViewLeft == 0 && originTopViewTop == 0 && topView != null) {
            originTopViewTop = topView?.top ?: 0
            originTopViewLeft = topView?.left ?: 0
        }
        // 通知加载数据
        if (adapterCount < prefetchCount) {
            onSwipeListener?.onLoadData()
        }
    }

    /**
     * 保留指定数量的视图，其它的移除并存入缓存中
     */
    private fun removeAndAddToCache(remain: Int) {
        while (childCount - remain > 0) {
            getChildAt(0)?.apply {
                removeViewInLayout(this)
                viewCaches.add(this)
            }
        }
    }

    private fun addChildren(startIndex: Int, adapterCount: Int) {
        var position = startIndex
        while (position < Math.min(adapterCount, maxVisible)) {
            var convertView: View? = null
            if (viewCaches.isNotEmpty()) {
                convertView = viewCaches.removeAt(0)
            }
            mAdapter?.getView(position, convertView, this)?.let {
                if (it.visibility != GONE) {
                    addChild(it, position)
                    topViewIndex = position
                }
            }
            position++
        }
    }

    private fun addChild(child: View, index: Int) {
        val lp = child.layoutParams as? FrameLayout.LayoutParams ?: return
        // 添加child，并且不触发layout请求
        addViewInLayout(child, 0, lp, true)
        layoutChild(child, lp)
        // 缩放层叠效果
        adjustChild(child, index)
    }

    private fun layoutChild(child: View, lp: FrameLayout.LayoutParams) {
        val needToMeasure = child.isLayoutRequested
        if (needToMeasure) {
            val childWidthSpec = getChildMeasureSpec(
                widthMeasureSpec,
                paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                lp.width
            )
            val childHeightSpec = getChildMeasureSpec(
                heightMeasureSpec,
                paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
                lp.height
            )
            child.measure(childWidthSpec, childHeightSpec)
        } else {
            cleanupLayoutState(child)
        }
        val w = child.measuredWidth
        val h = child.measuredHeight
        var gravity = lp.gravity
        if (gravity == UNSPECIFIED_GRAVITY) {
            gravity = Gravity.TOP or Gravity.START
        }
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val childLeft: Int = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> (width + paddingLeft - paddingRight - w) / 2 + lp.leftMargin - lp.rightMargin
            Gravity.END -> width + paddingRight - w - lp.rightMargin
            Gravity.START -> paddingLeft + lp.leftMargin
            else -> paddingLeft + lp.leftMargin
        }
        val childTop: Int = when (verticalGravity) {
            Gravity.CENTER_VERTICAL -> (height + paddingTop - paddingBottom - h) / 2 + lp.topMargin - lp.bottomMargin
            Gravity.BOTTOM -> height - paddingBottom - h - lp.bottomMargin
            Gravity.TOP -> paddingTop + lp.topMargin
            else -> paddingTop + lp.topMargin
        }
        child.layout(childLeft, childTop, childLeft + w, childTop + h)
    }

    /**
     * 调整视图垂直位置及缩放系数，达到缩放层叠效果
     */
    private fun adjustChild(child: View, index: Int) {
        if (index > -1 && index < maxVisible) {
            val level = if (index > 2) 2 else index
            child.offsetTopAndBottom(yOffsetStep * level)
            child.scaleX = 1 - scaleStep * level
            child.scaleY = 1 - scaleStep * level
        }
    }

    private fun setOnCardViewTouchListener() {
        val view = topView ?: return
        onCardViewTouchListener = OnCardViewTouchListener(view, mAdapter?.getItem(0), rotationDegrees, object : OnSwipeListener {
            override fun onCardExited(direction: Int, dataObject: Any?) {
                removeViewInLayout(view)
                topView = null
                onSwipeListener?.onCardExited(direction, dataObject)
            }

            override fun onClick(v: View?, dataObject: Any?) {
                onSwipeListener?.onClick(v, dataObject)
            }

            override fun onScroll(direction: Int, absProgress: Float) {
                var scale = absProgress / scaleMax// 修正系数
                if (scale > 1f) {
                    scale = 1f
                }
                adjustChildrenUnderTopView(scale)
                onSwipeListener?.onScroll(direction, absProgress)
            }

            override fun onLoadData() {
            }

        }).also {
            // 设置是否支持左右滑
            it.isNeedSwipe = isNeedSwipe
            view.setOnTouchListener(it)
        }
    }

    /**
     * 调整 TopView 之下的所有视图的缩放和垂直位移
     */
    private fun adjustChildrenUnderTopView(scale: Float) {
        val count = childCount
        if (count <= 1) {
            return
        }
        var index: Int
        var level: Int
        if (count == 2) {
            index = topViewIndex - 1
            level = 1
        } else {
            index = topViewIndex - 2
            level = 2
        }
        val absScale = Math.abs(scale)
        while (index < topViewIndex) {
            val yOffset = (yOffsetStep * (level - absScale)).toInt()
            val view = getChildAt(index)
            view.offsetTopAndBottom(yOffset - view.top + originTopViewTop)
            view.scaleX = 1 - scaleStep * level + scaleStep * absScale
            view.scaleY = 1 - scaleStep * level + scaleStep * absScale
            index++
            level--
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

    override fun getAdapter(): T? {
        return mAdapter
    }

    override fun setAdapter(adapter: T) {
        mAdapter?.unregisterDataSetObserver(dataSetObserver)
        mAdapter = adapter
        mAdapter?.registerDataSetObserver(dataSetObserver)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return FrameLayout.LayoutParams(context, attrs)
    }

    override fun getSelectedView(): View? {
        return topView
    }

    override fun requestLayout() {
        if (!inLayout) {
            super.requestLayout()
        }
    }

}
