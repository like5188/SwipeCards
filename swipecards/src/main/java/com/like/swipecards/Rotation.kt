package com.like.swipecards

import android.graphics.PointF

/**
 * 计算点绕圆心 pivot 旋转角度 angle 后新坐标
 */
fun PointF.rotation(pivot: PointF, angle: Float): PointF {
    val x0 = pivot.x
    val y0 = pivot.y
    val radian = Math.PI / 180 * angle// 弧度
    // 根据"极坐标"求解
    val newX = x0 + (x - x0) * Math.cos(radian) - (y - y0) * Math.sin(radian)
    val newY = y + (x - x0) * Math.sin(radian) + (y - y0) * Math.cos(radian)
    return PointF(newX.toFloat(), newY.toFloat())
}
