package com.like.swipecardview

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
import com.like.swipecardview.FlingCardListener.FlingListener

class SwipeFlingAdapterView<T : Adapter> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : BaseFlingAdapterView<T>(context, attrs, defStyle, defStyleRes) {
    private val cacheItems = mutableListOf<View?>()
    private var mAdapter: T? = null
    private val mDataSetObserver: AdapterDataSetObserver by lazy {
        AdapterDataSetObserver()
    }
    private var mInLayout = false
    private var mActiveCard: View? = null

    private var topViewIndex = 0
    private var initTop = 0
    private var initLeft = 0

    private var flingCardListener: FlingCardListener? = null

    //缩放层叠效果
    var yOffsetStep = 100 // view叠加垂直偏移量的步长
    var scaleStep = 0.08f // view叠加缩放的步长

    var maxVisible = 4 // 值建议最小为4
    var minAdapterStack = 6
    var rotationDegrees = 6f // 旋转角度
    var isNeedSwipe: Boolean = true // 支持左右滑

    var flingListener: OnFlingListener? = null
    var onItemClickListener: OnItemClickListener? = null

    override fun getSelectedView(): View? {
        return mActiveCard
    }

    override fun requestLayout() {
        if (!mInLayout) {
            super.requestLayout()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val adapter = mAdapter ?: return
        mInLayout = true
        val adapterCount = adapter.count
        if (adapterCount == 0) {
            removeAndAddToCache(0)
        } else {
            val topView = getChildAt(topViewIndex)
            if (mActiveCard != null && topView == mActiveCard) {
                removeAndAddToCache(1)
                layoutChildren(1, adapterCount)
            } else {
                removeAndAddToCache(0)
                layoutChildren(0, adapterCount)
                setTopView()
            }
        }
        mInLayout = false
        if (initTop == 0 && initLeft == 0) {
            mActiveCard?.apply {
                initTop = this.top
                initLeft = this.left
            }
        }
        if (adapterCount < minAdapterStack) {// 通知添加数据
            flingListener?.onAdapterAboutToEmpty(adapterCount)
        }
    }

    /**
     * 保留指定数量的视图，其它的移除并存入缓存中
     */
    private fun removeAndAddToCache(remain: Int) {
        while (childCount - remain > 0) {
            getChildAt(0)?.apply {
                removeViewInLayout(this)
                cacheItems.add(this)
            }
        }
    }

    private fun layoutChildren(startingIndex: Int, adapterCount: Int) {
        var index = startingIndex
        while (index < Math.min(adapterCount, maxVisible)) {
            var item: View? = null
            if (cacheItems.isNotEmpty()) {
                item = cacheItems.removeAt(0)
            }
            mAdapter?.getView(index, item, this)?.let {
                if (it.visibility != GONE) {
                    makeAndAddView(it, index)
                    topViewIndex = index
                }
            }
            index++
        }
    }

    private fun makeAndAddView(child: View, index: Int) {
        val lp = child.layoutParams as? FrameLayout.LayoutParams ?: return
        addViewInLayout(child, 0, lp, true)
        layoutChild(child, lp)
        // 缩放层叠效果
        adjustChildView(child, index)
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
    private fun adjustChildView(child: View, index: Int) {
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
        mActiveCard = getChildAt(topViewIndex)?.also { view ->
            flingCardListener = FlingCardListener(view, mAdapter?.getItem(0), rotationDegrees, object : FlingListener {
                override fun onCardExited() {
                    removeViewInLayout(view)
                    mActiveCard = null
                    flingListener?.removeFirstObjectInAdapter()
                }

                override fun leftExit(dataObject: Any) {
                    flingListener?.onLeftCardExit(dataObject)
                }

                override fun rightExit(dataObject: Any) {
                    flingListener?.onRightCardExit(dataObject)
                }

                override fun onClick(event: MotionEvent, v: View, dataObject: Any) {
                    if (onItemClickListener != null) onItemClickListener!!.onItemClicked(event, v, dataObject)
                }

                override fun onScroll(progress: Float, scrollXProgress: Float) {
                    adjustChildrenOfUnderTopView(progress)
                    flingListener?.onScroll(progress, scrollXProgress)
                }
            }).apply {
                // 设置是否支持左右滑
                setIsNeedSwipe(isNeedSwipe)
                view.setOnTouchListener(this)
            }
        }
    }

    private fun adjustChildrenOfUnderTopView(scrollRate: Float) {
        val count = childCount
        if (count <= 1) {
            return
        }
        var indexOfUnderTopView: Int
        var level: Int
        if (count == 2) {
            indexOfUnderTopView = topViewIndex - 1
            level = 1
        } else {
            indexOfUnderTopView = topViewIndex - 2
            level = 2
        }
        val rate = Math.abs(scrollRate)
        while (indexOfUnderTopView < topViewIndex) {
            val underTopView = getChildAt(indexOfUnderTopView)
            val yOffset = (yOffsetStep * (level - rate)).toInt()
            underTopView.offsetTopAndBottom(yOffset - underTopView.top + initTop)
            underTopView.scaleX = 1 - scaleStep * level + scaleStep * rate
            underTopView.scaleY = 1 - scaleStep * level + scaleStep * rate
            indexOfUnderTopView++
            level--
        }
    }

    /**
     * click to swipe left
     */
    fun swipeLeft() {
        flingCardListener?.selectLeft()
    }

    fun swipeLeft(duration: Int) {
        flingCardListener?.selectLeft(duration.toLong())
    }

    /**
     * click to swipe right
     */
    fun swipeRight() {
        flingCardListener?.selectRight()
    }

    fun swipeRight(duration: Int) {
        flingCardListener?.selectRight(duration.toLong())
    }

    override fun getAdapter(): T? {
        return mAdapter
    }

    override fun setAdapter(adapter: T) {
        mAdapter?.unregisterDataSetObserver(mDataSetObserver)
        mAdapter = adapter
        mAdapter?.registerDataSetObserver(mDataSetObserver)
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
        fun onItemClicked(event: MotionEvent?, v: View?, dataObject: Any?)
    }

    interface OnFlingListener {
        fun removeFirstObjectInAdapter()
        fun onLeftCardExit(dataObject: Any?)
        fun onRightCardExit(dataObject: Any?)
        fun onAdapterAboutToEmpty(itemsInAdapter: Int)
        fun onScroll(progress: Float, scrollXProgress: Float)
    }

}
