package com.like.swipecards

import android.view.View

interface OnSwipeListener {
    /**
     * 滚动回调
     *
     * @param direction     手指滑动方向。
     * [DIRECTION_TOP_HALF_RIGHT]、
     * [DIRECTION_TOP_HALF_LEFT]、
     * [DIRECTION_BOTTOM_HALF_RIGHT]、
     * [DIRECTION_BOTTOM_HALF_LEFT]、
     * [DIRECTION_UP]、
     * [DIRECTION_DOWN]
     * @param absProgress   滑动进度百分比。最大宽度为视图宽度，参照物是视图的四个顶点（具体是哪个根据滑动方向确定）
     */
    fun onScroll(direction: Int, absProgress: Float)

    /**
     * 视图完全离开屏幕时回调
     */
    fun onCardExited(direction: Int, dataObject: Any?)

    fun onClick(v: View?, dataObject: Any?)

    /**
     * 去添加数据
     */
    fun onLoadData()

    /**
     * 回退栈缓存数量改变回调
     */
    fun onUndoChange(size: Int)
}
