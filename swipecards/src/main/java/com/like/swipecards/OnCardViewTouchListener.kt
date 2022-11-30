package com.like.swipecards

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.core.graphics.toRectF
import kotlin.math.abs
import kotlin.math.tan

/**
 * 单个卡片视图触摸相关的处理
 * 包括功能：滑动、旋转、飞出、缩放等动画的处理
 */
class OnCardViewTouchListener : OnTouchListener {
    private lateinit var cardView: View
    private var originCardViewX: Float = 0f
    private var originCardViewY: Float = 0f
    private var originCardViewWidth: Int = 0
    private var originCardViewHeight: Int = 0
    private var halfCardViewWidth: Float = 0f
    private var halfCardViewHeight: Float = 0f
    private var parentWidth: Int = 0

    // 旋转中心点
    private lateinit var pivotPoint: PointF

    // 视图顶部的屏幕坐标
    private var originCardViewRawY: Int = 0

    // 原始视图的矩形
    private lateinit var originRectF: RectF

    // 对应的数据
    private var data: Any? = null

    /**
     * 附到指定视图上。
     * 此方法必须在 layout 完成后，否则获取不到 view 的相关参数。
     */
    fun attach(view: View, data: Any?) {
        with(view) {
            cardView = this
            originCardViewX = x
            originCardViewY = y
            originCardViewWidth = width
            originCardViewHeight = height
            halfCardViewWidth = originCardViewWidth / 2f
            halfCardViewHeight = originCardViewHeight / 2f
            parentWidth = (parent as ViewGroup).width
            pivotPoint = PointF(originCardViewX + halfCardViewWidth, originCardViewY + halfCardViewHeight)
            originCardViewRawY = Rect().also { getGlobalVisibleRect(it) }.top
            Rect().apply {
                getHitRect(this)
                originRectF = toRectF()
            }
            setOnTouchListener(this@OnCardViewTouchListener)
        }
        this.data = data
    }

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
     * 触摸的位置。参考 [TOUCH_PART_TOP_HALF]、[TOUCH_PART_BOTTOM_HALF]
     */
    private var touchPart = TOUCH_PART_TOP_HALF

    // 手指滑动方向。
    private val moveDirection: Int
        get() {
            // 这里不能使用 curCardViewX - originCardViewX 来计算 dx，因为需要包含旋转，即屏幕上看到的滑动和手指的滑动一致。而 curCardViewX 不包含。
            val dx = curRawX - downRawX
            val dy = curRawY - downRawY
            return when {
                abs(dx) >= abs(dy) -> {// 水平滑动
                    if (dx >= 0) {
                        if (touchPart == TOUCH_PART_TOP_HALF) DIRECTION_TOP_HALF_RIGHT else DIRECTION_BOTTOM_HALF_RIGHT
                    } else {
                        if (touchPart == TOUCH_PART_TOP_HALF) DIRECTION_TOP_HALF_LEFT else DIRECTION_BOTTOM_HALF_LEFT
                    }
                }
                else -> {// 垂直滑动
                    if (dy >= 0) DIRECTION_DOWN else DIRECTION_UP
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
                DIRECTION_TOP_HALF_RIGHT, DIRECTION_TOP_HALF_LEFT,
                DIRECTION_BOTTOM_HALF_RIGHT, DIRECTION_BOTTOM_HALF_LEFT -> dx / maxMoveDistanceXByY
                else -> dy / maxMoveDistanceXByY// 这里也除以窄边宽度，只有横向和纵向长度一致时，才能使从水平滑动变为垂直滑动时，scale 不跳跃。
            }
            if (percent > 1f) {
                percent = 1f
            }
            if (percent < -1f) {
                percent = -1f
            }
            return abs(percent)
        }

    /**
     * 指定y坐标时的最大滑动距离（角度为 rotationRadian 时的距离）
     * 比如左滑时：就是滑动使得右上角的点滑动到原始左上角点的位置的距离，在不同的 y 坐标时不一致。
     */
    private fun getMaxMoveDistanceXByY(y: Float = downRawY): Float {
        // rotationDegrees 对应的弧度
        val rotationRadian: Double = Math.PI / 180 * maxRotationAngle
        val offset = if (isSameRotationWhenTouchTopAndBottom) {
            abs((y - originCardViewRawY) * tan(rotationRadian)).toFloat()
        } else {
            if (touchPart == TOUCH_PART_TOP_HALF) {
                abs((y - originCardViewRawY) * tan(rotationRadian)).toFloat()
            } else {
                abs((originCardViewRawY + originCardViewHeight - y) * tan(rotationRadian)).toFloat()
            }
        }
        return originCardViewWidth - offset// 滑动到 rotationDegrees 时的滑动距离。
    }

    // 每秒移动的最小像素
    private var minimumVelocity = 800
    private var downTime: Long = 0L

    /**
     * 是否水平快速滑动
     * 注意：这里不直接使用[GestureDetector]或者[VelocityTracker]来计算，
     * 是因为它们没有计算手指停留的时间（因为此期间没有事件产生），即手指停留很久再松开，也会触发快速滑动操作。
     */
    private val isHorizontalQuickSwipe: Boolean
        get() {
            val time = (System.currentTimeMillis() - downTime) / 1000f
            val dX = abs(curRawX - downRawX)
            val xVelocity = dX / time
            return xVelocity > minimumVelocity
        }

    /**
     * 比如左滑时：就是卡片左上角滑动到卡片原始 left 时，卡片的旋转角度
     */
    var maxRotationAngle: Float = 20f

    /**
     * 是否支持左右滑
     */
    var isNeedSwipe = true

    /**
     * 动画执行时长
     */
    var animDuration = 300L

    /**
     * x 轴方向上的边界百分比[0f,1f]，相对于 left 或者 right
     */
    var borderPercent: Float = 0.5f

    /**
     * 触摸上半部分和下半部分是否使用相同的旋转方向
     */
    var isSameRotationWhenTouchTopAndBottom: Boolean = false

    var onSwipeListener: OnSwipeListener? = null

    /**
     * 单击触发往左滑出
     */
    fun swipeLeft() {
        exitWithAnimation(true, getExitPoint(true, null), true, 0f)
    }

    /**
     * 单击触发往右滑出
     */
    fun swipeRight() {
        exitWithAnimation(false, getExitPoint(false, null), true, 0f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (AnimatorHelper.isAnimRunning.get()) {
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
                downTime = System.currentTimeMillis()
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

                touchPart = if (y < originCardViewHeight / 2) {
                    TOUCH_PART_TOP_HALF
                } else {
                    TOUCH_PART_BOTTOM_HALF
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
                var rotation = (curRawX - downRawX) / getMaxMoveDistanceXByY() * maxRotationAngle
                if (touchPart == TOUCH_PART_BOTTOM_HALF && !isSameRotationWhenTouchTopAndBottom) {
                    rotation = -rotation
                }

                // 移动并旋转视图
                if (isNeedSwipe) {
                    cardView.x = curCardViewX
                    cardView.y = curCardViewY
                    cardView.rotation = rotation
                    onSwipeListener?.onScroll(moveDirection, absMoveProgressPercent)
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
            val direction = moveDirection
            if ((direction == DIRECTION_TOP_HALF_LEFT || direction == DIRECTION_BOTTOM_HALF_LEFT)// 左滑
                && (absMoveProgressPercent > borderPercent || isHorizontalQuickSwipe)// 超出边界或者是快速滑动
            ) {
                exitWithAnimation(true, getExitPoint(true, event), false, absMoveProgressPercent)
            } else if ((direction == DIRECTION_TOP_HALF_RIGHT || direction == DIRECTION_BOTTOM_HALF_RIGHT)// 右滑
                && (absMoveProgressPercent > borderPercent || isHorizontalQuickSwipe)// 超出边界或者是快速滑动
            ) {
                // Right Swipe
                exitWithAnimation(false, getExitPoint(false, event), false, absMoveProgressPercent)
            } else {
                // 如果能滑动，就根据视图坐标的变化判断点击事件
                val distanceX = abs(curCardViewX - originCardViewX)
                val distanceY = abs(curCardViewY - originCardViewY)
                if (distanceX < 4 && distanceY < 4) {
                    onSwipeListener?.onClick(cardView, data)
                }
                // 回弹到初始位置
                AnimatorHelper.reset(
                    cardView,
                    animDuration,
                    originCardViewX,
                    originCardViewY,
                    absMoveProgressPercent,
                    moveDirection
                ) { direction, progress ->
                    onSwipeListener?.onScroll(direction, progress)
                }
            }
        } else {
            // 如果不能滑动，就根据触摸坐标判断点击事件
            val pointerIndex = event.findPointerIndex(activePointerId)
            val distanceX = abs(event.getX(pointerIndex) - downX)
            val distanceY = abs(event.getY(pointerIndex) - downY)
            if (distanceX < 4 && distanceY < 4) {
                onSwipeListener?.onClick(cardView, data)
            }
        }
    }

    /**
     * 执行飞出屏幕动画，即点(originCardViewX,originCardViewY)移动到 exitPoint 位置。
     *
     * @param byClick   是否单击事件引起的
     * @param initScale 初始缩放系数，用于控制缩放动画
     */
    private fun exitWithAnimation(isLeft: Boolean, exitPoint: PointF, byClick: Boolean, initScale: Float) {
        // 移动方向。包括手指滑动和单击自动移动。
        AnimatorHelper.exit(
            cardView,
            animDuration,
            isLeft,
            exitPoint,
            byClick,
            maxRotationAngle,
            initScale,
            moveDirection,
            onEnd = {
                onSwipeListener?.onCardExited(it, data)
            }) { direction, progress ->
            onSwipeListener?.onScroll(direction, progress)
        }
    }

    /**
     * 获取视图完全飞出屏幕时的偏移距离，需要考虑旋转造成的宽度变化。
     */
    private fun getExitPoint(isLeft: Boolean, event: MotionEvent?): PointF {
        val rotation = if (event != null) {
            // 如果是手指触摸滑动，那么是先旋转，再取值，则 cardView.rotation 有值。
            cardView.rotation
        } else {// 如果是单击飞出，那么是先取值，再旋转，则 cardView.rotation 没有值，只能用 maxRotationAngle
            maxRotationAngle
        }
        val newRectFByRotation = getNewRectFByRotation(rotation = if (isLeft) -rotation else rotation)
        // 求需要在x方向的平移距离。根据x方向来判断，不管y方向。
        val translationX = if (isLeft) {
            -newRectFByRotation.right
        } else {
            -newRectFByRotation.left + parentWidth
        }
        // 求需要在y方向的平移距离
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
        } else {// 单击飞出，需要保持最上面那个顶点的y坐标不变，所以就需要设置为旋转导致的y轴方向上的偏移。
//            val yOffsetByRotation = originRectF.top - newRectFByRotation.top// 由于旋转造成的y轴方向的偏移
//            0f + yOffsetByRotation
            0f
        }.toFloat()
        return PointF(translationX, translationY)
    }

    /**
     * 获取原始矩形围绕中心点 [pivot] 旋转 [rotation] 角度得到的新矩形
     */
    private fun getNewRectFByRotation(
        pivot: PointF = this.pivotPoint,
        rotation: Float = cardView.rotation
    ): RectF {
        // 原始矩形
        val rectF = RectF(originRectF)
        // 旋转
        val matrix = Matrix()
        matrix.setRotate(rotation, pivot.x, pivot.y)
        matrix.mapRect(rectF)
        return rectF
    }

    /**
     * 获取点[src]围绕中心点 [pivot] 旋转 [rotation] 角度得到的新点
     */
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

    companion object {
        private const val INVALID_POINTER_ID = -1

        // 触摸了视图的上半部分
        private const val TOUCH_PART_TOP_HALF = 0

        // 触摸了视图的下半部分
        private const val TOUCH_PART_BOTTOM_HALF = 1
    }

}