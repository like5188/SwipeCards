package com.like.swipecardview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    private val parentWidth: Int = (cardView.parent as ViewGroup).width

    // cardView 的当前坐标
    private var curCardViewX = 0f
    private var curCardViewY = 0f

    // 手指按下时的坐标
    private var downX = 0f
    private var downY = 0f

    // 手指抬起时的坐标
    private var upX = 0f

    // The active pointer is the one currently moving our object.
    private var activePointerId = INVALID_POINTER_ID

    // 触摸的位置。参考 TOUCH_ABOVE、TOUCH_BELOW
    private var touchPosition = 0

    private var isAnimationRunning = AtomicBoolean(false)

    // 支持左右滑
    var isNeedSwipe = true

    private val animDuration = 300
    private var scale = 0f

    /**
     * every time we touch down,we should stop the [.animRun]
     */
    private var resetAnimCanceled = false

    /**
     * When the object rotates it's width becomes bigger.
     * The maximum width is at 45 degrees.
     *
     * The below method calculates the width offset of the rotation.
     *
     */
    private val rotationWidthOffset: Float
        get() = originCardViewWidth / MAX_COS - originCardViewWidth

    private val scrollProgress: Float
        get() {
            val dx = curCardViewX - originCardViewX
            val dy = curCardViewY - originCardViewY
            val dis = Math.abs(dx) + Math.abs(dy)
            return Math.min(dis, 400f) / 400f
        }
    private val scrollXProgressPercent: Float
        get() = if (movedBeyondLeftBorder()) {
            -1f
        } else if (movedBeyondRightBorder()) {
            1f
        } else {
            val zeroToOneValue = (curCardViewX + halfCardViewWidth - leftBorder()) / (rightBorder() - leftBorder())
            zeroToOneValue * 2f - 1f
        }
    private val animRun: Runnable = object : Runnable {
        override fun run() {
            flingListener.onScroll(scale, 0f)
            if (scale > 0 && !resetAnimCanceled) {
                scale -= 0.1f
                if (scale < 0) scale = 0f
                cardView.postDelayed(this, animDuration / 20L)
            }
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        try {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    // remove the listener because 'onAnimationEnd' will still be called if we cancel the animation.
                    cardView.animate().setListener(null)
                    cardView.animate().cancel()
                    resetAnimCanceled = true

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
                MotionEvent.ACTION_POINTER_DOWN -> {}
                MotionEvent.ACTION_POINTER_UP -> {
                    // Extract the index of the pointer that left the touch sensor
                    val pointerIndex = event.action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        // This was our active pointer going up. Choose a new active pointer and adjust accordingly.
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        activePointerId = event.getPointerId(newPointerIndex)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Find the index of the active pointer and fetch its position
                    val pointerIndex = event.findPointerIndex(activePointerId)
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
                    val disX = curCardViewX - originCardViewX
                    var rotation = rotationDegrees * 2f * disX / parentWidth
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pointerCount = event.pointerCount
                    val activePointerId = Math.min(activePointerId, pointerCount - 1)
                    upX = event.getX(activePointerId)
                    this.activePointerId = INVALID_POINTER_ID
                    resetCardViewOnStack(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    private fun resetCardViewOnStack(event: MotionEvent): Boolean {
        if (isNeedSwipe) {
            val duration = 200
            if (movedBeyondLeftBorder()) {
                // Left Swipe
                exitWithAnimation(true, getExitPoint(-originCardViewWidth), duration.toLong())
                flingListener.onScroll(1f, -1.0f)
            } else if (movedBeyondRightBorder()) {
                // Right Swipe
                exitWithAnimation(false, getExitPoint(parentWidth), duration.toLong())
                flingListener.onScroll(1f, 1.0f)
            } else {
                val absMoveXDistance = Math.abs(curCardViewX - originCardViewX)
                val absMoveYDistance = Math.abs(curCardViewY - originCardViewY)
                if (absMoveXDistance < 4 && absMoveYDistance < 4) {
                    flingListener.onClick(event, cardView, data)
                } else {
                    cardView.animate()
                        .setDuration(animDuration.toLong())
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
            }
        } else {
            val distanceX = Math.abs(upX - downX)
            if (distanceX < 4) flingListener.onClick(event, cardView, data)
        }
        return false
    }

    private fun movedBeyondLeftBorder(): Boolean {
        return curCardViewX + halfCardViewWidth < leftBorder()
    }

    private fun movedBeyondRightBorder(): Boolean {
        return curCardViewX + halfCardViewWidth > rightBorder()
    }

    private fun leftBorder(): Float {
        return parentWidth / 4f
    }

    private fun rightBorder(): Float {
        return 3 * parentWidth / 4f
    }

    private fun exitWithAnimation(isLeft: Boolean, exitY: Float, duration: Long) {
        if (isAnimationRunning.compareAndSet(false, true)) {
            val exitX: Float = if (isLeft) {
                -originCardViewWidth - rotationWidthOffset
            } else {
                parentWidth + rotationWidthOffset
            }
            cardView.animate()
                .setDuration(duration)
                .setInterpolator(LinearInterpolator())
                .translationX(exitX)
                .translationY(exitY)
                .rotation(if (isLeft) -rotationDegrees else rotationDegrees)
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
                }).start()
        }
    }

    /**
     * Starts a default left exit animation.
     */
    fun exitFromLeft() {
        exitFromLeft(animDuration.toLong())
    }

    /**
     * Starts a default left exit animation.
     */
    fun exitFromLeft(duration: Long) {
        exitWithAnimation(true, originCardViewY, duration)
    }

    /**
     * Starts a default right exit animation.
     */
    fun exitFromRight() {
        exitFromRight(animDuration.toLong())
    }

    /**
     * Starts a default right exit animation.
     */
    fun exitFromRight(duration: Long) {
        exitWithAnimation(false, originCardViewY, duration)
    }

    private fun getExitPoint(exitXPoint: Int): Float {
        val x = FloatArray(2)
        x[0] = originCardViewX
        x[1] = curCardViewX
        val y = FloatArray(2)
        y[0] = originCardViewY
        y[1] = curCardViewY
        val regression = LinearRegression(x, y)

        //Your typical y = ax+b linear regression
        return regression.slope().toFloat() * exitXPoint + regression.intercept().toFloat()
    }

    private fun getExitRotation(isLeft: Boolean): Float {
        var rotation = rotationDegrees * 2f * (parentWidth - originCardViewX) / parentWidth
        if (touchPosition == TOUCH_BELOW) {
            rotation = -rotation
        }
        if (isLeft) {
            rotation = -rotation
        }
        return rotation
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
        private val MAX_COS = Math.cos(Math.toRadians(45.0)).toFloat()
    }

}