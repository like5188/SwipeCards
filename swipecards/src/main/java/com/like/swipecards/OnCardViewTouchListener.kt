package com.like.swipecards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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

    // 旋转中心点
    private val pivot: PointF = PointF(originCardViewX + halfCardViewWidth, originCardViewY + halfCardViewHeight)

    // 右上角的原始点
    private val originRightTopPoint: PointF = PointF(originCardViewX + originCardViewWidth, originCardViewY)

    // 当前右上角的点
    private val curRightTopPoint: PointF
        get() {
            val rightTopPoint = getNewPointByRotation(originRightTopPoint)
            rightTopPoint.x += cardView.translationX
            rightTopPoint.y += cardView.translationY
            return rightTopPoint
        }

    // rotationDegrees 对应的弧度
    private val rotationRadian: Double = Math.PI / 180 * rotationDegrees

    // 视图顶部的屏幕坐标
    private val originCardViewRawY: Int = Rect().apply {
        cardView.getGlobalVisibleRect(this)
    }.top

    // cardView 的当前坐标
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

    private val animDuration = 300L
    private var scale = 0f

    // x 轴方向上的左边界
    private val leftBorderX: Float = parentWidth / 2f

    // x 轴方向上的右边界
    private val rightBorderX: Float = parentWidth / 2f

    // 点 src 围绕中心点 pivot 旋转 rotation 角度得到新的点
    private fun getNewPointByRotation(
        src: PointF = PointF(originCardViewX, originCardViewY),
        pivot: PointF = this.pivot,
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

    // x 轴方向上通过移动和旋转造成的位移
    private val absDistanceXByMoveAndRotation: Float
        get() = Math.abs(curCardViewX - originCardViewX) + Math.abs(getNewPointByRotation().x - originCardViewX)

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

    // 手指左右滑动方向。0：上半部分往右滑；1：上半部分往左滑；2：下半部分往右滑；3：下半部分往左滑；
    private val moveDirectionX: Int
        get() {
            val isLeft = curCardViewX < originCardViewX
            return if (touchPosition == TOUCH_TOP_HALF) {
                if (!isLeft) 0 else 1
            } else {
                if (!isLeft) 2 else 3
            }
        }

    // x方向上的滑动进度
    private val absMoveXProgress: Float
        get() {
            var moveRate = cardView.rotation / rotationDegrees
            if (moveRate > 1f) {
                moveRate = 1f
            }
            if (moveRate < -1f) {
                moveRate = -1f
            }
            return Math.abs(moveRate)
        }

    // 手指上下滑动方向。0：下滑；1：上滑；
    private val moveDirectionY: Int
        get() {
            return if (curRawY - downRawY > 0) {
                0
            } else {
                1
            }
        }

    // y方向上的滑动进度
    private val absMoveYProgress: Float
        get() {
            var moveRate = (curRawY - downRawY) / originCardViewHeight
            if (moveRate > 1f) {
                moveRate = 1f
            }
            if (moveRate < -1f) {
                moveRate = -1f
            }
            return Math.abs(moveRate)
        }

    // 还原缩放的动画是否取消
    private val resetScaleAnimCanceled = AtomicBoolean(false)

    // 还原缩放动画
    private val resetScaleRunnable: Runnable = object : Runnable {
        override fun run() {
            // 在 SwipeCardsAdapterView 中会处理底层的所有视图的缩放
            flingListener.onScale(scale)
            if (scale > 0 && !resetScaleAnimCanceled.get()) {
                scale -= 0.1f
                if (scale < 0) scale = 0f
                cardView.postDelayed(this, animDuration / 20)
            }
        }
    }

    // 原始左上角和原始右下角连线的直线方程
    private val leftTop2RightBottomLinearRegression = LinearRegression(
        floatArrayOf(originCardViewX, originCardViewX + originCardViewWidth),
        floatArrayOf(originCardViewY, originCardViewY + originCardViewHeight)
    )

    // 原始右上角和原始左下角连线的直线方程
    private val rightTop2LeftBottomLinearRegression = LinearRegression(
        floatArrayOf(originCardViewX + originCardViewWidth, originCardViewX),
        floatArrayOf(originCardViewY, originCardViewY + originCardViewHeight)
    )

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
                resetScaleAnimCanceled.set(true)

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
                // 修正由于手指触摸点的 y 坐标不一致，那么滑动到最大角度所需要的滑动距离不一致，那么就需要修正这个差距使得滑动到最大角度时，都是 rotationDegrees 度。
                // 具体滑动全程参考 FlingListener.onScroll的注释
                val offset = if (touchPosition == TOUCH_TOP_HALF) {
                    Math.abs((downRawY - originCardViewRawY) * Math.tan(rotationRadian)).toFloat()
                } else {
                    Math.abs((originCardViewRawY + originCardViewHeight - downRawY) * Math.tan(rotationRadian)).toFloat()
                }
                val maxMoveDistanceX = originCardViewWidth - offset// 滑动到 rotationDegrees 时的滑动距离。
                val moveDistanceX = curRawX - downRawX
                var rotation = rotationDegrees * moveDistanceX / maxMoveDistanceX
                if (touchPosition == TOUCH_BOTTOM_HALF) {
                    rotation = -rotation
                }

                // 移动并旋转视图
                if (isNeedSwipe) {
                    cardView.x = curCardViewX
                    cardView.y = curCardViewY
                    cardView.rotation = rotation
                    if (isHorizontalMove()) {
                        flingListener.onHorizontalScroll(moveDirectionX, absMoveXProgress)
                        flingListener.onScale(absMoveXProgress)
                    } else {
                        flingListener.onVerticalScroll(moveDirectionY, absMoveYProgress)
                        flingListener.onScale(absMoveYProgress)
                    }
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
     * 手指是否左右滑动
     */
    private fun isHorizontalMove(): Boolean {
        val isLeft = curCardViewX < originCardViewX
        // 根据直线方程 y = ax+b 求滑出点的y坐标
        val yInRightTop2LeftBottomLinearRegression =
            rightTop2LeftBottomLinearRegression.slope() * curRightTopPoint.x + rightTop2LeftBottomLinearRegression.intercept()
        val yInLeftTop2RightBottomLinearRegression =
            leftTop2RightBottomLinearRegression.slope() * curRightTopPoint.x + leftTop2RightBottomLinearRegression.intercept()
        val isHorizontalMove = if (touchPosition == TOUCH_TOP_HALF) {
            when {
                curCardViewX < originCardViewX -> curRightTopPoint.y < yInRightTop2LeftBottomLinearRegression
                curCardViewX > originCardViewX -> curRightTopPoint.y < yInLeftTop2RightBottomLinearRegression
                else -> false
            }
        } else {
            when {
                curCardViewX < originCardViewX -> curRightTopPoint.y > yInLeftTop2RightBottomLinearRegression
                curCardViewX > originCardViewX -> curRightTopPoint.y > yInRightTop2LeftBottomLinearRegression
                else -> false
            }
        }
        Log.e("TAG", "$isHorizontalMove $curRightTopPoint")
        return isHorizontalMove
    }

    /**
     * 为视图执行回弹动画、滑出动画，点击事件判断
     */
    private fun resetCardViewOnStack(event: MotionEvent) {
        if (isNeedSwipe) {
            val isLeft = curCardViewX < originCardViewX
            if (isMovedBeyondLeftBorder) {
                // Left Swipe
                exitWithAnimation(isLeft, getExitPoint(isLeft, event), animDuration, false)
                flingListener.onHorizontalScroll(1, 1f)
                flingListener.onScale(1f)
            } else if (isMovedBeyondRightBorder) {
                // Right Swipe
                exitWithAnimation(isLeft, getExitPoint(isLeft, event), animDuration, false)
                flingListener.onHorizontalScroll(0, 1f)
                flingListener.onScale(1f)
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
                // 还原缩放动画
                if (resetScaleAnimCanceled.compareAndSet(true, false)) {
                    scale = if (isHorizontalMove()) {
                        absMoveXProgress
                    } else {
                        absMoveYProgress
                    }
                    cardView.post(resetScaleRunnable)
                }
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
     * 执行自动滑出屏幕动画，即点(originCardViewX,originCardViewY)移动到 exitPoint 位置。
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
     * 如果要视图滑出屏幕，需要获取点(originCardViewX,originCardViewY)移动的位移，这里称为滑出点：exitPoint。
     */
    private fun getExitPoint(isLeft: Boolean, event: MotionEvent?): PointF {
        val newPointByRotation = if (event != null) {
            // 如果是手指触摸滑动，那么是先旋转，再取值，则 cardView.rotation 有值。
            getNewPointByRotation(rotation = cardView.rotation)
        } else {// 如果是单击滑出，那么是先取值，再旋转，则 cardView.rotation 没有值，只能用 rotationDegrees
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
            // 已知点(downRawX,downRawY)和点(finishRawX,finishRawY)构成的直线，
            // 平移这条直线，使点(downRawX,downRawY)和点(curCardViewX,curCardViewY)重合，
            // 然后求出点(finishRawX,finishRawY)在同步平移后的新坐标。
            val newFinishRawX = curRawX - (downRawX - curCardViewX)
            val newFinishRawY = curRawY - (downRawY - curCardViewY)
            // 根据新的点(curCardViewX,curCardViewY)和点(newFinishRawX,newFinishRawY)得到新的直线方程
            val regression = LinearRegression(floatArrayOf(curCardViewX, newFinishRawX), floatArrayOf(curCardViewY, newFinishRawY))
            // 根据直线方程 y = ax+b 求滑出点的y坐标
            regression.slope() * translationX + regression.intercept()
        } else {// 单击滑出
            0f
        }.toFloat()
        return PointF(translationX, translationY)
    }

    interface FlingListener {
        fun onCardExited()
        fun leftExit(dataObject: Any?)
        fun rightExit(dataObject: Any?)
        fun onClick(event: MotionEvent?, v: View?, dataObject: Any?)

        /**
         * 左右滑动回调
         * @param direction     手指滑动方向。0：上半部分往右滑；1：上半部分往左滑；2：下半部分往右滑；3：下半部分往左滑；
         * @param absProgress   视图滑动进度的绝对值。
         * direction==0：全程为：视图左上角滑动到与视图原来的右上角重叠。
         * direction==1：全程为：视图右上角滑动到与视图原来的左上角重叠。
         * direction==2：全程为：视图左下角滑动到与视图原来的右下角重叠。
         * direction==3：全程为：视图右下角滑动到与视图原来的左下角重叠。
         */
        fun onHorizontalScroll(direction: Int, absProgress: Float)

        /**
         * 上下滑动回调
         * @param direction     手指滑动方向。0：下滑；1：上滑；
         * @param absProgress   视图滑动进度的绝对值。
         */
        fun onVerticalScroll(direction: Int, absProgress: Float)

        /**
         * 缩放回调
         *
         * @param scale     缩放系数
         */
        fun onScale(scale: Float)
    }

    companion object {
        private const val INVALID_POINTER_ID = -1

        // 触摸了视图的上半部分
        private const val TOUCH_TOP_HALF = 0

        // 触摸了视图的下半部分
        private const val TOUCH_BOTTOM_HALF = 1
    }

}