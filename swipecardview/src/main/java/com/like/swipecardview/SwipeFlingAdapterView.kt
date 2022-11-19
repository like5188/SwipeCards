package com.like.swipecardview

import android.content.Context
import android.database.DataSetObserver
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Adapter
import android.widget.FrameLayout
import com.like.swipecardview.FlingCardListener.FlingListener

class SwipeFlingAdapterView<T : Adapter> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : BaseFlingAdapterView<T>(context, attrs, defStyle, defStyleRes) {
    private val cacheItems = mutableListOf<View?>()
    private var mAdapter: T? = null
    private var mDataSetObserver: AdapterDataSetObserver? = null
    private var mInLayout = false
    private var mActiveCard: View? = null
    private var flingCardListener: FlingCardListener? = null

    private var lastObjectInStack = 0
    private var initTop = 0
    private var initLeft = 0

    //缩放层叠效果
    var yOffsetStep = 100 // view叠加垂直偏移量的步长
    var scaleStep = 0.08f // view叠加缩放的步长

    var maxVisible = 4 // 值建议最小为4
    var minAdapterStack = 6
    var rotationDegrees = 6f // 旋转角度

    var flingListener: OnFlingListener? = null
    var onItemClickListener: OnItemClickListener? = null

    // 支持左右滑
    var needSwipe: Boolean = true

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
            val topCard = getChildAt(lastObjectInStack)
            if (mActiveCard != null && topCard == mActiveCard) {
                removeAndAddToCache(1)
                layoutChildren(1, adapterCount)
            } else {
                removeAndAddToCache(0)
                layoutChildren(0, adapterCount)
                setTopView()
            }
        }
        mInLayout = false
        if (initTop == 0 && initLeft == 0 && mActiveCard != null) {
            initTop = mActiveCard!!.top
            initLeft = mActiveCard!!.left
        }
        if (adapterCount < minAdapterStack) {
            if (flingListener != null) {
                flingListener!!.onAdapterAboutToEmpty(adapterCount)
            }
        }
    }

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
            if (cacheItems.size > 0) {
                item = cacheItems[0]
                cacheItems.remove(item)
            }
            val newUnderChild = mAdapter!!.getView(index, item, this)
            if (newUnderChild.visibility != GONE) {
                makeAndAddView(newUnderChild, index)
                lastObjectInStack = index
            }
            index++
        }
    }

    private fun makeAndAddView(child: View, index: Int) {
        val lp = child.layoutParams as FrameLayout.LayoutParams
        addViewInLayout(child, 0, lp, true)
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
        if (gravity == -1) {
            gravity = Gravity.TOP or Gravity.START
        }
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val childLeft: Int = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> (width + paddingLeft - paddingRight - w) / 2 +
                    lp.leftMargin - lp.rightMargin
            Gravity.END -> width + paddingRight - w - lp.rightMargin
            Gravity.START -> paddingLeft + lp.leftMargin
            else -> paddingLeft + lp.leftMargin
        }
        val childTop: Int = when (verticalGravity) {
            Gravity.CENTER_VERTICAL -> (height + paddingTop - paddingBottom - h) / 2 +
                    lp.topMargin - lp.bottomMargin
            Gravity.BOTTOM -> height - paddingBottom - h - lp.bottomMargin
            Gravity.TOP -> paddingTop + lp.topMargin
            else -> paddingTop + lp.topMargin
        }
        child.layout(childLeft, childTop, childLeft + w, childTop + h)
        // 缩放层叠效果
        adjustChildView(child, index)
    }

    private fun adjustChildView(child: View, index: Int) {
        if (index > -1 && index < maxVisible) {
            val multiple: Int = if (index > 2) 2 else index
            child.offsetTopAndBottom(yOffsetStep * multiple)
            child.scaleX = 1 - scaleStep * multiple
            child.scaleY = 1 - scaleStep * multiple
        }
    }

    private fun adjustChildrenOfUnderTopView(scrollRate: Float) {
        val count = childCount
        if (count > 1) {
            var i: Int
            var multiple: Int
            if (count == 2) {
                i = lastObjectInStack - 1
                multiple = 1
            } else {
                i = lastObjectInStack - 2
                multiple = 2
            }
            val rate = Math.abs(scrollRate)
            while (i < lastObjectInStack) {
                val underTopView = getChildAt(i)
                val offset = (yOffsetStep * (multiple - rate)).toInt()
                underTopView.offsetTopAndBottom(offset - underTopView.top + initTop)
                underTopView.scaleX = 1 - scaleStep * multiple + scaleStep * rate
                underTopView.scaleY = 1 - scaleStep * multiple + scaleStep * rate
                i++
                multiple--
            }
        }
    }

    /**
     * Set the top view and add the fling listener
     */
    private fun setTopView() {
        if (childCount > 0) {
            mActiveCard = getChildAt(lastObjectInStack)
            if (mActiveCard != null && flingListener != null) {
                flingCardListener = FlingCardListener(mActiveCard, mAdapter!!.getItem(0),
                    rotationDegrees, object : FlingListener {
                        override fun onCardExited() {
                            removeViewInLayout(mActiveCard)
                            mActiveCard = null
                            flingListener!!.removeFirstObjectInAdapter()
                        }

                        override fun leftExit(dataObject: Any) {
                            flingListener!!.onLeftCardExit(dataObject)
                        }

                        override fun rightExit(dataObject: Any) {
                            flingListener!!.onRightCardExit(dataObject)
                        }

                        override fun onClick(event: MotionEvent, v: View, dataObject: Any) {
                            if (onItemClickListener != null) onItemClickListener!!.onItemClicked(event, v, dataObject)
                        }

                        override fun onScroll(progress: Float, scrollXProgress: Float) {
//                                Log.e("Log", "onScroll " + progress);
                            adjustChildrenOfUnderTopView(progress)
                            flingListener!!.onScroll(progress, scrollXProgress)
                        }
                    })
                // 设置是否支持左右滑
                flingCardListener!!.setIsNeedSwipe(needSwipe)
                mActiveCard!!.setOnTouchListener(flingCardListener)
            }
        }
    }

    @Throws(NullPointerException::class)
    fun getTopCardListener(): FlingCardListener {
        if (flingCardListener == null) {
            throw NullPointerException("flingCardListener is null")
        }
        return flingCardListener!!
    }

    /**
     * click to swipe left
     */
    fun swipeLeft() {
        getTopCardListener().selectLeft()
    }

    fun swipeLeft(duration: Int) {
        getTopCardListener().selectLeft(duration.toLong())
    }

    /**
     * click to swipe right
     */
    fun swipeRight() {
        getTopCardListener().selectRight()
    }

    fun swipeRight(duration: Int) {
        getTopCardListener().selectRight(duration.toLong())
    }

    override fun getAdapter(): T? {
        return mAdapter
    }


    override fun setAdapter(adapter: T) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter!!.unregisterDataSetObserver(mDataSetObserver)
            mDataSetObserver = null
        }
        mAdapter = adapter
        if (mAdapter != null && mDataSetObserver == null) {
            mDataSetObserver = AdapterDataSetObserver()
            mAdapter!!.registerDataSetObserver(mDataSetObserver)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams? {
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
