package com.like.swipecards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个卡片触摸监听处理
 *
 * @param rotationDegrees   滑动一个视图宽度时的最大旋转角度
 */
class OnCardViewTouchListener(
    private val cardView: View,
    private val data: Any?,
    private val rotationDegrees: Float,
    private val flingListener: FlingListener
) : OnTouchListener {
    private val originCardViewX: Float = cardView.x
    private val originCardViewY: Float = cardView.y
    private val originCardViewWidth: Int = cardView.width
    private val originCardViewHeight: Int = cardView.height
    private val halfCardViewWidth: Float = originCardViewWidth / 2f
    private val halfCardViewHeight: Float = originCardViewHeight / 2f
    private val parentWidth: Int = (cardView.parent as ViewGroup).width
    private val lifecycleScope = cardView.findViewTreeLifecycleOwner()?.lifecycleScope

    // 旋转中心点
    private val pivotPoint: PointF = PointF(originCardViewX + halfCardViewWidth, originCardViewY + halfCardViewHeight)

    // rotationDegrees 对应的弧度
    private val rotationRadian: Double = Math.PI / 180 * rotationDegrees

    // 视图顶部的屏幕坐标
    private val originCardViewRawY: Int = Rect().apply {
        cardView.getGlobalVisibleRect(this)
    }.top

    // cardView 的当前坐标。不包含旋转引起的位移
    private var curCardViewX = 0f
    private var curCardViewY = 0f

    // 手指按下时的坐标
    private var downX = 0f
    private var downY = 0f

    // 手指按下时的屏幕坐标
    private var downRawX = 0f
    private var downRawY = 0f

    // 当前手指触摸点的屏幕坐标
    private var curRawX = 0f
    private var curRawY = 0f

    // 当前活动手指的id
    private var activePointerId = INVALID_POINTER_ID

    /**
     * 触摸的位置。参考 [TOUCH_TOP_HALF]、[TOUCH_BOTTOM_HALF]
     */
    private var touchPosition = 0

    // 退出动画是否正在执行
    private val isExitAnimRunning = AtomicBoolean(false)

    // 支持左右滑
    var isNeedSwipe = true

    var animDuration = 300L

    // x 轴方向上的边界百分比
    var borderPercent: Float = 0.5f

    // 点 src 围绕中心点 pivot 旋转 rotation 角度得到新的点
    private fun getNewPointByRotation(
        src: PointF = PointF(originCardViewX, originCardViewY),
        pivot: PointF = this.pivotPoint,
        rotation: Float = cardView.rotation
    ): PointF {
        val matrix = Matrix()
        matrix.setRotate(rotation, pivot.x, pivot.y)
        val old = FloatArray(2)
        old[0] = src.x
        old[1] = src.y
        matrix.mapPoints(old)
        return PointF(old[0], old[1])
    }

    /**
     * 指定y坐标时的最大滑动距离（角度为 rotationRadian 时的距离）
     * 比如左滑时：就是滑动使得右上角的点滑动到原始左上角点的位置的距离，在不同的 y 坐标时不一致。
     */
    private fun getMaxMoveDistanceXByY(y: Float = downRawY): Float {
        val offset = if (touchPosition == TOUCH_TOP_HALF) {
            Math.abs((y - originCardViewRawY) * Math.tan(rotationRadian)).toFloat()
        } else {
            Math.abs((originCardViewRawY + originCardViewHeight - y) * Math.tan(rotationRadian)).toFloat()
        }
        return originCardViewWidth - offset// 滑动到 rotationDegrees 时的滑动距离。
    }

    // 手指滑动方向。0：上半部分往右滑；1：上半部分往左滑；2：下半部分往右滑；3：下半部分往左滑；4：上滑；5：下滑
    private val moveDirection: Int
        get() {
            // 这里不能使用 curCardViewX - originCardViewX 来计算 dx，因为需要包含旋转，即屏幕上看到的滑动和手指的滑动一致。而 curCardViewX 不包含。
            val dx = curRawX - downRawX
            val dy = curRawY - downRawY
            return when {
                Math.abs(dx) >= Math.abs(dy) -> {// 水平滑动
                    if (dx >= 0) {
                        if (touchPosition == TOUCH_TOP_HALF) 0 else 2
                    } else {
                        if (touchPosition == TOUCH_TOP_HALF) 1 else 3
                    }
                }
                else -> {// 垂直滑动
                    if (dy >= 0) 5 else 4
                }
            }
        }

    // 滑动进度百分比
    private val absMoveProgressPercent: Float
        get() {
            val maxMoveDistanceXByY = getMaxMoveDistanceXByY()
            val dx = curRawX - downRawX
            val dy = curRawY - downRawY
            var percent = when (moveDirection) {
                0, 1, 2, 3 -> dx / maxMoveDistanceXByY
                else -> dy / maxMoveDistanceXByY// 这里也除以窄边宽度，才能使从水平滑动变为垂直滑动时，scale 不跳跃。
            }
            if (percent > 1f) {
                percent = 1f
            }
            if (percent < -1f) {
                percent = -1f
            }
            return Math.abs(percent)
        }

    // 缩放操作任务
    private var scaleJob: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (isExitAnimRunning.get()) {
            return true
        }
        curRawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(event.actionIndex)
        } else {
            event.rawX
        }
        curRawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(event.actionIndex)
        } else {
            event.rawY
        }
        // 以第一个按下的手指为准
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (activePointerId != INVALID_POINTER_ID) {
                    return true
                }
                // remove the listener because 'onAnimationEnd' will still be called if we cancel the animation.
                cardView.animate().setListener(null)
                cardView.animate().cancel()
                scaleJob?.cancel()
                scaleJob = null

                downRawX = curRawX
                downRawY = curRawY

                val pointerIndex = event.actionIndex
                activePointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                downX = x
                downY = y

                curCardViewX = cardView.x
                curCardViewY = cardView.y

                touchPosition = if (y < originCardViewHeight / 2) {
                    TOUCH_TOP_HALF
                } else {
                    TOUCH_BOTTOM_HALF
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.actionIndex
                if (activePointerId != event.getPointerId(pointerIndex)) {
                    return true
                }
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val dx = x - downX
                val dy = y - downY
                curCardViewX += dx
                curCardViewY += dy

                // 根据滑动距离计算旋转角度
                var rotation = (curRawX - downRawX) / getMaxMoveDistanceXByY() * rotationDegrees
                if (touchPosition == TOUCH_BOTTOM_HALF) {
                    rotation = -rotation
                }

                // 移动并旋转视图
                if (isNeedSwipe) {
                    cardView.x = curCardViewX
                    cardView.y = curCardViewY
                    cardView.rotation = rotation
                    flingListener.onScroll(moveDirection, absMoveProgressPercent)
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                if (activePointerId != event.getPointerId(pointerIndex)) {
                    return true
                }
                resetCardViewOnStack(event)
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                resetCardViewOnStack(event)
                activePointerId = INVALID_POINTER_ID
            }
        }
        return true
    }

    /**
     * 为视图执行回弹动画、飞出动画，点击事件判断
     */
    private fun resetCardViewOnStack(event: MotionEvent) {
        if (isNeedSwipe) {
            val moveDirection = moveDirection
            val zoom = if ((moveDirection == 1 || moveDirection == 3) && absMoveProgressPercent > borderPercent) {
                // Left Swipe
                exitWithAnimation(true, getExitPoint(true, event), animDuration, false)
                true
            } else if ((moveDirection == 0 || moveDirection == 2) && absMoveProgressPercent > borderPercent) {
                // Right Swipe
                exitWithAnimation(false, getExitPoint(false, event), animDuration, false)
                true
            } else {
                // 如果能滑动，就根据视图坐标的变化判断点击事件
                val distanceX = Math.abs(curCardViewX - originCardViewX)
                val distanceY = Math.abs(curCardViewY - originCardViewY)
                if (distanceX < 4 && distanceY < 4) {
                    flingListener.onClick(event, cardView, data)
                }
                // 回弹到初始位置
                cardView.animate()
                    .setDuration(animDuration)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .x(originCardViewX)
                    .y(originCardViewY)
                    .rotation(0f)
                    .start()
                false
            }
            // 执行缩放动画
            scale(absMoveProgressPercent, zoom)

            curCardViewX = 0f
            curCardViewY = 0f
            downX = 0f
            downY = 0f
            downRawX = 0f
            downRawY = 0f
        } else {
            // 如果不能滑动，就根据触摸坐标判断点击事件
            val pointerIndex = event.findPointerIndex(activePointerId)
            val distanceX = Math.abs(event.getX(pointerIndex) - downX)
            val distanceY = Math.abs(event.getY(pointerIndex) - downY)
            if (distanceX < 4 && distanceY < 4) {
                flingListener.onClick(event, cardView, data)
            }
        }
    }

    /**
     * 计算缩放系数，并发送数据给 SwipeCardsAdapterView 执行缩放操作
     * @param initScale     初始缩放系数
     * @param zoom          true：放大；false：缩小；
     */
    private fun scale(initScale: Float, zoom: Boolean) {
        scaleJob?.cancel()
        scaleJob = lifecycleScope?.launchWhenResumed {
            var scale = initScale
            if (zoom) {// 放大
                while (scale < 1) {
                    scale += 0.1f
                    if (scale > 1) scale = 1f
                    flingListener.onScroll(moveDirection, scale)
                    // 这里必须用 animDuration 作被除数，因为飞出动画 exitWithAnimation 使用的也是这个值。
                    // 而飞出动画执行完毕后，会导致 SwipeCardsAdapterView 执行 requestLayout，重新 onLayout，
                    // 如果缩放动画此时还未完成的话，就会受到影响。
                    delay(animDuration / 20)
                }
            } else {// 缩小
                while (scale > 0) {
                    scale -= 0.1f
                    if (scale < 0) scale = 0f
                    flingListener.onScroll(moveDirection, scale)
                    delay(animDuration / 20)
                }
            }
        }
    }

    /**
     * 执行飞出屏幕动画，即点(originCardViewX,originCardViewY)移动到 exitPoint 位置。
     *
     * @param byClick   是否单击事件引起的
     */
    private fun exitWithAnimation(isLeft: Boolean, exitPoint: PointF, duration: Long, byClick: Boolean) {
        if (isExitAnimRunning.compareAndSet(false, true)) {
            val animator = cardView.animate()
                .setDuration(duration)
                .setInterpolator(LinearInterpolator())
                .translationX(exitPoint.x)
                .translationY(exitPoint.y)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (isLeft) {
                            flingListener.onCardExited()
                            flingListener.leftExit(data)
                        } else {
                            flingListener.onCardExited()
                            flingListener.rightExit(data)
                        }
                        isExitAnimRunning.set(false)
                    }
                })
            if (byClick) {
                animator.rotation(if (isLeft) -rotationDegrees else rotationDegrees)
            }
            animator.start()
        }
    }

    /**
     * 单击触发往左滑出的动画
     */
    fun exitFromLeft() {
        exitFromLeft(animDuration)
    }

    /**
     * 单击触发往左滑出的动画
     */
    fun exitFromLeft(duration: Long) {
        exitWithAnimation(true, getExitPoint(true, null), duration, true)
    }

    /**
     * 单击触发往右滑出的动画
     */
    fun exitFromRight() {
        exitFromRight(animDuration)
    }

    /**
     * 单击触发往右滑出的动画
     */
    fun exitFromRight(duration: Long) {
        exitWithAnimation(false, getExitPoint(false, null), duration, true)
    }

    /**
     * 如果要视图飞出屏幕，需要计算点(originCardViewX,originCardViewY)移动多少，这里称这个移动后的点为飞出点：exitPoint。
     */
    private fun getExitPoint(isLeft: Boolean, event: MotionEvent?): PointF {
        val newPointByRotation = if (event != null) {
            // 如果是手指触摸滑动，那么是先旋转，再取值，则 cardView.rotation 有值。
            getNewPointByRotation(rotation = cardView.rotation)
        } else {// 如果是单击飞出，那么是先取值，再旋转，则 cardView.rotation 没有值，只能用 rotationDegrees
            getNewPointByRotation(rotation = rotationDegrees)
        }
        val distanceXByRotation = Math.abs(newPointByRotation.x - originCardViewX)
        // 求 exitPoint 需要在x方向的平移距离
        val translationX = if (isLeft) {
            -(originCardViewX + originCardViewWidth) - distanceXByRotation
        } else {
            parentWidth - originCardViewX + distanceXByRotation
        }
        // 求 exitPoint 需要在y方向的平移距离
        val translationY = if (event != null) {// 手指触摸滑动
            // 已知点(downRawX,downRawY)和点(curRawX,curRawY)构成的直线，
            // 平移这条直线，使点(downRawX,downRawY)和点(curCardViewX,curCardViewY)重合，
            // 然后求出点(curRawX,curRawY)在同步平移后的新坐标。
            val newFinishRawX = curRawX - (downRawX - curCardViewX)
            val newFinishRawY = curRawY - (downRawY - curCardViewY)
            // 根据新的点(curCardViewX,curCardViewY)和点(newFinishRawX,newFinishRawY)得到新的直线方程
            val regression = LinearRegression(floatArrayOf(curCardViewX, newFinishRawX), floatArrayOf(curCardViewY, newFinishRawY))
            // 根据直线方程 y = ax+b 求飞出点的y坐标
            regression.slope() * translationX + regression.intercept()
        } else {// 单击飞出
            0f
        }.toFloat()
        return PointF(translationX, translationY)
    }

    interface FlingListener {
        fun onCardExited()
        fun leftExit(dataObject: Any?)
        fun rightExit(dataObject: Any?)
        fun onClick(event: MotionEvent?, v: View?, dataObject: Any?)
        fun onScroll(direction: Int, absProgress: Float)
    }

    companion object {
        private const val INVALID_POINTER_ID = -1

        // 触摸了视图的上半部分
        private const val TOUCH_TOP_HALF = 0

        // 触摸了视图的下半部分
        private const val TOUCH_BOTTOM_HALF = 1
    }

}