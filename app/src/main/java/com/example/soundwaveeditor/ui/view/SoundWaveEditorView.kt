package com.example.soundwaveeditor.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.soundwaveeditor.R


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs),
    ScaleGestureDetector.OnScaleGestureListener {

    companion object {
        private const val MINUTES_TWO_SYMBOLS_TIME_FORMAT = "%02d:%02d"
        private const val MINUTES_ONE_SYMBOL_TIME_FORMAT = "%01d:%02d"
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_TIME_TEXT_SIZE = 14F
        private const val DEFAULT_COLUMNS_RATIO = 1F
        private const val ZERO_SIZE_F = 0F
        private const val ZERO_SIZE = 0
        private const val DEFAULT_MAX_TRIM_LENGTH_IN_SEC = 10
        private const val DEFAULT_MIN_TRIM_LENGTH_IN_SEC = 0
        private const val DEFAULT_SLIDE_BARS_PADDING = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 500
        private const val DEFAULT_MIN_COLUMNS_COUNT = 200
        private const val DEFAULT_COLUMNS_COUNT = 300
        private const val NO_MOVE = -1
        private const val MOVE_SLIDE = 2
        private const val MOVE_LEFT = 4
        private const val MOVE_RIGHT = 8
        private const val MOVE_CENTER = 16
    }

    private var scaleDetector: ScaleGestureDetector

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

    private var position = 0F
    private var movin = NO_MOVE
    private var isScaling = false

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

    var maxVisibleColumnsCount = DEFAULT_MAX_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, { value >= currentVisibleColumnsCount }, {
            field = it
        })

    var minVisibleColumnsCount = DEFAULT_MIN_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, { value < currentVisibleColumnsCount }, {
            field = it
        })

    var currentVisibleColumnsCount = DEFAULT_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(
            value, { value in (minVisibleColumnsCount + 1) until maxVisibleColumnsCount }, { field = it })

    var distanceBetweenSlideBarsInColumns = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in 1 until currentVisibleColumnsCount }, {
            field = it
        })

    var soundDuration = ZERO_SIZE
        set(value) = field.getIfAndInvalidate(value, { value > 0 }, { field = it })

    var needToRoundColumns = false
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var histogramTopPaddingRatio = DEFAULT_HISTOGRAM_TOP_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in histogramTopPaddingRatioRange }, {
            field = it
        })

    var columnSpacingRatio = DEFAULT_COLUMNS_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in columnsRatioRange }, { field = it })

    var columnVerticalPaddingRatio = DEFAULT_VERTICAL_PADDING_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in verticalPaddingRatioRange }, {
            field = it
        })

    var firstVisibleColumn = ZERO_SIZE
        set(value) = field.getIfAndInvalidate(value, {
            value in 0..columnBytes.size - currentVisibleColumnsCount
        }) {
            field = it
        }

    // TODO add processing for variant when rightSlideBar - leftSlideBar !in minTrimSize..maxTrimSize
    var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in DEFAULT_SLIDE_BARS_PADDING..rightSlideBar - distanceBetweenSlideBarsInColumns
        }) {
            field = it
        }

    // TODO add processing for variant when rightSlideBar - leftSlideBar !in minTrimSize..maxTrimSize
    var rightSlideBar = DEFAULT_COLUMNS_COUNT - DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in leftSlideBar + distanceBetweenSlideBarsInColumns - 1 until currentVisibleColumnsCount - DEFAULT_SLIDE_BARS_PADDING
        }) {
            field = when (it == currentVisibleColumnsCount) {
                true -> it - 1
                false -> it
            }
        }

    var minTrimLengthInSeconds = DEFAULT_MIN_TRIM_LENGTH_IN_SEC
        set(value) = field.getIfAndInvalidate(value, {
            value in 1 until maxTrimLengthInSeconds
        }) { field = it }

    var maxTrimLengthInSeconds = DEFAULT_MAX_TRIM_LENGTH_IN_SEC
        set(value) = field.getIfAndInvalidate(value, {
            value in (minTrimLengthInSeconds + 1) until soundDuration
        }) { field = it }

    var columnBytes = mutableListOf<Byte>()
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

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

            maxVisibleColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_maxVisibleColumnsCount,
                DEFAULT_MAX_COLUMNS_COUNT
            )

            minVisibleColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_minVisibleColumnsCount,
                DEFAULT_MIN_COLUMNS_COUNT
            )

            currentVisibleColumnsCount = getInt(
                R.styleable.SoundWaveEditorView_currentVisibleColumnsCount,
                DEFAULT_COLUMNS_COUNT
            )

            maxTrimLengthInSeconds = getInt(
                R.styleable.SoundWaveEditorView_maxTrimLengthInSeconds,
                DEFAULT_MAX_TRIM_LENGTH_IN_SEC
            )

            minTrimLengthInSeconds = getInt(
                R.styleable.SoundWaveEditorView_minTrimLengthInSeconds,
                DEFAULT_MIN_TRIM_LENGTH_IN_SEC
            )

            distanceBetweenSlideBarsInColumns = getInt(
                R.styleable.SoundWaveEditorView_slideBarsPaddingInColumns,
                DEFAULT_SLIDE_BARS_PADDING
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

        scaleDetector = ScaleGestureDetector(context, this)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        getElementsSizes(width, height)
    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        drawRect(histogramBackgroundRectF, histogramBackgroundPaint)

        val lastVisibleItem = firstVisibleColumn + currentVisibleColumnsCount

        columns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, column ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> inactiveColumnsPaint
                }.let { paint ->
                    val leftColumnSide = index * (columnWidth + spacingBetweenColumns)

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val eventResult = scaleDetector.onTouchEvent(event)

        if (!isScaling) {
            val columnWidthAndSpacing = columnWidth + spacingBetweenColumns
            val leftSlideBarPosition = leftSlideBar * columnWidthAndSpacing
            val rightSlideBarPosition = rightSlideBar * columnWidthAndSpacing

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    movin = when (event.x) {
                        in 0F..leftSlideBarPosition - 10F, in rightSlideBarPosition + 10F..width.toFloat() -> MOVE_SLIDE
                        in leftSlideBarPosition - 10F..leftSlideBarPosition + 10F -> MOVE_LEFT
                        in rightSlideBarPosition - 10F..rightSlideBarPosition + 10F -> MOVE_RIGHT
                        in leftSlideBarPosition + 10F..rightSlideBarPosition - 10F -> MOVE_CENTER
                        else -> NO_MOVE
                    }

                    position = event.x
                }

                MotionEvent.ACTION_MOVE -> {
                    ((event.x - position) / (columnWidth + spacingBetweenColumns)).toInt().let {
                        when (movin) {
                            MOVE_SLIDE -> firstVisibleColumn -= it
                            MOVE_LEFT -> leftSlideBar += it
                            MOVE_RIGHT -> rightSlideBar += it
                            MOVE_CENTER -> {
                                leftSlideBar += it
                                rightSlideBar += it
                            }
                        }
                    }

                    position = event.x
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    position = 0F
                    movin = NO_MOVE
                }

                else -> Unit
            }
        }

        return eventResult
    }

    override fun onScaleBegin(scaleDetector: ScaleGestureDetector?): Boolean {
        isScaling = true
        return true
    }

    override fun onScaleEnd(scaleDetector: ScaleGestureDetector?) {
        isScaling = false
    }

    override fun onScale(scaleDetector: ScaleGestureDetector?): Boolean {
        scaleDetector?.scaleFactor?.takeIf { it < 1F }?.let {
            currentVisibleColumnsCount += 5

            while(firstVisibleColumn + currentVisibleColumnsCount >= columns.size - 10) {
                firstVisibleColumn -= 5
            }
        } ?: let { currentVisibleColumnsCount -= 5 }

        getWidthAndSpacing()
        invalidate()

        return true
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

        getWidthAndSpacing()

        histogramBackgroundRectF = RectF(ZERO_SIZE_F, histogramTopPadding, fWidth, fHeight)
        halfOfHistogramTopPadding = histogramTopPadding / 2

        val heightWithTopPadding = fHeight - histogramTopPadding

        histogramHeight = heightWithTopPadding - heightWithTopPadding * columnVerticalPaddingRatio
        histogramYAxis = histogramHeight / 2F + histogramTopPadding + fHeight * columnVerticalPaddingRatio / 2F

        initColumns()
    }

    private fun initColumns() {
        columns.clear()

        val heightMin = histogramHeight / Byte.MAX_VALUE.toFloat()

        columnBytes.forEach { short ->
            val halfOfColumnHeight = (heightMin * short.toFloat() - histogramTopPadding) / 2F

            columns.add(
                ColumnSize(
                    histogramYAxis - halfOfColumnHeight,
                    histogramYAxis + halfOfColumnHeight
                )
            )
        }
    }

    private fun getWidthAndSpacing() {
        columnWidth = fWidth / (currentVisibleColumnsCount + (currentVisibleColumnsCount - 1) * columnSpacingRatio)
        columnRadius = columnWidth / 2F
        spacingBetweenColumns = columnWidth * columnSpacingRatio
    }

    private fun <T> T.getIfNew(value: T?, predicate: () -> Boolean = { true }, receiver: (T) -> Unit) =
        value?.takeIf { this != it && predicate() }?.let {
            receiver(it)
        } ?: Unit

    private fun <T> T.getIfAndInvalidate(value: T?, predicate: () -> Boolean, receiver: (T) -> Unit) =
        value?.takeIf { this != it && predicate() }?.let {
            receiver(it)
            invalidate()
        } ?: Unit

    private fun <T> T.getIfNewAndInvalidate(value: T?, receiver: (T) -> Unit) =
        value?.takeIf { this != it }?.let {
            receiver(it)
            invalidate()
        } ?: Unit

    data class SoundData(
        val filePath: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: Int?,
        val fileType: String?,
        val sampleRate: Int?,
        val averageBitrate: Int?
    )

    private data class ColumnSize(var top: Float, var bottom: Float)
}
