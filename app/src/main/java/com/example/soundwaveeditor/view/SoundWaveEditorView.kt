package com.example.soundwaveeditor.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.soundwaveeditor.R


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    companion object {
        private const val DEFAULT_SLIDE_BARS_PADDING = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 100
        private const val DEFAULT_MIN_COLUMNS_COUNT = 20
        private const val DEFAULT_COLUMNS_COUNT = 50
        private const val DEFAULT_TIME_TEXT_SIZE = 14F
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_COLUMNS_RATIO = 1F
        private const val DEFAULT_ZERO_SIZE = 0F
        private const val ZERO_SOUND_DURATION = 0
        private const val MINUTES_TWO_SYMBOLS_TIME_FORMAT = "%02d:%02d"
        private const val MINUTES_ONE_SYMBOL_TIME_FORMAT = "%01d:%02d"
    }

    private val histogramTopPaddingRatioRange = 0F..2F
    private val verticalPaddingRatioRange = 0F..0.5F
    private val columnsRatioRange = 0F..1F

    private var histogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var halfOfHistogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var spacingBetweenColumns = DEFAULT_COLUMNS_RATIO
    private var columnWidth = DEFAULT_COLUMNS_RATIO
    private var columnRadius = DEFAULT_ZERO_SIZE

    private var fWidth = DEFAULT_ZERO_SIZE
    private var fHeight = DEFAULT_ZERO_SIZE

    private var histogramXAxis = DEFAULT_ZERO_SIZE
    private var histogramHeight = DEFAULT_ZERO_SIZE

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

    private var histogramBackgroundRectF = RectF(0F, 0F, 0F, 0F)
    private val textRect = Rect(0, 0, 0, 0)

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

    var columns = mutableListOf<Float>()

    var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in DEFAULT_SLIDE_BARS_PADDING until rightSlideBar - slideBarsPaddingInColumns
        }) {
            field = it
        }

    var rightSlideBar = DEFAULT_COLUMNS_COUNT - DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in leftSlideBar + slideBarsPaddingInColumns..currentColumnsCount - DEFAULT_SLIDE_BARS_PADDING
        }) {
            field = when (it == currentColumnsCount) {
                true -> it - 1
                false -> it
            }
        }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SoundWaveEditorView, 0, 0).apply {
            timeTextSize = getDimension(
                R.styleable.SoundWaveEditorView_timeTextSize,
                DEFAULT_TIME_TEXT_SIZE
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
                R.styleable.SoundWaveEditorView_maxColumnsCount,
                DEFAULT_MAX_COLUMNS_COUNT
            )

            minColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_minColumnsCount,
                DEFAULT_MIN_COLUMNS_COUNT
            )

            currentColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_currentColumnsCount,
                DEFAULT_COLUMNS_COUNT
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

        histogramBackgroundPaint.color = histogramBackgroundColor
        activeColumnsPaint.color = activeColumnsColor
        inactiveColumnsPaint.color = inactiveColumnsColor
        slideBarPaint.color = slideBarsColor
        timeTextPaint.apply {
            color = timeTextColor
            textSize = timeTextSize
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        fWidth = w.toFloat()
        fHeight = h.toFloat()

        getElementsSizes()
        getColumnsHeight()
    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        drawRect(histogramBackgroundRectF, histogramBackgroundPaint)

        val lastVisibleItem = firstVisibleColumn + currentColumnsCount

        columns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, halfColumnHeight ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> inactiveColumnsPaint
                }.let { paint ->
                    val rectF = getRectF(index, halfColumnHeight, isSlideBar)

                    when (needToRoundColumns) {
                        true -> drawRoundRect(rectF, columnRadius, columnRadius, paint)
                        false -> drawRect(rectF, paint)
                    }
                }
            }

        getTimeAndPosition(leftSlideBar) { text, x, y -> drawText(text, x, y, timeTextPaint) }
        getTimeAndPosition(rightSlideBar) { text, x, y -> drawText(text, x, y, timeTextPaint) }
    }

    private fun getRectF(
        position: Int,
        halfColumnHeight: Float,
        isSlideBar: Boolean = false
    ): RectF {
        val left = position * columnWidth + position * spacingBetweenColumns
        val right = left + columnWidth

        return when (isSlideBar) {
            true -> RectF(left, halfOfHistogramTopPadding, right, fHeight)
            false -> RectF(
                left,
                histogramXAxis - halfColumnHeight,
                right,
                histogramXAxis + halfColumnHeight
            )
        }
    }

    private fun getTimeAndPosition(position: Int, result: (String, Float, Float) -> Unit) {
        columnBytes.takeIf { it.isNotEmpty() }?.let {
            val timeText = getTimeText(position + firstVisibleColumn)

            timeTextPaint.run {
                getTextBounds(timeText, 0, timeText.length, textRect)

                var xPos =
                    position * columnWidth + position * spacingBetweenColumns + halfOfHistogramTopPadding

                val rightSlideBarPos =
                    rightSlideBar * columnWidth + rightSlideBar * spacingBetweenColumns

                when (position) {
                    leftSlideBar -> {
                        if (xPos + textRect.right.toFloat() >= rightSlideBarPos) {
                            xPos -= textRect.right * 2 + halfOfHistogramTopPadding
                        }
                    }
                    rightSlideBar -> {
                        if (xPos + textRect.right.toFloat() >= fWidth) {
                            xPos -= textRect.right * 2 + halfOfHistogramTopPadding
                        }
                    }
                }

                result(timeText, xPos, (halfOfHistogramTopPadding - (descent() + ascent()) / 2F))
            }
        }
    }

    private fun getTimeText(position: Int): String {
        val millis = soundDuration / (columnBytes.size.takeIf { it != 0 } ?: 1) * position
        val second = millis / 1_000 % 60
        val minute = millis / 60_000 % 60

        return String.format(MINUTES_TWO_SYMBOLS_TIME_FORMAT.takeIf { minute > 10 }
            ?: MINUTES_ONE_SYMBOL_TIME_FORMAT, minute, second)
    }

    private fun getElementsSizes() {
        histogramTopPadding = fHeight * histogramTopPaddingRatio

        columnWidth =
            fWidth / (currentColumnsCount + (currentColumnsCount - 1) * columnSpacingRatio)

        columnRadius = columnWidth / 2F

        spacingBetweenColumns = columnWidth * columnSpacingRatio

        histogramBackgroundRectF = RectF(DEFAULT_ZERO_SIZE, histogramTopPadding, fWidth, fHeight)

        halfOfHistogramTopPadding = histogramTopPadding / 2

        val heightWithTopPadding = fHeight - histogramTopPadding

        histogramHeight =
            heightWithTopPadding - heightWithTopPadding * columnVerticalPaddingRatio

        histogramXAxis =
            histogramHeight / 2F + histogramTopPadding + fHeight * columnVerticalPaddingRatio / 2F
    }

    private fun getColumnsHeight() {
        columns.clear()

        columnBytes.forEach { uByte ->
            val columnHeight =
                histogramHeight / UByte.MAX_VALUE.toFloat() * uByte.toFloat() - histogramTopPadding

            columns.add(columnHeight / 2F)
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
}
