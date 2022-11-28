package com.like.swipecards

import android.view.View

/**
 * 视图状态数据，用于保持或者恢复视图时使用
 */
data class ViewStatus(
    val x: Float,
    val y: Float,
    val translationX: Float,
    val translationY: Float,
    val rotation: Float,
    val scaleX: Float,
    val scaleY: Float,
)

var View.viewStatus: ViewStatus
    get() = ViewStatus(
        x, y, translationX, translationY, rotation, scaleX, scaleY
    )
    set(value) {
        with(this) {
            x = value.x
            y = value.y
            translationX = value.translationX
            translationY = value.translationY
            rotation = value.rotation
            scaleX = value.scaleX
            scaleY = value.scaleY
        }
    }

