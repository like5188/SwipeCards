package com.like.swipecards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.addListener
import java.util.concurrent.atomic.AtomicBoolean

object AnimatorHelper {
    /**
     * 动画是否正在执行
     */
    val isAnimRunning = AtomicBoolean(false)
    private var mViewPropertyAnimators = mutableListOf<ViewPropertyAnimator>()
    private var mValueAnimators = mutableListOf<ValueAnimator>()

    fun cancel() {
        mViewPropertyAnimators.listIterator().forEach {
            it.setListener(null)
            it.setUpdateListener(null)
            it.cancel()
            mViewPropertyAnimators.remove(it)
        }
        mValueAnimators.listIterator().forEach {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
            mValueAnimators.remove(it)
        }
        isAnimRunning.set(false)
    }

    /**
     * 执行回弹动画
     */
    fun reset(
        view: View,
        duration: Long,
        x: Float,
        y: Float,
        initScale: Float,
        direction: Int,
        onEnd: ((direction: Int) -> Unit)? = null,
        onUpdate: ((direction: Int, progress: Float) -> Unit)? = null,
    ) {
        if (isAnimRunning.compareAndSet(false, true)) {
            val animator = view.animate()
                .setDuration(duration)
                .setInterpolator(OvershootInterpolator(1.5f))
                .x(x)
                .y(y)
                .rotation(0f)
                .withStartAction {
                    // 执行缩放动画
                    scale(duration, initScale, false) {
                        onUpdate?.invoke(direction, it)
                    }
                }
            animator.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke(direction)
                    isAnimRunning.set(false)
                    mViewPropertyAnimators.remove(animator)
                }
            })
            mViewPropertyAnimators.add(animator)
            animator.start()
        }
    }

    /**
     * 执行飞出屏幕动画，即点(originCardViewX,originCardViewY)移动到 exitPoint 位置。
     *
     * @param byClick   是否单击事件引起的
     * @param initScale 初始缩放系数，用于控制缩放动画
     */
    fun exit(
        view: View,
        duration: Long,
        isLeft: Boolean,
        exitPoint: PointF,
        byClick: Boolean,
        rotationDegrees: Float,
        initScale: Float,
        direction: Int,
        onEnd: ((direction: Int) -> Unit)? = null,
        onUpdate: ((direction: Int, progress: Float) -> Unit)? = null,
    ) {
        // 移动方向。包括手指滑动和单击自动移动。
        val d = if (byClick) {
            if (isLeft) DIRECTION_TOP_HALF_LEFT else DIRECTION_TOP_HALF_RIGHT
        } else {
            direction
        }
        if (isAnimRunning.compareAndSet(false, true)) {
            val animator = view.animate()
                .setDuration(duration)
                .setInterpolator(LinearInterpolator())
                .translationX(exitPoint.x)
                .translationY(exitPoint.y)
                .withStartAction {
                    // 执行缩放动画
                    scale(duration, initScale, true) {
                        onUpdate?.invoke(d, it)
                    }
                }
            animator.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke(d)
                    isAnimRunning.set(false)
                    mViewPropertyAnimators.remove(animator)
                }
            })
            if (byClick) {
                animator.rotation(if (isLeft) -rotationDegrees else rotationDegrees)
            }
            mViewPropertyAnimators.add(animator)
            animator.start()

        }
    }

    /**
     * 计算缩放系数，并发送数据给 SwipeCardsAdapterView 执行缩放操作。
     * 这个缩放操作是为了在手指离开屏幕后，补充完成进度回调
     * @param initScale     初始缩放系数
     * @param zoom          true：放大；false：缩小；
     */
    private fun scale(
        duration: Long,
        initScale: Float,
        zoom: Boolean,
        onUpdate: ((progress: Float) -> Unit)? = null
    ) {
        ValueAnimator.ofFloat(initScale, if (zoom) 1f else 0f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener {
                onUpdate?.invoke(it.animatedValue as Float)
            }
            addListener(
                onEnd = {
                    mValueAnimators.remove(this)
                }
            )
            mValueAnimators.add(this)
            start()
        }
    }
}