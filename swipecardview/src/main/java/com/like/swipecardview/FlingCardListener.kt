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
    private val objectX: Float = cardView.x
    private val objectY: Float = cardView.y
    private val objectH: Int = cardView.width
    private val objectW: Int = cardView.height
    private val parentWidth: Int = (cardView.parent as ViewGroup).width
    private val halfWidth: Float = objectW / 2f

    private var aPosX = 0f
    private var aPosY = 0f
    private var aDownTouchX = 0f
    private var aDownTouchY = 0f

    // The active pointer is the one currently moving our object.
    private var activePointerId = INVALID_POINTER_ID
    private var touchPosition = 0

    private var isAnimationRunning = AtomicBoolean(false)

    // 支持左右滑
    var isNeedSwipe = true
    private var aTouchUpX = 0f
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
        get() = objectW / MAX_COS - objectW

    private val scrollProgress: Float
        get() {
            val dx = aPosX - objectX
            val dy = aPosY - objectY
            val dis = Math.abs(dx) + Math.abs(dy)
            return Math.min(dis, 400f) / 400f
        }
    private val scrollXProgressPercent: Float
        get() = if (movedBeyondLeftBorder()) {
            -1f
        } else if (movedBeyondRightBorder()) {
            1f
        } else {
            val zeroToOneValue = (aPosX + halfWidth - leftBorder()) / (rightBorder() - leftBorder())
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
                    activePointerId = event.getPointerId(0)
                    val x = event.getX(activePointerId)
                    val y = event.getY(activePointerId)

                    // Remember where we started
                    aDownTouchX = x
                    aDownTouchY = y
                    // to prevent an initial jump of the magnifier, aposX and aPosY must
                    // have the values from the magnifier frame
                    aPosX = cardView.x
                    aPosY = cardView.y
                    touchPosition = if (y < objectH / 2) {
                        TOUCH_ABOVE
                    } else {
                        TOUCH_BELOW
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {}
                MotionEvent.ACTION_POINTER_UP -> {
                    // Extract the index of the pointer that left the touch sensor
                    val pointerIndex = event.action and
                            MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        activePointerId = event.getPointerId(newPointerIndex)
                    }
                }
                MotionEvent.ACTION_MOVE -> {

                    // Find the index of the active pointer and fetch its position
                    val pointerIndexMove = event.findPointerIndex(activePointerId)
                    val xMove = event.getX(pointerIndexMove)
                    val yMove = event.getY(pointerIndexMove)

                    // from http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
                    // Calculate the distance moved
                    val dx = xMove - aDownTouchX
                    val dy = yMove - aDownTouchY

                    // Move the frame
                    aPosX += dx
                    aPosY += dy

                    // calculate the rotation degrees
                    val distObjectX = aPosX - objectX
                    var rotation = rotationDegrees * 2f * distObjectX / parentWidth
                    if (touchPosition == TOUCH_BELOW) {
                        rotation = -rotation
                    }

                    // in this area would be code for doing something with the view as the frame moves.
                    if (isNeedSwipe) {
                        cardView.x = aPosX
                        cardView.y = aPosY
                        cardView.rotation = rotation
                        flingListener.onScroll(scrollProgress, scrollXProgressPercent)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    //mActivePointerId = INVALID_POINTER_ID;
                    val pointerCount = event.pointerCount
                    val activePointerId = Math.min(activePointerId, pointerCount - 1)
                    aTouchUpX = event.getX(activePointerId)
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
                exitWithAnimation(true, getExitPoint(-objectW), duration.toLong())
                flingListener.onScroll(1f, -1.0f)
            } else if (movedBeyondRightBorder()) {
                // Right Swipe
                exitWithAnimation(false, getExitPoint(parentWidth), duration.toLong())
                flingListener.onScroll(1f, 1.0f)
            } else {
                val absMoveXDistance = Math.abs(aPosX - objectX)
                val absMoveYDistance = Math.abs(aPosY - objectY)
                if (absMoveXDistance < 4 && absMoveYDistance < 4) {
                    flingListener.onClick(event, cardView, data)
                } else {
                    cardView.animate()
                        .setDuration(animDuration.toLong())
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .x(objectX)
                        .y(objectY)
                        .rotation(0f)
                        .start()
                    scale = scrollProgress
                    cardView.postDelayed(animRun, 0)
                    resetAnimCanceled = false
                }
                aPosX = 0f
                aPosY = 0f
                aDownTouchX = 0f
                aDownTouchY = 0f
            }
        } else {
            val distanceX = Math.abs(aTouchUpX - aDownTouchX)
            if (distanceX < 4) flingListener.onClick(event, cardView, data)
        }
        return false
    }

    private fun movedBeyondLeftBorder(): Boolean {
        return aPosX + halfWidth < leftBorder()
    }

    private fun movedBeyondRightBorder(): Boolean {
        return aPosX + halfWidth > rightBorder()
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
                -objectW - rotationWidthOffset
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
        exitWithAnimation(true, objectY, duration)
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
        exitWithAnimation(false, objectY, duration)
    }

    private fun getExitPoint(exitXPoint: Int): Float {
        val x = FloatArray(2)
        x[0] = objectX
        x[1] = aPosX
        val y = FloatArray(2)
        y[0] = objectY
        y[1] = aPosY
        val regression = LinearRegression(x, y)

        //Your typical y = ax+b linear regression
        return regression.slope().toFloat() * exitXPoint + regression.intercept().toFloat()
    }

    private fun getExitRotation(isLeft: Boolean): Float {
        var rotation = rotationDegrees * 2f * (parentWidth - objectX) / parentWidth
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