package com.like.swipecards

/**
 * 上半部分往右滑
 */
const val DIRECTION_TOP_HALF_RIGHT = 0

/**
 * 上半部分往左滑
 */
const val DIRECTION_TOP_HALF_LEFT = 1

/**
 * 下半部分往右滑
 */
const val DIRECTION_BOTTOM_HALF_RIGHT = 2

/**
 * 下半部分往左滑
 */
const val DIRECTION_BOTTOM_HALF_LEFT = 3

/**
 * 上滑
 */
const val DIRECTION_UP = 4

/**
 * 下滑
 */
const val DIRECTION_DOWN = 5

/**
 * 左、右两边边飞出的视图都能回退
 */
const val UNDO_ALL = 0

/**
 * 只能回退从左边飞出的视图
 */
const val UNDO_LEFT = 1

/**
 * 只能回退从右边飞出的视图
 */
const val UNDO_RIGHT = 2