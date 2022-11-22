package com.like.swipecardview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.PointF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by dionysis_lorentzos on 5/8/14
 * for package com.lorentzos.swipecards
 * and project Swipe cards.
 * Use with caution dinausaurs might appear!
 */
class FlingCardListener(
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
    private val pivot: PointF = PointF(originCardViewX + halfCardViewWidth, originCardViewY + halfCardViewHeight)

    // cardView 的当前坐标
    private var curCardViewX = 0f
    private var curCardViewY = 0f

    // 手指按下时的坐标
    private var downX = 0f
    private var downY = 0f

    // 手指按下时的屏幕坐标
    private var downRawX = 0f
    private var downRawY = 0f

    // The active pointer is the one currently moving our object.
    private var activePointerId = INVALID_POINTER_ID

    // 触摸的位置。参考 TOUCH_ABOVE、TOUCH_BELOW
    private var touchPosition = 0

    private var isAnimationRunning = AtomicBoolean(false)

    // 支持左右滑
    var isNeedSwipe = true

    private val animDuration = 300L
    private var scale = 0f

    /**
     * every time we touch down,we should stop the [.animRun]
     */
    private var resetAnimCanceled = false

    // x 轴方向上的左边界
    private val leftBorderX: Float = parentWidth / 2f

    // x 轴方向上的右边界
    private val rightBorderX: Float = parentWidth / 2f

    // x 轴方向上通过移动和旋转 rotation 角度造成的位移
    private fun getNewPointByRotation(rotation: Float): PointF {
        val angle = Math.abs(rotation)
        val oldPoint = PointF(originCardViewX, originCardViewY)
        return oldPoint.rotation(pivot, angle)
    }

    // x 轴方向上通过移动和旋转造成的位移
    private val absDistanceXByMoveAndRotation: Float
        get() = Math.abs(curCardViewX - originCardViewX) + Math.abs(getNewPointByRotation(cardView.rotation).x - originCardViewX)

    // y 轴方向上通过移动和旋转造成的位移
    private val absDistanceYByMoveAndRotation: Float
        get() = Math.abs(curCardViewY - originCardViewY) + Math.abs(getNewPointByRotation(cardView.rotation).y - originCardViewY)

    // 是否左滑超出了左边界
    private val isMovedBeyondLeftBorder: Boolean
        get() {
            val anchorX = originCardViewX + originCardViewWidth
            return anchorX - absDistanceXByMoveAndRotation < leftBorderX
        }

    // 是否右滑超出了右边界
    private val isMovedBeyondRightBorder: Boolean
        get() {
            val anchorX = originCardViewX
            return anchorX + absDistanceXByMoveAndRotation > rightBorderX
        }

    private val scrollProgress: Float
        get() {
            val dx = curCardViewX - originCardViewX
            val dy = curCardViewY - originCardViewY
            val dis = Math.abs(dx) + Math.abs(dy)
            return Math.min(dis, 400f) / 400f
        }
    private val scrollXProgressPercent: Float
        get() {
            val isLeft = curCardViewX < originCardViewX
            return if (isLeft && isMovedBeyondLeftBorder) {
                -1f
            } else if (!isLeft && isMovedBeyondRightBorder) {
                1f
            } else {
                val zeroToOneValue = (curCardViewX + halfCardViewWidth - leftBorderX) / (rightBorderX - leftBorderX)
                zeroToOneValue * 2f - 1f
            }
        }
    private val animRun: Runnable = object : Runnable {
        override fun run() {
            flingListener.onScroll(scale, 0f)
            if (scale > 0 && !resetAnimCanceled) {
                scale -= 0.1f
                if (scale < 0) scale = 0f
                cardView.postDelayed(this, animDuration / 20)
            }
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (isAnimationRunning.get()) {
            return true
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
                resetAnimCanceled = true

                downRawX = event.rawX
                downRawY = event.rawY

                // Save the ID of this pointer
                val pointerIndex = event.actionIndex
                activePointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // Remember where we started
                downX = x
                downY = y
                // to prevent an initial jump of the magnifier, aposX and aPosY must have the values from the magnifier cardView
                curCardViewX = cardView.x
                curCardViewY = cardView.y
                touchPosition = if (y < originCardViewHeight / 2) {
                    TOUCH_ABOVE
                } else {
                    TOUCH_BELOW
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.actionIndex
                if (activePointerId != event.getPointerId(pointerIndex)) {
                    return true
                }
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // from http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
                // Calculate the distance moved
                val dx = x - downX
                val dy = y - downY

                // Move the cardView
                curCardViewX += dx
                curCardViewY += dy

                // calculate the rotation degrees
                val distanceX = curCardViewX - originCardViewX
                var rotation = rotationDegrees * 2f * distanceX / parentWidth
                if (touchPosition == TOUCH_BELOW) {
                    rotation = -rotation
                }

                // in this area would be code for doing something with the view as the cardView moves.
                if (isNeedSwipe) {
                    cardView.x = curCardViewX
                    cardView.y = curCardViewY
                    cardView.rotation = rotation
                    flingListener.onScroll(scrollProgress, scrollXProgressPercent)
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

    private fun resetCardViewOnStack(event: MotionEvent) {
        if (isNeedSwipe) {
            val duration = 200L
            val isLeft = curCardViewX < originCardViewX
            if (isMovedBeyondLeftBorder) {
                // Left Swipe
                exitWithAnimation(isLeft, getExitPoint(isLeft, event), duration, false)
                flingListener.onScroll(1f, -1.0f)
            } else if (isMovedBeyondRightBorder) {
                // Right Swipe
                exitWithAnimation(isLeft, getExitPoint(isLeft, event), duration, false)
                flingListener.onScroll(1f, 1.0f)
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
                scale = scrollProgress
                cardView.postDelayed(animRun, 0)
                resetAnimCanceled = false
            }
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
     * 自动滑出屏幕
     *
     * @param byClick   是否单击事件引起的
     */
    private fun exitWithAnimation(isLeft: Boolean, exitPoint: PointF, duration: Long, byClick: Boolean) {
        if (isAnimationRunning.compareAndSet(false, true)) {
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
                        isAnimationRunning.set(false)
                    }
                })
            if (byClick) {
                animator.rotation(if (isLeft) -rotationDegrees else rotationDegrees)
            }
            animator.start()
        }
    }

    /**
     * Starts a default left exit animation.
     */
    fun exitFromLeft() {
        exitFromLeft(animDuration)
    }

    /**
     * Starts a default left exit animation.
     */
    fun exitFromLeft(duration: Long) {
        exitWithAnimation(true, getExitPoint(true, null), duration, true)
    }

    /**
     * Starts a default right exit animation.
     */
    fun exitFromRight() {
        exitFromRight(animDuration)
    }

    /**
     * Starts a default right exit animation.
     */
    fun exitFromRight(duration: Long) {
        exitWithAnimation(false, getExitPoint(false, null), duration, true)
    }

    /**
     * 如果要视图滑出屏幕，需要获取点(originCardViewX,originCardViewY)移动的位移，这里称为滑出点：exitPoint。
     */
    private fun getExitPoint(isLeft: Boolean, event: MotionEvent?): PointF {
        val newPointByRotation = if (event != null) {
            // 如果是手指触摸滑动，那么是先旋转，再取值，则 cardView.rotation 有值。
            getNewPointByRotation(cardView.rotation)
        } else {// 如果是单击滑出，那么是先取值，再旋转，则 cardView.rotation 没有值，只能用 rotationDegrees
            getNewPointByRotation(rotationDegrees)
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
            val pointerIndex = event.findPointerIndex(activePointerId)
            // 事件结束时的相对屏幕的x坐标
            val finishRawX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.getRawX(pointerIndex)
            } else {
                event.rawX
            }
            // 事件结束时的相对屏幕的y坐标
            val finishRawY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.getRawY(pointerIndex)
            } else {
                event.rawY
            }
            // 已知点(downRawX,downRawY)和点(finishRawX,finishRawY)构成的直线，
            // 平移这条直线，使点(downRawX,downRawY)和点(curCardViewX,curCardViewY)重合，
            // 然后求出点(finishRawX,finishRawY)在同步平移后的新坐标。
            val newFinishRawX = finishRawX - (downRawX - curCardViewX)
            val newFinishRawY = finishRawY - (downRawY - curCardViewY)
            // 根据新的点(curCardViewX,curCardViewY)和点(newFinishRawX,newFinishRawY)得到新的直线方程
            val regression = LinearRegression(floatArrayOf(curCardViewX, newFinishRawX), floatArrayOf(curCardViewY, newFinishRawY))
            // 根据直线方程 y = ax+b 求滑出点的y坐标
            regression.slope().toFloat() * translationX + regression.intercept().toFloat()
        } else {// 单击滑出
            0f
        }
        return PointF(translationX, translationY)
    }

    interface FlingListener {
        fun onCardExited()
        fun leftExit(dataObject: Any?)
        fun rightExit(dataObject: Any?)
        fun onClick(event: MotionEvent?, v: View?, dataObject: Any?)
        fun onScroll(progress: Float, scrollXProgress: Float)
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val TOUCH_ABOVE = 0
        private const val TOUCH_BELOW = 1
    }

}