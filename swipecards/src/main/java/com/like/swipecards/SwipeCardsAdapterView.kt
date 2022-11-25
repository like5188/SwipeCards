package com.like.swipecards

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout

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
     * 视觉看到的"最外层"的那个视图的索引。
     * 注意：AdapterView中，最底层（屏幕最深处）的索引是0。这会影响到相关的添加视图[addViewInLayout]、获取视图[getChildAt]等方法。
     */
    private val topViewIndex: Int
        get() = Math.min(mAdapter?.count ?: 0, maxVisible) - 1

    // 记录 TopView 原始位置的left、top
    private var originTopViewLeft = 0
    private var originTopViewTop = 0

    private var onCardViewTouchListener: OnCardViewTouchListener? = null
    private val maxVisible = 4 // 值建议最小为4，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。

    //缩放层叠效果
    /**
     * view叠加垂直偏移量的步长
     */
    var yOffsetStep = 100

    /**
     * view叠加缩放的步长
     */
    var scaleStep = 0.08f

    /**
     * 当滑动进度为0.5时，缩放到最大。[0f,1f]
     */
    var scaleMax = 0.5f

    /**
     * 预取数量。
     * 当数量小于此值时，触发加载数据的操作。建议为 maxVisible + 1，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。
     */
    var prefetchCount = 5

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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val adapterCount = mAdapter?.count ?: return

        inLayout = true
        if (adapterCount == 0) {
            removeAndAddToCache(0)
        } else if (topView != null) {// 如果 topView 存在
            removeAndAddToCache(1)
            addChildren(1, adapterCount)
        } else {// 如果 topView 不存在
            removeAndAddToCache(0)
            addChildren(0, adapterCount)
            topView = getChildAt(topViewIndex)
            setOnCardViewTouchListener()
        }
        inLayout = false

        // 通知加载数据
        if (adapterCount < prefetchCount) {
            onSwipeListener?.onLoadData()
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
                }
            }
            position++
        }
    }

    private fun addChild(child: View, index: Int) {
        val lp = child.layoutParams as? FrameLayout.LayoutParams ?: return
        // 添加child，并且不触发requestLayout()方法，性能比addView更好
        addViewInLayout(child, 0, lp, true)
        // 布局child
        layoutChild(child, lp)
        // 缩放层叠效果
        adjustChild(child, index)
    }

    private fun layoutChild(child: View, lp: FrameLayout.LayoutParams) {
        // 测量child。参考ListView
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

        // 计算child的位置参数。参考FrameLayout
        val w = child.measuredWidth
        val h = child.measuredHeight
        var gravity = lp.gravity
        if (gravity == -1) {
            gravity = Gravity.TOP or Gravity.START
        }
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val childLeft: Int = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> (width + paddingLeft - paddingRight - w) / 2 + lp.leftMargin - lp.rightMargin
            Gravity.END -> width - paddingRight - w - lp.rightMargin
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
            val level = if (index > 2) 2 else index// 大于2的层级都叠放在一起。
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
