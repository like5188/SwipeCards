package com.like.swipecards

import android.view.View

/**
 * 视图状态数据，用于保持或者恢复视图时使用
 */
data class ViewStatus(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

fun View.setViewStatus(viewStatus: ViewStatus = ViewStatus()) {
    with(this) {
        translationX = viewStatus.translationX
        translationY = viewStatus.translationY
        rotation = viewStatus.rotation
        scaleX = viewStatus.scaleX
        scaleY = viewStatus.scaleY
    }
}
