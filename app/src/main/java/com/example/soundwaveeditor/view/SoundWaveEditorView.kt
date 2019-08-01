package com.example.soundwaveeditor.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.soundwaveeditor.R


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private const val DEFAULT_MIN_COLUMNS_COUNT_BETWEEN_SLIDE_BARS = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 100
        private const val DEFAULT_MIN_COLUMNS_COUNT = 20
        private const val DEFAULT_COLUMNS_COUNT = 50
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_COLUMNS_RATIO = 1F
    }

    private val histogramTopPaddingRatioRange = 0F..2F
    private val verticalPaddingRatioRange = 0F..0.5F
    private val columnsRatioRange = 0F..1F

    private var histogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var spacingBetweenColumns = DEFAULT_COLUMNS_RATIO
    private var columnWidth = DEFAULT_COLUMNS_RATIO

    private var histogramBackgroundPaint: Paint? = null
    private var inactiveColumnsPaint: Paint? = null
    private var activeColumnsPaint: Paint? = null
    private var slideBarPaint: Paint? = null

    var histogramBackgroundColor = android.R.color.transparent
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            histogramBackgroundPaint?.color = it
        }

    var inactiveColumnsColor = android.R.color.darker_gray
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            inactiveColumnsPaint?.color = it
        }

    var activeColumnsColor = android.R.color.holo_red_light
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            activeColumnsPaint?.color = it
        }

    var slideBarsColor = android.R.color.holo_red_light
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            slideBarPaint?.color = it
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

    var minColumnsCountBetweenSlideBars = DEFAULT_MIN_COLUMNS_COUNT_BETWEEN_SLIDE_BARS
        set(value) = field.getIfAndInvalidate(value, { value in 1 until currentColumnsCount }, {
            field = it
        })

    var needToRoundColumns = false
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
        }

    var isColumnsMirroredOnY = false
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
            0 <= value && value <= volumeColumns.size
        }) {
            field = it
        }

    var volumeColumns = mutableListOf<UByte>()
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
        }

    var leftSlideBar = 0
        set(value) = field.getIfAndInvalidate(value, {
            value + minColumnsCountBetweenSlideBars < rightSlideBar
        }) {
            field = it
        }

    var rightSlideBar = DEFAULT_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, {
            value - minColumnsCountBetweenSlideBars > leftSlideBar
        }) {
            field = it
        }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SoundWaveEditorView, 0, 0).apply {
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

            minColumnsCountBetweenSlideBars = getInt(
                R.styleable.SoundWaveEditorView_minColumnsCountBetweenSlideBars,
                DEFAULT_MIN_COLUMNS_COUNT_BETWEEN_SLIDE_BARS
            )

            needToRoundColumns =
                getBoolean(R.styleable.SoundWaveEditorView_needToRoundColumns, false)

            isColumnsMirroredOnY =
                getBoolean(R.styleable.SoundWaveEditorView_isColumnsMirroredOnY, false)

            columnSpacingRatio =
                getFloat(R.styleable.SoundWaveEditorView_columnSpacingRatio, DEFAULT_COLUMNS_RATIO)

            columnVerticalPaddingRatio =
                getFloat(
                    R.styleable.SoundWaveEditorView_columnVerticalPaddingRatio,
                    DEFAULT_VERTICAL_PADDING_RATIO
                )
        }.recycle()

        histogramBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = histogramBackgroundColor
        }

        activeColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = activeColumnsColor
        }

        inactiveColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = inactiveColumnsColor
        }

        slideBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = slideBarsColor
        }
    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        getElementsSizes()

        histogramBackgroundPaint?.let { paint -> drawRect(getHistogramBackgroundRectF(), paint) }

        val radius = columnWidth / 2F

        volumeColumns.subList(firstVisibleColumn, firstVisibleColumn + currentColumnsCount)
            .forEachIndexed { index, uByte ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> inactiveColumnsPaint
                }?.let { paint ->
                    val rectF = getRectF(index, uByte, isSlideBar)

                    when (needToRoundColumns) {
                        true -> drawRoundRect(rectF, radius, radius, paint)
                        false -> drawRect(rectF, paint)
                    }
                }
            }
    }

    private fun getHistogramBackgroundRectF() = RectF(
        0F, histogramTopPadding, width.toFloat(), height.toFloat()
    )

    private fun getRectF(position: Int, volumeLevel: UByte, isSlideBar: Boolean = false): RectF {
        val left = position * columnWidth + position * spacingBetweenColumns

        val heightWithPadding = height - height * columnVerticalPaddingRatio

        val columnHeight = when (isSlideBar) {
            true -> height.toFloat()
            false -> heightWithPadding / UByte.MAX_VALUE.toFloat() * volumeLevel.toFloat()
        }

        val halfOfViewHeight = heightWithPadding / 2F
        val halfOfColumnHeight = columnHeight / 2F

        return when (isColumnsMirroredOnY) {
            true -> RectF(
                left,
                halfOfViewHeight - halfOfColumnHeight,
                left + columnWidth,
                halfOfViewHeight + halfOfColumnHeight
            )
            false -> RectF(
                left,
                heightWithPadding - columnHeight,
                left + columnWidth,
                heightWithPadding
            )
        }
    }

    private fun getElementsSizes() {
        histogramTopPadding = height * histogramTopPaddingRatio
        columnWidth = width / (currentColumnsCount + (currentColumnsCount - 1) * columnSpacingRatio)
        spacingBetweenColumns = columnWidth * columnSpacingRatio
    }

    private fun <T> T.getIfNew(value: T?, receiver: (T) -> Unit) = value?.let {
        if (this != value) receiver(value)
    } ?: Unit

    private fun <T> T.getIf(value: T?, predicate: () -> Boolean, receiver: (T) -> Unit) =
        value?.takeIf { this != value && predicate() }?.let { receiver(value) } ?: Unit

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
