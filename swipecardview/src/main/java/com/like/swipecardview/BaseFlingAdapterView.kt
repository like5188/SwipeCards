package com.like.swipecardview

import android.content.Context
import android.util.AttributeSet
import android.widget.Adapter
import android.widget.AdapterView

abstract class BaseFlingAdapterView<T : Adapter>(context: Context?, attrs: AttributeSet?, defStyle: Int) :
    AdapterView<T>(context, attrs, defStyle) {
    var heightMeasureSpec = 0
        private set
    var widthMeasureSpec = 0
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        this.widthMeasureSpec = widthMeasureSpec
        this.heightMeasureSpec = heightMeasureSpec
    }

    override fun setSelection(position: Int) {
        throw UnsupportedOperationException()
    }
}