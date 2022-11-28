package com.like.swipecards

import android.util.Log
import java.util.*

/**
 * 回退栈
 */
internal class Undo {
    /**
     * 缓存数量改变回调
     */
    var onChange: ((Int) -> Unit)? = null

    /**
     * 最大缓存数量，用于回退操作
     */
    var maxCacheCount = 2
    private val mCache = LinkedList<ViewStatus>()

    fun push(viewStatus: ViewStatus) {
        if (maxCacheCount == 0) return
        if (mCache.size >= maxCacheCount) {
            mCache.removeLast()
        }
        mCache.push(viewStatus)
        onChange?.invoke(mCache.size)
    }

    fun pop(): ViewStatus? {
        if (mCache.isEmpty()) return null
        return mCache.pop().apply {
            Log.d("TAG", "回退：$this")
            onChange?.invoke(mCache.size)
        }
    }

    fun clear() {
        mCache.clear()
        onChange?.invoke(mCache.size)
    }

}
