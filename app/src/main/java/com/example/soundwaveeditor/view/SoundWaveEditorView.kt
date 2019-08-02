package com.example.soundwaveeditor.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.soundwaveeditor.R
import android.graphics.Rect
import android.util.Log


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private const val DEFAULT_MIN_COLUMNS_COUNT_BETWEEN_SLIDE_BARS = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 100
        private const val DEFAULT_MIN_COLUMNS_COUNT = 20
        private const val DEFAULT_COLUMNS_COUNT = 50
        private const val DEFAULT_TIME_TEXT_SIZE = 14F
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_COLUMNS_RATIO = 1F
        private const val ZERO_SOUND_DURATION = 0
        private const val MINUTES_TWO_SYMBOLS_TIME_FORMAT = "%02d:%02d"
        private const val MINUTES_ONE_SYMBOL_TIME_FORMAT = "%01d:%02d"
    }

    // TODO optimize drawing time (it's too slowly)

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
    private var timeTextPaint: Paint? = null

    private val textRect = Rect(0, 0, 0, 0)

    var timeTextSize = DEFAULT_TIME_TEXT_SIZE
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            timeTextPaint?.textSize = it
        }

    var timeTextColor = android.R.color.black
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            timeTextPaint?.color = it
        }

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

            minColumnsCountBetweenSlideBars = getInt(
                R.styleable.SoundWaveEditorView_minColumnsCountBetweenSlideBars,
                DEFAULT_MIN_COLUMNS_COUNT_BETWEEN_SLIDE_BARS
            )

            soundDuration = getInt(
                R.styleable.SoundWaveEditorView_soundDuration,
                ZERO_SOUND_DURATION
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

        timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = timeTextColor
            textSize = timeTextSize
        }
    }

    // TODO Maybe it would good idea to do size measurements in this method and save results for each column
    // TODO it release onDraw method from large calculations
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Log.e("onSizeChanged", "w: $w, h: $h")
    }

//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//
////        Log.e("ON MEASURE", "w: $widthMeasureSpec, h: $heightMeasureSpec")
//
//        Log.e("ON MEASURE", "w: $width, h: $height")
//    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        getElementsSizes()

        histogramBackgroundPaint?.let { paint -> drawRect(getHistogramBackgroundRectF(), paint) }

        val columnRadius = columnWidth / 2F

        // TODO fix this shit (firstVisibleColumn + currentColumnsCount can be greater than columns count)
        val lastVisibleItem = firstVisibleColumn + currentColumnsCount

        volumeColumns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, uByte ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> inactiveColumnsPaint
                }?.let { paint ->
                    val rectF = getRectF(index, uByte, isSlideBar)

                    when (needToRoundColumns) {
                        true -> drawRoundRect(rectF, columnRadius, columnRadius, paint)
                        false -> drawRect(rectF, paint)
                    }
                }
            }

        // TODO fix time it related to column position in view, not in data set
        if (volumeColumns.isNotEmpty()) {
            val leftText = getTimeText(leftSlideBar + firstVisibleColumn)
            val rightText = getTimeText(rightSlideBar + firstVisibleColumn)

            timeTextPaint?.let {
                it.getTextBounds(leftText, 0, leftText.length, textRect)

                val xPos = getTextXAxis(leftSlideBar)
                val yPos = (histogramTopPadding / 2 - (it.descent() + it.ascent()) / 2F)

                drawText(leftText, xPos, yPos, it)

                it.getTextBounds(rightText, 0, rightText.length, textRect)

                val xPos1 = getTextXAxis(rightSlideBar)
                val yPos1 = (histogramTopPadding / 2 - (it.descent() + it.ascent()) / 2F)

                drawText(rightText, xPos1, yPos1, it)
            }
        }
    }

    private fun getTextXAxis(position: Int) =
        position * columnWidth + position * spacingBetweenColumns + histogramTopPadding / 2

    private fun getTimeText(position: Int): String {
        // TODO refactor, move to const
        val millis = soundDuration / (volumeColumns.size.takeIf { it != 0 } ?: 1) * position
        val second = millis / 1000 % 60
        val minute = millis / (1000 * 60) % 60

        return String.format(MINUTES_TWO_SYMBOLS_TIME_FORMAT.takeIf { minute > 10 }
            ?: MINUTES_ONE_SYMBOL_TIME_FORMAT, minute, second)
    }

    // TODO get and save float width and height values once
    private fun getHistogramBackgroundRectF() =
        RectF(0F, histogramTopPadding, width.toFloat(), height.toFloat())

    private fun getRectF(position: Int, volumeLevel: UByte, isSlideBar: Boolean = false): RectF {
        val left = position * columnWidth + position * spacingBetweenColumns
        val right = left + columnWidth

        val heightWithTopPadding = height - histogramTopPadding

        val histogramHeight =
            heightWithTopPadding - heightWithTopPadding * columnVerticalPaddingRatio

        val columnHeight =
            histogramHeight / UByte.MAX_VALUE.toFloat() * volumeLevel.toFloat() - histogramTopPadding

        val histogramXAxis =
            histogramHeight / 2F + histogramTopPadding + height * columnVerticalPaddingRatio / 2F

        val halfOfColumnHeight = columnHeight / 2F

        return when (isSlideBar) {
            true -> RectF(
                left,
                histogramTopPadding / 2,
                right,
                height.toFloat()
            )
            false -> RectF(
                left,
                histogramXAxis - halfOfColumnHeight,
                right,
                histogramXAxis + halfOfColumnHeight
            )
        }
    }

    private fun getElementsSizes() {
        histogramTopPadding = height * histogramTopPaddingRatio
        columnWidth = width / (currentColumnsCount + (currentColumnsCount - 1) * columnSpacingRatio)
        spacingBetweenColumns = columnWidth * columnSpacingRatio
    }


    // TODO maybe need to remove unused
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
