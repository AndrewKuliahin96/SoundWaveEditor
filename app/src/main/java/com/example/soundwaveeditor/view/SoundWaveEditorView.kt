package com.example.soundwaveeditor.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.example.soundwaveeditor.R
import kotlin.math.absoluteValue


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs),
    GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    companion object {
        private const val MINUTES_TWO_SYMBOLS_TIME_FORMAT = "%02d:%02d"
        private const val MINUTES_ONE_SYMBOL_TIME_FORMAT = "%01d:%02d"
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_TIME_TEXT_SIZE = 14F
        private const val DEFAULT_COLUMNS_RATIO = 1F
        private const val ZERO_SIZE_F = 0F
        private const val ZERO_SIZE = 0
        private const val DEFAULT_SLIDE_BARS_PADDING = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 100
        private const val DEFAULT_MIN_COLUMNS_COUNT = 20
        private const val DEFAULT_COLUMNS_COUNT = 50
        private const val ZERO_SOUND_DURATION = 0
    }

    private var gestureDetector: GestureDetectorCompat

    private val histogramTopPaddingRatioRange = 0F..2F
    private val verticalPaddingRatioRange = 0F..0.5F
    private val columnsRatioRange = 0F..1F

    private var histogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var halfOfHistogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var spacingBetweenColumns = DEFAULT_COLUMNS_RATIO
    private var columnWidth = DEFAULT_COLUMNS_RATIO
    private var columnRadius = ZERO_SIZE_F

    private var fWidth = ZERO_SIZE_F
    private var fHeight = ZERO_SIZE_F

    private var histogramYAxis = ZERO_SIZE_F
    private var histogramHeight = ZERO_SIZE_F

    private var histogramBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var inactiveColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var activeColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var slideBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = DEFAULT_TIME_TEXT_SIZE
    }

    private var histogramBackgroundRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)

    private val drawingRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)

    private val textRect = Rect(ZERO_SIZE, ZERO_SIZE, ZERO_SIZE, ZERO_SIZE)

    private val columns = mutableListOf<ColumnSize>()

    var timeTextSize = DEFAULT_TIME_TEXT_SIZE
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            timeTextPaint.textSize = it
        }

    var timeTextColor = android.R.color.black
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            timeTextPaint.color = it
        }

    var histogramBackgroundColor = android.R.color.transparent
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            histogramBackgroundPaint.color = it
        }

    var inactiveColumnsColor = android.R.color.darker_gray
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            inactiveColumnsPaint.color = it
        }

    var activeColumnsColor = android.R.color.holo_red_light
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            activeColumnsPaint.color = it
        }

    var slideBarsColor = android.R.color.holo_red_light
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            slideBarPaint.color = it
        }

    var maxColumnsCount = DEFAULT_MAX_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, { value >= currentColumnsCount }, {
            field = it
        })

    var minColumnsCount = DEFAULT_MIN_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, { value < currentColumnsCount }, {
            field = it
        })

    var currentColumnsCount = DEFAULT_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(
            value,
            { value in (minColumnsCount + 1) until maxColumnsCount },
            {
                field = it
            })

    var slideBarsPaddingInColumns = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in 1 until currentColumnsCount }, {
            field = it
        })

    var soundDuration = 0
        set(value) = field.getIfAndInvalidate(value, { value > 0 }, {
            field = it
        })

    var needToRoundColumns = false
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
        }

    var histogramTopPaddingRatio = DEFAULT_HISTOGRAM_TOP_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in histogramTopPaddingRatioRange }, {
            field = it
        })

    var columnSpacingRatio = DEFAULT_COLUMNS_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in columnsRatioRange }, {
            field = it
        })

    var columnVerticalPaddingRatio = DEFAULT_VERTICAL_PADDING_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in verticalPaddingRatioRange }, {
            field = it
        })

    var firstVisibleColumn = 0
        set(value) = field.getIfAndInvalidate(value, {
            value in 0..columnBytes.size - currentColumnsCount
        }) {
            field = it
        }

    var columnBytes = mutableListOf<UByte>()
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
        }

    var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in DEFAULT_SLIDE_BARS_PADDING..rightSlideBar - slideBarsPaddingInColumns
        }) {
            field = it
        }

    var rightSlideBar = DEFAULT_COLUMNS_COUNT - DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in leftSlideBar + slideBarsPaddingInColumns - 1 until currentColumnsCount - DEFAULT_SLIDE_BARS_PADDING
        }) {
            field = when (it == currentColumnsCount) {
                true -> it - 1
                false -> it
            }
        }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SoundWaveEditorView, 0, 0).apply {
            timeTextSize = getDimension(
                R.styleable.SoundWaveEditorView_timeTextSize, DEFAULT_TIME_TEXT_SIZE
            )

            timeTextColor = getColor(
                R.styleable.SoundWaveEditorView_timeTextColor,
                ContextCompat.getColor(context, android.R.color.black)
            )

            histogramBackgroundColor = getColor(
                R.styleable.SoundWaveEditorView_histogramBackground,
                ContextCompat.getColor(context, android.R.color.transparent)
            )

            activeColumnsColor = getColor(
                R.styleable.SoundWaveEditorView_activeColumnsColor,
                ContextCompat.getColor(context, android.R.color.holo_red_light)
            )

            inactiveColumnsColor = getColor(
                R.styleable.SoundWaveEditorView_inactiveColumnsColor,
                ContextCompat.getColor(context, android.R.color.darker_gray)
            )

            slideBarsColor = getColor(
                R.styleable.SoundWaveEditorView_slideBarsColor,
                ContextCompat.getColor(context, android.R.color.holo_red_light)
            )

            maxColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_maxColumnsCount, DEFAULT_MAX_COLUMNS_COUNT
            )

            minColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_minColumnsCount, DEFAULT_MIN_COLUMNS_COUNT
            )

            currentColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_currentColumnsCount, DEFAULT_COLUMNS_COUNT
            )

            slideBarsPaddingInColumns = getInt(
                R.styleable.SoundWaveEditorView_slideBarsPaddingInColumns,
                DEFAULT_SLIDE_BARS_PADDING
            )

            soundDuration = getInt(
                R.styleable.SoundWaveEditorView_soundDuration, ZERO_SOUND_DURATION
            )

            needToRoundColumns =
                getBoolean(R.styleable.SoundWaveEditorView_needToRoundColumns, false)

            columnSpacingRatio =
                getFloat(R.styleable.SoundWaveEditorView_columnSpacingRatio, DEFAULT_COLUMNS_RATIO)

            columnVerticalPaddingRatio =
                getFloat(
                    R.styleable.SoundWaveEditorView_columnVerticalPaddingRatio,
                    DEFAULT_VERTICAL_PADDING_RATIO
                )
        }.recycle()

        setLayerType(LAYER_TYPE_HARDWARE, null)

        histogramBackgroundPaint.color = histogramBackgroundColor
        activeColumnsPaint.color = activeColumnsColor
        inactiveColumnsPaint.color = inactiveColumnsColor
        slideBarPaint.color = slideBarsColor
        timeTextPaint.apply {
            color = timeTextColor
            textSize = timeTextSize
        }

        gestureDetector = GestureDetectorCompat(context, this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        getElementsSizes(w, h)
    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        drawRect(histogramBackgroundRectF, histogramBackgroundPaint)

        val lastVisibleItem = firstVisibleColumn + currentColumnsCount

        columns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, column ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> inactiveColumnsPaint
                }.let { paint ->
                    val leftColumnSide = index * columnWidth + index * spacingBetweenColumns

                    drawingRectF.run {
                        left = leftColumnSide
                        right = leftColumnSide + columnWidth
                        top = if (isSlideBar) halfOfHistogramTopPadding else column.top
                        bottom = if (isSlideBar) fHeight else column.bottom
                    }

                    when (needToRoundColumns) {
                        true -> drawRoundRect(drawingRectF, columnRadius, columnRadius, paint)
                        false -> drawRect(drawingRectF, paint)
                    }
                }
            }

        getTimeAndPosition(leftSlideBar) { text, x, y -> drawText(text, x, y, timeTextPaint) }
        getTimeAndPosition(rightSlideBar) { text, x, y -> drawText(text, x, y, timeTextPaint) }
    }


    // TODO rename all, remove unused


    // TODO remove later this shit nakhui

    override fun onTouchEvent(event: MotionEvent) =
        gestureDetector.onTouchEvent(event).takeIf { it }?.let { it } ?: super.onTouchEvent(event)


    // TODO double taps
    override fun onDoubleTap(p0: MotionEvent?): Boolean {
        Log.e("GESTURES", "onDoubleTap")

        return true
    }

    override fun onDoubleTapEvent(p0: MotionEvent?): Boolean {
        Log.e("GESTURES", "onDoubleTapEvent")

        return true
    }

    override fun onSingleTapConfirmed(p0: MotionEvent?): Boolean {
        Log.e("GESTURES", "onSingleTapConfirmed")

        return true
    }



    override fun onShowPress(p0: MotionEvent?) {
        Log.e("GESTURES", "onShowPress")
    }

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        Log.e("GESTURES", "onSingleTapUp")

        valueAnimator?.takeIf { it.isRunning }?.cancel()

        return true
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        Log.e("GESTURES", "onDown")

        return true
    }

    override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        Log.e("GESTURES", "onFling $p2")

        valueAnimator?.takeIf { it.isRunning }?.apply {
            val oldFlingEnd = finishValue == 0
            val newFlingEnd = p2 > 0

            if ((oldFlingEnd && newFlingEnd) || (!oldFlingEnd && !newFlingEnd)) {
                duration /= 2
            } else {
                cancel()
            }
        } ?: startOnFlingAnimation(p2)

        return true
    }

    override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        Log.e("GESTURES", "onScroll, $p2")
        return true
    }

    override fun onLongPress(p0: MotionEvent?) {
        Log.e("GESTURES", "onLongPress")
    }

    private var valueAnimator: ValueAnimator? = null
    private var finishValue: Int = 0

    private fun startOnFlingAnimation(flingPower: Float) {
        if (flingPower.absoluteValue > 7_000F) {
            finishValue = flingPower.takeIf { it > 0 }?.let { 0 } ?: let {
                columnBytes.size - currentColumnsCount
            }

            valueAnimator = ValueAnimator.ofInt(firstVisibleColumn, finishValue).apply {
                // TODO change this value to ratio with volume columns
                duration = 2_500L
                addUpdateListener { animation -> firstVisibleColumn = (animation.animatedValue as Int) }
                start()
            }

        } else return
    }





    private fun getTimeAndPosition(position: Int, result: (String, Float, Float) -> Unit) {
        columnBytes.takeIf { it.isNotEmpty() }?.let {
            val timeText = getTimeText(position + firstVisibleColumn)

            timeTextPaint.run {
                getTextBounds(timeText, ZERO_SIZE, timeText.length, textRect)

                var xPos =
                    position * columnWidth + position * spacingBetweenColumns + halfOfHistogramTopPadding

                val rightSlideBarPos = rightSlideBar * (columnWidth + spacingBetweenColumns)
                val textWidth = xPos + textRect.right.toFloat() * 2

                val isLeftSlideBar = position == leftSlideBar && textWidth >= rightSlideBarPos
                val isRightSlideBar = position == rightSlideBar && textWidth >= fWidth

                if (isLeftSlideBar || isRightSlideBar) {
                    xPos -= textRect.right * 2 + halfOfHistogramTopPadding
                }

                result(timeText, xPos, (halfOfHistogramTopPadding - (descent() + ascent()) / 2F))
            }
        }
    }

    private fun getTimeText(position: Int): String {
        val millis = soundDuration / (columnBytes.size.takeIf { it != ZERO_SIZE } ?: 1) * position
        val second = millis / 1_000 % 60
        val minute = millis / 60_000 % 60

        return String.format(MINUTES_TWO_SYMBOLS_TIME_FORMAT.takeIf { minute > 10 }
            ?: MINUTES_ONE_SYMBOL_TIME_FORMAT, minute, second)
    }

    private fun getElementsSizes(w: Int, h: Int) {
        fWidth = w.toFloat()
        fHeight = h.toFloat()

        histogramTopPadding = fHeight * histogramTopPaddingRatio

        columnWidth =
            fWidth / (currentColumnsCount + (currentColumnsCount - 1) * columnSpacingRatio)

        columnRadius = columnWidth / 2F

        spacingBetweenColumns = columnWidth * columnSpacingRatio

        histogramBackgroundRectF = RectF(ZERO_SIZE_F, histogramTopPadding, fWidth, fHeight)

        halfOfHistogramTopPadding = histogramTopPadding / 2

        val heightWithTopPadding = fHeight - histogramTopPadding

        histogramHeight = heightWithTopPadding - heightWithTopPadding * columnVerticalPaddingRatio

        histogramYAxis =
            histogramHeight / 2F + histogramTopPadding + fHeight * columnVerticalPaddingRatio / 2F

        columns.clear()

        val heightMin = histogramHeight / UByte.MAX_VALUE.toFloat()

        columnBytes.forEach { uByte ->
            val halfOfColumnHeight = (heightMin * uByte.toFloat() - histogramTopPadding) / 2F

            columns.add(
                ColumnSize(
                    histogramYAxis - halfOfColumnHeight, histogramYAxis + halfOfColumnHeight
                )
            )
        }
    }

    private fun <T> T.getIfAndInvalidate(
        value: T?,
        predicate: () -> Boolean,
        receiver: (T) -> Unit
    ) =
        value?.takeIf { this != value && predicate() }?.let {
            receiver(value)
            invalidate()
        } ?: Unit

    private fun <T> T.getIfNewAndInvalidate(value: T?, receiver: (T) -> Unit) = value?.let {
        if (this != value) {
            receiver(value)
            invalidate()
        }
    } ?: Unit

    data class ColumnSize(var top: Float, var bottom: Float)
}
