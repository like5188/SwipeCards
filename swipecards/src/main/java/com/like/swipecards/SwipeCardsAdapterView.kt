package com.like.swipecards

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
import com.like.swipecards.OnCardViewTouchListener.FlingListener

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
    private val adapterDataSetObserver: AdapterDataSetObserver by lazy {
        AdapterDataSetObserver()
    }
    private var inLayout = false
    private var topView: View? = null
    private var topViewIndex = 0
    private var initTop = 0
    private var initLeft = 0

    private var onCardViewTouchListener: OnCardViewTouchListener? = null

    //缩放层叠效果
    var yOffsetStep = 100 // view叠加垂直偏移量的步长
    var scaleStep = 0.08f // view叠加缩放的步长

    var maxVisible = 4 // 值建议最小为4
    var minAdapterStack = 6
    var rotationDegrees = 20f // 最大旋转角度
    var isNeedSwipe: Boolean = true // 是否支持滑动

    var onFlingListener: OnFlingListener? = null
    var onItemClickListener: OnItemClickListener? = null

    override fun getSelectedView(): View? {
        return topView
    }

    override fun requestLayout() {
        if (!inLayout) {
            super.requestLayout()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val adapter = mAdapter ?: return
        inLayout = true
        val adapterCount = adapter.count
        if (adapterCount == 0) {
            removeAndAddToCache(0)
        } else {
            val topView = getChildAt(topViewIndex)
            if (this.topView != null && topView == this.topView) {
                removeAndAddToCache(1)
                layoutChildren(1, adapterCount)
            } else {
                removeAndAddToCache(0)
                layoutChildren(0, adapterCount)
                setTopView()
            }
        }
        inLayout = false
        if (initTop == 0 && initLeft == 0) {
            topView?.apply {
                initTop = this.top
                initLeft = this.left
            }
        }
        if (adapterCount < minAdapterStack) {// 通知添加数据
            onFlingListener?.onAdapterAboutToEmpty(adapterCount)
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

    private fun layoutChildren(startIndex: Int, adapterCount: Int) {
        var index = startIndex
        while (index < Math.min(adapterCount, maxVisible)) {
            var view: View? = null
            if (viewCaches.isNotEmpty()) {
                view = viewCaches.removeAt(0)
            }
            mAdapter?.getView(index, view, this)?.let {
                if (it.visibility != GONE) {
                    addChild(it, index)
                    topViewIndex = index
                }
            }
            index++
        }
    }

    private fun addChild(child: View, index: Int) {
        val lp = child.layoutParams as? FrameLayout.LayoutParams ?: return
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

    /**
     * Set the top view and add the fling listener
     */
    private fun setTopView() {
        topView = getChildAt(topViewIndex)?.also { view ->
            onCardViewTouchListener = OnCardViewTouchListener(view, mAdapter?.getItem(0), rotationDegrees, object : FlingListener {
                override fun onCardExited() {
                    removeViewInLayout(view)
                    topView = null
                    onFlingListener?.removeFirstObjectInAdapter()
                }

                override fun leftExit(dataObject: Any?) {
                    onFlingListener?.onExitFromLeft(dataObject)
                }

                override fun rightExit(dataObject: Any?) {
                    onFlingListener?.onExitFromRight(dataObject)
                }

                override fun onClick(event: MotionEvent?, v: View?, dataObject: Any?) {
                    onItemClickListener?.onItemClick(event, v, dataObject)
                }

                override fun onScroll(direction: Int, absProgress: Float) {
                    adjustChildrenUnderTopView(absProgress)
                    onFlingListener?.onScroll(direction, absProgress)
                }

            }).also {
                // 设置是否支持左右滑
                it.isNeedSwipe = isNeedSwipe
                view.setOnTouchListener(it)
            }
        }
    }

    /**
     * 调整 TopView 之下的所有视图的缩放和垂直位移
     */
    private fun adjustChildrenUnderTopView(scale: Float) {
        Log.d("TAG", "scale=$scale")
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
        while (index < topViewIndex) {
            val yOffset = (yOffsetStep * (level - scale)).toInt()
            val view = getChildAt(index)
            view.offsetTopAndBottom(yOffset - view.top + initTop)
            view.scaleX = 1 - scaleStep * level + scaleStep * scale
            view.scaleY = 1 - scaleStep * level + scaleStep * scale
            index++
            level--
        }
    }

    /**
     * click to swipe left
     */
    fun swipeLeft() {
        onCardViewTouchListener?.exitFromLeft()
    }

    fun swipeLeft(duration: Int) {
        onCardViewTouchListener?.exitFromLeft(duration.toLong())
    }

    /**
     * click to swipe right
     */
    fun swipeRight() {
        onCardViewTouchListener?.exitFromRight()
    }

    fun swipeRight(duration: Int) {
        onCardViewTouchListener?.exitFromRight(duration.toLong())
    }

    override fun getAdapter(): T? {
        return mAdapter
    }

    override fun setAdapter(adapter: T) {
        mAdapter?.unregisterDataSetObserver(adapterDataSetObserver)
        mAdapter = adapter
        mAdapter?.registerDataSetObserver(adapterDataSetObserver)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return FrameLayout.LayoutParams(context, attrs)
    }

    private inner class AdapterDataSetObserver : DataSetObserver() {
        override fun onChanged() {
            requestLayout()
        }

        override fun onInvalidated() {
            requestLayout()
        }
    }

    interface OnItemClickListener {
        fun onItemClick(event: MotionEvent?, v: View?, dataObject: Any?)
    }

    interface OnFlingListener {
        fun removeFirstObjectInAdapter()
        fun onExitFromLeft(dataObject: Any?)
        fun onExitFromRight(dataObject: Any?)
        fun onAdapterAboutToEmpty(itemsInAdapter: Int)
        fun onScroll(direction: Int, absProgress: Float)
    }

}
