package com.like.swipecardview

import android.annotation.TargetApi
import android.content.Context
import android.database.DataSetObserver
import android.os.Build
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
) :
    BaseFlingAdapterView<T>(context, attrs, defStyle, defStyleRes) {
    private val cacheItems = ArrayList<View?>()

    //缩放层叠效果
    private var yOffsetStep = 0 // view叠加垂直偏移量的步长
    private val SCALE_STEP = 0.08f // view叠加缩放的步长
    //缩放层叠效果

    private var MAX_VISIBLE = 4 // 值建议最小为4
    private var MIN_ADAPTER_STACK = 6
    private var ROTATION_DEGREES = 2f
    private var LAST_OBJECT_IN_STACK = 0

    private var mAdapter: T? = null
    var flingListener: OnFlingListener? = null
    private var mDataSetObserver: AdapterDataSetObserver? = null
    private var mInLayout = false
    private var mActiveCard: View? = null
    var onItemClickListener: OnItemClickListener? = null
    private var flingCardListener: FlingCardListener? = null

    // 支持左右滑
    var needSwipe: Boolean = true

    private var initTop = 0
    private var initLeft = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SwipeFlingAdapterView, defStyle, defStyleRes)
        MAX_VISIBLE = a.getInt(R.styleable.SwipeFlingAdapterView_max_visible, MAX_VISIBLE)
        MIN_ADAPTER_STACK = a.getInt(R.styleable.SwipeFlingAdapterView_min_adapter_stack, MIN_ADAPTER_STACK)
        ROTATION_DEGREES = a.getFloat(R.styleable.SwipeFlingAdapterView_rotation_degrees, ROTATION_DEGREES)
        yOffsetStep = a.getDimensionPixelOffset(R.styleable.SwipeFlingAdapterView_y_offset_step, 0)
        a.recycle()
    }

    /**
     * A shortcut method to set both the listeners and the adapter.
     *
     * @param context The activity context which extends onFlingListener, OnItemClickListener or both
     * @param mAdapter The adapter you have to set.
     */
    fun init(context: Context?, mAdapter: T) {
        flingListener = if (context is OnFlingListener) {
            context
        } else {
            throw RuntimeException("Activity does not implement SwipeFlingAdapterView.onFlingListener")
        }
        if (context is OnItemClickListener) {
            onItemClickListener = context
        }
        setAdapter(mAdapter)
    }

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
        // if we don't have an adapter, we don't need to do anything
        if (mAdapter == null) {
            return
        }
        mInLayout = true
        val adapterCount = mAdapter!!.count
        if (adapterCount == 0) {
//            removeAllViewsInLayout();
            removeAndAddToCache(0)
        } else {
            val topCard = getChildAt(LAST_OBJECT_IN_STACK)
            if (mActiveCard != null && topCard != null && topCard === mActiveCard) {
//                removeViewsInLayout(0, LAST_OBJECT_IN_STACK);
                removeAndAddToCache(1)
                layoutChildren(1, adapterCount)
            } else {
                // Reset the UI and set top view listener
//                removeAllViewsInLayout();
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
        if (adapterCount < MIN_ADAPTER_STACK) {
            if (flingListener != null) {
                flingListener!!.onAdapterAboutToEmpty(adapterCount)
            }
        }
    }

    private fun removeAndAddToCache(remain: Int) {
        var view: View?
        val i = 0
        while (i < childCount - remain) {
            view = getChildAt(i)
            removeViewInLayout(view)
            cacheItems.add(view)
        }
    }

    private fun layoutChildren(startingIndex: Int, adapterCount: Int) {
        var startingIndex = startingIndex
        while (startingIndex < Math.min(adapterCount, MAX_VISIBLE)) {
            var item: View? = null
            if (cacheItems.size > 0) {
                item = cacheItems[0]
                cacheItems.remove(item)
            }
            val newUnderChild = mAdapter!!.getView(startingIndex, item, this)
            if (newUnderChild.visibility != GONE) {
                makeAndAddView(newUnderChild, startingIndex)
                LAST_OBJECT_IN_STACK = startingIndex
            }
            startingIndex++
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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
        var layoutDirection = 0
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) layoutDirection = getLayoutDirection()
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val childLeft: Int
        val childTop: Int
        childLeft = when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> (width + paddingLeft - paddingRight - w) / 2 +
                    lp.leftMargin - lp.rightMargin
            Gravity.END -> width + paddingRight - w - lp.rightMargin
            Gravity.START -> paddingLeft + lp.leftMargin
            else -> paddingLeft + lp.leftMargin
        }
        childTop = when (verticalGravity) {
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
        if (index > -1 && index < MAX_VISIBLE) {
            val multiple: Int
            multiple = if (index > 2) 2 else index
            child.offsetTopAndBottom(yOffsetStep * multiple)
            child.scaleX = 1 - SCALE_STEP * multiple
            child.scaleY = 1 - SCALE_STEP * multiple
        }
    }

    private fun adjustChildrenOfUnderTopView(scrollRate: Float) {
        val count = childCount
        if (count > 1) {
            var i: Int
            var multiple: Int
            if (count == 2) {
                i = LAST_OBJECT_IN_STACK - 1
                multiple = 1
            } else {
                i = LAST_OBJECT_IN_STACK - 2
                multiple = 2
            }
            val rate = Math.abs(scrollRate)
            while (i < LAST_OBJECT_IN_STACK) {
                val underTopView = getChildAt(i)
                val offset = (yOffsetStep * (multiple - rate)).toInt()
                underTopView.offsetTopAndBottom(offset - underTopView.top + initTop)
                underTopView.scaleX = 1 - SCALE_STEP * multiple + SCALE_STEP * rate
                underTopView.scaleY = 1 - SCALE_STEP * multiple + SCALE_STEP * rate
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
            mActiveCard = getChildAt(LAST_OBJECT_IN_STACK)
            if (mActiveCard != null && flingListener != null) {
                flingCardListener = FlingCardListener(mActiveCard, mAdapter!!.getItem(0),
                    ROTATION_DEGREES, object : FlingListener {
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

    fun setMaxVisible(MAX_VISIBLE: Int) {
        this.MAX_VISIBLE = MAX_VISIBLE
    }

    fun setMinStackInAdapter(MIN_ADAPTER_STACK: Int) {
        this.MIN_ADAPTER_STACK = MIN_ADAPTER_STACK
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
