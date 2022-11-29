package com.like.swipecards

import android.content.Context
import android.database.DataSetObserver
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import androidx.databinding.ViewDataBinding
import kotlin.math.abs

/*
ViewGroup的职能为：给childView计算出建议的宽和高和测量模式 ；决定childView的位置；为什么只是建议的宽和高，而不是直接确定呢，别忘了childView宽和高可以设置为wrap_content，这样只有childView才能计算出自己的宽和高。
View的职责，根据测量模式和ViewGroup给出的建议的宽和高，计算出自己的宽和高；同时还有个更重要的职责是：在ViewGroup为其指定的区域内绘制自己的形态。
 */
/**
 * 滑动卡片集合视图
 */
class SwipeCardsAdapterView<T : SwipeCardsAdapterView.Adapter<*>> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyle, defStyleRes) {
    private val mRecycler by lazy {
        Recycler()
    }
    private val mUndo by lazy {
        Undo().apply {
            onChange = {
                onSwipeListener?.onUndoChange(it)
            }
        }
    }
    private lateinit var adapter: T

    // 是否是刷新操作
    private var isRefreshData: Boolean = false
    private val dataSetObserver: DataSetObserver by lazy {
        object : DataSetObserver() {
            override fun onChanged() {
                isRefreshData = false
                requestLayout()
            }

            override fun onInvalidated() {
                isRefreshData = true
                requestLayout()
            }
        }
    }

    /**
     * 视觉看到的"最外层"的那个视图。
     */
    private var topView: View? = null
        set(value) {
            if (originTopViewLeft == 0 && originTopViewTop == 0 && value != null) {
                originTopViewTop = value.top
                originTopViewLeft = value.left
            }
            field = value
        }

    /**
     * topView 的索引。
     * 注意：最底层（屏幕最深处）的索引是0。
     */
    private val topViewIndex: Int
        get() = Math.min(adapter.count, config.maxChildCount) - 1

    // 记录 TopView 原始位置的left、top
    private var originTopViewLeft = 0
    private var originTopViewTop = 0

    private val onCardViewTouchListener by lazy {
        OnCardViewTouchListener().also {
            it.onSwipeListener = object : OnSwipeListener {
                override fun onCardExited(direction: Int, dataObject: Any?) {
                    topView?.let {
                        removeViewInLayout(it)
                        mUndo.push(it.viewStatus.apply {
                            this.data = dataObject
                        })
                        mRecycler.addScrapView(it.tag as ViewHolder<*>)
                    }
                    onSwipeListener?.onCardExited(direction, dataObject)
                    // 通知加载数据
                    if (adapter.count == config.prefetchCount) {
                        onSwipeListener?.onLoadData()
                    }
                }

                override fun onClick(v: View?, dataObject: Any?) {
                    onSwipeListener?.onClick(v, dataObject)
                }

                override fun onScroll(direction: Int, absProgress: Float) {
                    var rate = absProgress / config.scaleMax// 修正系数
                    if (rate > 1f) {
                        rate = 1f
                    }
                    adjustChildren(rate, false)
                    onSwipeListener?.onScroll(direction, absProgress)
                }

                override fun onLoadData() {
                }

                override fun onUndoChange(size: Int) {
                    onSwipeListener?.onUndoChange(size)
                }
            }
        }
    }

    var config: Config = Config()
        set(value) {
            onCardViewTouchListener.animDuration = value.animDuration
            onCardViewTouchListener.maxRotationAngle = value.maxRotationAngle
            onCardViewTouchListener.borderPercent = value.borderPercent
            onCardViewTouchListener.isNeedSwipe = value.isNeedSwipe
            mUndo.maxCacheSize = value.maxUndoCacheSize
            field = value
        }

    var onSwipeListener: OnSwipeListener? = null

    /**
     * 单击触发往左滑出
     */
    fun swipeLeft() {
        onCardViewTouchListener.swipeLeft()
    }

    /**
     * 单击触发往右滑出
     */
    fun swipeRight() {
        onCardViewTouchListener.swipeRight()
    }

    fun setAdapter(adapter: T) {
        if (this::adapter.isInitialized) {
            this.adapter.unregisterDataSetObserver(dataSetObserver)
        }
        this.adapter = adapter
        this.adapter.registerDataSetObserver(dataSetObserver)
    }

    fun undo() {
        if (AnimatorHelper.isAnimRunning.get()) {
            return
        }
        val undoViewStatus = mUndo.pop() ?: return
        val removeView = if (childCount == config.maxChildCount) {
            // 此时 mRecycler 中是没有缓存的，所以需要复用最底层那个被遮住的视图，当然此视图也必须要移除才对。
            getChildAt(0).apply {
                removeViewInLayout(this)
            }
        } else {
            // 此时不存在最底层被遮住的视图，但是 mRecycler 中肯定会有缓存，因为缓存都是用来复用到最底层被遮住的视图了。
            mRecycler.getLastAddScrapView()
        } ?: return
        addViewInLayout(removeView, childCount, removeView.layoutParams, true)
        // 还原ViewStatus，即View飞出后的状态。
        removeView.viewStatus = undoViewStatus
        adapter.undo(removeView, undoViewStatus.data)
        // 飞回初始位置
        AnimatorHelper.reset(
            removeView,
            config.animDuration,
            originTopViewLeft.toFloat(),
            originTopViewTop.toFloat(),
            1f,
            1,
            onEnd = {
                resetTopView()// 需要重新设置topView
            }
        ) { direction, progress ->
            // 回退时不需要修正缩放系数，这样效果更好
            adjustChildren(progress, false)
            onSwipeListener?.onScroll(direction, progress)
        }
    }

    fun clearUndoCache() {
        mUndo.clear()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.i("TAG", "onMeasure")
        makeAndAddView()
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0) return
        measureChildren(widthMeasureSpec, heightMeasureSpec)
    }

    private fun makeAndAddView() {
        val adapterCount = adapter.count
        if (adapterCount == 0 || isRefreshData) {// 一个个删除完所有，或者清除adapter中的所有数据时触发
            isRefreshData = false
            if (childCount > 0) {
                (childCount - 1 downTo 0).forEach {
                    val child = getChildAt(it)
                    removeViewInLayout(child)// 移除完成后会重新触发onMeasure，从而触发resetTopView()方法
                    mRecycler.addScrapView(child.tag as ViewHolder<*>)
                }
            }
            resetTopView()// 不管是清除，还是一个个删除，当数据为空时，都需要重置topView
        } else if (topView == null) {// 初始化时触发
            if (childCount == 0) {
                addChildren(0)
            }
        } else {// 一个个删除topView后，但是还没有全部删除完成触发
            if (childCount <= topViewIndex) {
                addChildren(childCount)// 添加一个视图到最底层
            }
        }
    }

    /**
     * @param startIndex    添加数据的开始索引。注意：这个索引是数据集合中的位置，和屏幕视图的索引是相反的。
     */
    private fun addChildren(startIndex: Int) {
        (startIndex..topViewIndex).forEach { index ->
            val scrapView = mRecycler.getScrapView(index)?.itemView
            val child = adapter.getView(index, scrapView, this)
            // 添加child，并且不触发requestLayout()方法，性能比addView更好。index为0代表往屏幕最底层插入。
            addViewInLayout(child, 0, child.layoutParams, true)
        }
    }

    // 此处重写此方法，而不使用FrameLayout自带的测量方法，是因为它把margin也计算到了child的宽中，这样会导致滑动判断边界的时候更麻烦。
    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParams as LayoutParams
        val childWidthSpec = getChildMeasureSpec(
            parentWidthMeasureSpec,
            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
            lp.width
        )
        val childHeightSpec = getChildMeasureSpec(
            parentHeightMeasureSpec,
            paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
            lp.height
        )
        child.measure(childWidthSpec, childHeightSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        Log.i("TAG", "onLayout")
        if (childCount == 0) return
        super.onLayout(changed, left, top, right, bottom)
        adjustChildren()
        resetTopView()
    }

    // 设置OnCardViewTouchListener监听必须在layout完成后，否则OnCardViewTouchListener中获取不到cardView的相关参数。
    private fun resetTopView() {
        topView = getChildAt(topViewIndex)?.apply {
            // attachView必须在layout完成后，否则获取不到view的相关参数。
            onCardViewTouchListener.attach(this, adapter.getItem(0))
        }
    }

    /**
     * 调整视图达到缩放层叠效果
     * 注意：
     * 1、如果是初始化，那么就调整TopView 之下的所有视图。
     * 2、如果不是初始化，那么就调整TopView 之下的所有"可见"视图。"可见"是指最底层那一个被遮住的视图不需要处理，因为它不需要缩放和位移。它只是在其上层的视图缩放或者位移时显现出来而已。
     * @param rate      滑动进度
     * @param isInit    是否初始化
     */
    private fun adjustChildren(rate: Float = 0f, isInit: Boolean = true) {
        val absRate = abs(rate)
        // index代表可见的最底层视图的索引。所有视图的最底层的视图为0。topViewIndex-1代表TopView 之下
        for (index in (topViewIndex - 1 downTo 0)) {
            // 如果不是初始化，并且"最底层那一个被遮住的视图"存在
            if (!isInit && childCount >= config.maxChildCount) {
                if (index == 0) {// 排除"最底层那一个被遮住的视图"
                    return
                }
            }
            // 层级。最外层（topView）的层级为 0，向底层递增
            var level = topViewIndex - index
            if (level > config.maxChildCount - 2) {
                level = config.maxChildCount - 2
            }
            // 进行缩放和垂直平移
            getChildAt(index)?.apply {
                val yOffset = (config.yOffsetStep * (level - absRate)).toInt()
                val curYOffset = if (isInit) 0 else top - originTopViewTop
                offsetTopAndBottom(yOffset - curYOffset)
                val scale = 1 - config.scaleStep * level + config.scaleStep * absRate
                scaleX = scale
                scaleY = scale
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mRecycler.clear()
        mUndo.clear()
    }

    class ViewHolder<VB : ViewDataBinding>(val binding: VB) {
        val itemView: View = binding.root
        var itemViewType: Int = -1
    }

    abstract class Adapter<VH : ViewHolder<*>> : BaseAdapter() {

        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH

        abstract fun onBindViewHolder(holder: VH, data: Any?)

        /**
         * 回退时调用，在此把数据[data]还原到列表中。
         */
        abstract fun onUndo(data: Any?)

        private fun createViewHolder(parent: ViewGroup, viewType: Int): VH {
            val holder = onCreateViewHolder(parent, viewType)
            check(holder.itemView.parent == null) {
                ("ViewHolder views must not be attached when"
                        + " created. Ensure that you are not passing 'true' to the attachToRoot"
                        + " parameter of LayoutInflater.inflate(..., boolean attachToRoot)")
            }
            holder.itemViewType = viewType
            return holder
        }

        private fun bindViewHolder(holder: VH, data: Any?) {
            onBindViewHolder(holder, data)
        }

        fun undo(view: View, data: Any?) {
            onUndo(data)
            val viewHolder = view.tag as? VH ?: return
            bindViewHolder(viewHolder, data)
        }

        final override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var viewHolder: VH? = convertView?.tag as? VH
            if (viewHolder == null) {
                viewHolder = createViewHolder(parent, getItemViewType(position)).apply {
                    itemView.tag = this
                }
            }
            bindViewHolder(viewHolder, getItem(position))
            return viewHolder.itemView
        }
    }

    private inner class Recycler {
        /**
         * 按 viewType 缓存视图
         * key：viewType；
         * value：视图集合，这个集合每种 viewType 的视图最多只会有[Config.maxChildCount]个。
         *        因为都是添加一个使用一个。最多的时候就是清除或者一个个删除到最后[maxCount]个的时候。
         */
        private val mScrapViewMap = mutableMapOf<Int, MutableList<ViewHolder<*>>>()
        private var mLastScrapViewType: Int? = null

        fun clear() {
            mScrapViewMap.clear()
        }

        fun getScrapView(position: Int): ViewHolder<*>? {
            val viewType = adapter.getItemViewType(position)
            return getScrapViewByViewType(viewType)
        }

        fun getScrapViewByViewType(viewType: Int): ViewHolder<*>? {
            val scrapViewList = mScrapViewMap[viewType]
            val scrapView = scrapViewList?.removeLastOrNull()
            if (mScrapViewMap[viewType].isNullOrEmpty()) {
                mScrapViewMap.remove(viewType)
            }
            if (scrapView != null) {
                Log.d("TAG", "从缓存中获取到视图：${scrapView.hashCode()}")
            }
            return scrapView?.apply {
                // 重置 view 的状态，否则在取出缓存使用时，会影响测量和布局。
                this.itemView.viewStatus = ViewStatus(
                    originTopViewLeft.toFloat(),
                    originTopViewTop.toFloat(),
                    0f, 0f, 0f, 1f, 1f
                )
            }
        }

        fun addScrapView(scrap: ViewHolder<*>) {
            val viewType = scrap.itemViewType
            mLastScrapViewType = viewType
            if (mScrapViewMap.containsKey(viewType)) {
                mScrapViewMap[viewType]?.add(scrap)
            } else {
                mScrapViewMap[viewType] = mutableListOf(scrap)
            }
        }

        fun getLastAddScrapView(): View? {
            val viewType = mLastScrapViewType ?: return null
            return getScrapViewByViewType(viewType)?.itemView
        }

    }

    /**
     * [SwipeCardsAdapterView] 需要的配置参数
     * @param maxChildCount     存在屏幕中的最大子视图数量。包括"最底层那一个被遮住的视图"。
     * @param prefetchCount     预取数据阈值
     * 当数量等于此值时，触发加载数据的操作。建议 >=[maxChildCount]，这样才不会出现缩放时最下面那个界面需要加载，而是先就加载好了的。
     * @param yOffsetStep       缩放层叠时的垂直偏移量步长
     * @param scaleStep         缩放层叠时的缩放步长
     * @param scaleMax          当滑动进度为这个值时，缩放到最大。[0f,1f]
     * @param animDuration      动画执行时长
     * @param maxRotationAngle  比如左滑时：就是卡片左上角滑动到卡片原始 left 时，卡片的旋转角度
     * @param borderPercent     x 轴方向上的边界百分比[0f,1f]，相对于卡片的 left 或者 right
     * @param isNeedSwipe       是否支持左右滑
     * @param maxUndoCacheSize  回退栈大小
     */
    data class Config(
        val maxChildCount: Int = 4,
        val prefetchCount: Int = 4,
        val yOffsetStep: Int = 100,
        val scaleStep: Float = 0.08f,
        val scaleMax: Float = 0.5f,
        val animDuration: Long = 300,
        val maxRotationAngle: Float = 20f,
        val borderPercent: Float = 0.5f,
        val isNeedSwipe: Boolean = true,
        val maxUndoCacheSize: Int = 2,
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readInt(),
            parcel.readInt(),
            parcel.readInt(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readLong(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readByte() != 0.toByte(),
            parcel.readInt()
        ) {
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(maxChildCount)
            parcel.writeInt(prefetchCount)
            parcel.writeInt(yOffsetStep)
            parcel.writeFloat(scaleStep)
            parcel.writeFloat(scaleMax)
            parcel.writeLong(animDuration)
            parcel.writeFloat(maxRotationAngle)
            parcel.writeFloat(borderPercent)
            parcel.writeByte(if (isNeedSwipe) 1 else 0)
            parcel.writeInt(maxUndoCacheSize)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Config> {
            override fun createFromParcel(parcel: Parcel): Config {
                return Config(parcel)
            }

            override fun newArray(size: Int): Array<Config?> {
                return arrayOfNulls(size)
            }
        }
    }
}
