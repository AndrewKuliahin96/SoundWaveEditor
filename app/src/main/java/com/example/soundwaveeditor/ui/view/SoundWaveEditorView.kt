package com.example.soundwaveeditor.ui.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.soundfile.CheapSoundFile
import com.example.soundwaveeditor.soundfile.SongMetadataReader
import kotlinx.android.parcel.Parcelize
import java.io.File
import java.io.RandomAccessFile
import java.io.Serializable
import java.lang.ref.WeakReference
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.min
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
        private const val ZERO_SIZE_L = 0L
        private const val DEFAULT_MAX_TRIM_LENGTH_IN_SEC = 10
        private const val DEFAULT_MIN_TRIM_LENGTH_IN_SEC = 0
        private const val DEFAULT_SLIDE_BARS_PADDING = 10
        private const val DEFAULT_MAX_COLUMNS_COUNT = 500
        private const val DEFAULT_MIN_COLUMNS_COUNT = 100
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

    private var histogramBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()

    private var inactiveColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()

    private var activeColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()

    private var slideBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()

    private var timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill().apply {
        textSize = DEFAULT_TIME_TEXT_SIZE
    }

    private var histogramBackgroundRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)

    private val drawingRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)

    private val textRect = Rect(ZERO_SIZE, ZERO_SIZE, ZERO_SIZE, ZERO_SIZE)

    private val columns = mutableListOf<ColumnSize>()

    // TODO incapsulate all fields
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

    var soundDuration = ZERO_SIZE_L
        set(value) = field.getIfAndInvalidate(value, { value > 0L }, { field = it })

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
        }) { field = it }

    // TODO add processing for variant when rightSlideBar - leftSlideBar !in minTrimSize..maxTrimSize
    var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            value in DEFAULT_SLIDE_BARS_PADDING..rightSlideBar - distanceBetweenSlideBarsInColumns
        }) { field = it }

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

    var columnBytes = mutableListOf<UByte>()
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var fileName: String? = null
        set(value) = field.getIfNew(value) {
            it?.let { file -> processAudioFile(file) }
            field = it
        }

    var soundFile: CheapSoundFile? = null
        set(value) = field.getIfNew(value) { field = it }

    var soundData: SoundData? = null
        set(value) = field.getIfNew(value) { field = it }

    var currentPlayTimeMs = 0L
        set(value) = field.getIfNew(value) {
            field = it
            playingPosition = (it * columnBytes.size / soundDuration).toInt()
        }

    private var playingPosition = ZERO_SIZE
        set(value) {
            field = value
            invalidate()
        }

    // TODO replace this callback by interface
    val updatingCallback = { time: Long ->
        currentPlayTimeMs += time
    }

    init {
        // TODO test HA performance and if it low -> remove this 4 fuckin lines below
        // TODO also remove @android:hardwareAccelerated="true" in manifest
//        setLayerType(LAYER_TYPE_HARDWARE, slideBarPaint)
//        setLayerType(LAYER_TYPE_HARDWARE, activeColumnsPaint)
//        setLayerType(LAYER_TYPE_HARDWARE, inactiveColumnsPaint)
//        setLayerType(LAYER_TYPE_HARDWARE, timeTextPaint)

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

        // TODO to create a value animator for play position escorting
        val initCenter = firstVisibleColumn + currentVisibleColumnsCount / 2

        if (playingPosition in initCenter - 1 .. initCenter + 1) {
            firstVisibleColumn++
        }

        columns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, column ->
                var isSlideBar = false

                when (index) {
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    leftSlideBar, rightSlideBar -> slideBarPaint.apply { isSlideBar = true }
                    else -> {
                        if (index + firstVisibleColumn == playingPosition) {
                            slideBarPaint.apply { isSlideBar = true }
                        } else {
                            inactiveColumnsPaint
                        }
                    }
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

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            position = 0F
            movin = NO_MOVE
            isScaling = false
        }

        if (!isScaling) {
            val columnWidthAndSpacing = columnWidth + spacingBetweenColumns
            val leftSlideBarPosition = leftSlideBar * columnWidthAndSpacing
            val rightSlideBarPosition = rightSlideBar * columnWidthAndSpacing

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    movin = when (event.x) {
                        in 0F..leftSlideBarPosition - 10F, in rightSlideBarPosition + 10F..fWidth -> MOVE_SLIDE
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
            }
        }

        return eventResult
    }

    override fun onScaleBegin(scaleDetector: ScaleGestureDetector?) =
        (scaleDetector?.let { it.currentSpanX > it.currentSpanY } ?: false).also { isScaling = it }

    // TODO create simple scale listener
    override fun onScaleEnd(scaleDetector: ScaleGestureDetector?) = Unit

    private var scaleAnimator: ValueAnimator? = null

    // TODO optimize this shitty implementation
    private fun centerScale(from: Int, to: Int, animDuration: Long) {
        scaleAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = animDuration

            addUpdateListener {
                it.animatedValue.takeIfType<Int> { value ->
                    firstVisibleColumn = value
                }
            }

            start()
        }
    }

    // TODO impl this to save view state
//    override fun onSaveInstanceState() = Bundle().apply {
//        putParcelable(SUPER_STATE, super.onSaveInstanceState())
//        putParcelable( IntRange)

//        private var scaleDetector: ScaleGestureDetector
//
//        private val histogramTopPaddingRatioRange = 0F..2F
//        private val verticalPaddingRatioRange = 0F..0.5F
//        private val columnsRatioRange = 0F..1F
//
//        private var histogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
//        private var halfOfHistogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
//        private var spacingBetweenColumns = DEFAULT_COLUMNS_RATIO
//        private var columnWidth = DEFAULT_COLUMNS_RATIO
//        private var columnRadius = ZERO_SIZE_F
//
//        private var fWidth = ZERO_SIZE_F
//        private var fHeight = ZERO_SIZE_F
//
//        private var histogramYAxis = ZERO_SIZE_F
//        private var histogramHeight = ZERO_SIZE_F
//
//        private var position = 0F
//        private var movin = NO_MOVE
//        private var isScaling = false
//
//        private var histogramBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
//
//        private var inactiveColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
//
//        private var activeColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
//
//        private var slideBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
//
//        private var timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill().apply {
//            textSize = DEFAULT_TIME_TEXT_SIZE
//        }
//
//        private var histogramBackgroundRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)
//
//        private val drawingRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)
//
//        private val textRect = Rect(ZERO_SIZE, ZERO_SIZE, ZERO_SIZE, ZERO_SIZE)
//
//        private val columns = mutableListOf<ColumnSize>()
//
//        // TODO incapsulate all fields
//        var timeTextSize = DEFAULT_TIME_TEXT_SIZE
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            timeTextPaint.textSize = it
//        }
//
//        var timeTextColor = android.R.color.black
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            timeTextPaint.color = it
//        }
//
//        var histogramBackgroundColor = android.R.color.transparent
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            histogramBackgroundPaint.color = it
//        }
//
//        var inactiveColumnsColor = android.R.color.darker_gray
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            inactiveColumnsPaint.color = it
//        }
//
//        var activeColumnsColor = android.R.color.holo_red_light
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            activeColumnsPaint.color = it
//        }
//
//        var slideBarsColor = android.R.color.holo_red_light
//        set(value) = field.getIfNewAndInvalidate(value) {
//            field = it
//            slideBarPaint.color = it
//        }
//
//        var maxVisibleColumnsCount = DEFAULT_MAX_COLUMNS_COUNT
//        set(value) = field.getIfAndInvalidate(value, { value >= currentVisibleColumnsCount }, {
//            field = it
//        })
//
//        var minVisibleColumnsCount = DEFAULT_MIN_COLUMNS_COUNT
//        set(value) = field.getIfAndInvalidate(value, { value < currentVisibleColumnsCount }, {
//            field = it
//        })
//
//        var currentVisibleColumnsCount = DEFAULT_COLUMNS_COUNT
//        set(value) = field.getIfAndInvalidate(
//            value, { value in (minVisibleColumnsCount + 1) until maxVisibleColumnsCount }, { field = it })
//
//        var distanceBetweenSlideBarsInColumns = DEFAULT_SLIDE_BARS_PADDING
//        set(value) = field.getIfAndInvalidate(value, { value in 1 until currentVisibleColumnsCount }, {
//            field = it
//        })
//
//        var soundDuration = ZERO_SIZE_L
//        set(value) = field.getIfAndInvalidate(value, { value > 0L }, { field = it })
//
//        var needToRoundColumns = false
//        set(value) = field.getIfNewAndInvalidate(value) { field = it }
//
//        var histogramTopPaddingRatio = DEFAULT_HISTOGRAM_TOP_PADDING
//        set(value) = field.getIfAndInvalidate(value, { value in histogramTopPaddingRatioRange }, {
//            field = it
//        })
//
//        var columnSpacingRatio = DEFAULT_COLUMNS_RATIO
//        set(value) = field.getIfAndInvalidate(value, { value in columnsRatioRange }, { field = it })
//
//        var columnVerticalPaddingRatio = DEFAULT_VERTICAL_PADDING_RATIO
//        set(value) = field.getIfAndInvalidate(value, { value in verticalPaddingRatioRange }, {
//            field = it
//        })
//
//        var firstVisibleColumn = ZERO_SIZE
//        set(value) = field.getIfAndInvalidate(value, {
//            value in 0..columnBytes.size - currentVisibleColumnsCount
//        }) { field = it }
//
//        // TODO add processing for variant when rightSlideBar - leftSlideBar !in minTrimSize..maxTrimSize
//        var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
//        set(value) = field.getIfAndInvalidate(value, {
//            value in DEFAULT_SLIDE_BARS_PADDING..rightSlideBar - distanceBetweenSlideBarsInColumns
//        }) { field = it }
//
//        // TODO add processing for variant when rightSlideBar - leftSlideBar !in minTrimSize..maxTrimSize
//        var rightSlideBar = DEFAULT_COLUMNS_COUNT - DEFAULT_SLIDE_BARS_PADDING
//        set(value) = field.getIfAndInvalidate(value, {
//            value in leftSlideBar + distanceBetweenSlideBarsInColumns - 1 until currentVisibleColumnsCount - DEFAULT_SLIDE_BARS_PADDING
//        }) {
//            field = when (it == currentVisibleColumnsCount) {
//                true -> it - 1
//                false -> it
//            }
//        }
//
//        var minTrimLengthInSeconds = DEFAULT_MIN_TRIM_LENGTH_IN_SEC
//        set(value) = field.getIfAndInvalidate(value, {
//            value in 1 until maxTrimLengthInSeconds
//        }) { field = it }
//
//        var maxTrimLengthInSeconds = DEFAULT_MAX_TRIM_LENGTH_IN_SEC
//        set(value) = field.getIfAndInvalidate(value, {
//            value in (minTrimLengthInSeconds + 1) until soundDuration
//        }) { field = it }
//
//        var columnBytes = mutableListOf<UByte>()
//        set(value) = field.getIfNewAndInvalidate(value) { field = it }
//
//        var fileName: String? = null
//        set(value) = field.getIfNew(value) {
//            it?.let { file -> processAudioFile(file) }
//            field = it
//        }
//
//        var soundFile: CheapSoundFile? = null
//        set(value) = field.getIfNew(value) { field = it }
//
//        var soundData: SoundData? = null
//        set(value) = field.getIfNew(value) { field = it }
//
//        var currentPlayTimeMs = 0L
//        set(value) = field.getIfNew(value) {
//            field = it
//            playingPosition = (it * columnBytes.size / soundDuration).toInt()
//        }
//
//        private var playingPosition = ZERO_SIZE
//        set(value) {
//            field = value
//            invalidate()
//        }
//
//        // TODO replace this callback by interface
//        val updatingCallback = { time: Long ->
//            Log.e("UPD TIME", "$time")
//
//            // TODO replace by 100L
//            currentPlayTimeMs += time
//        }

//    }

    // TODO and this
    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)


    }

    private inline fun <reified T> Any?.takeIfType(onCasted: (T) -> Unit): Any? {
        if (this is T) onCasted(this as T)

        return this
    }

    override fun onScale(scaleDetector: ScaleGestureDetector?): Boolean {
        var result = false

//        val oldFirstVisible = firstVisibleColumn
//        val oldVisibleColumnsCount = currentVisibleColumnsCount

        // TODO fix non-scaling state



        scaleDetector?.let { detector ->
            (currentVisibleColumnsCount / detector.scaleFactor.absoluteValue).toInt().takeIf {
                firstVisibleColumn + it <= columns.size
            }?.let {
                currentVisibleColumnsCount = it

                getWidthAndSpacing()
                invalidate()

                result = true
            }

//            if (firstVisibleColumn + currentVisibleColumnsCount <= columns.size - 10) {
//                currentVisibleColumnsCount = (currentVisibleColumnsCount / detector.scaleFactor.absoluteValue).toInt()
//                getWidthAndSpacing()
//                invalidate()
//
//                result = true
//            }

//            if (scaleAnimator?.isRunning != true) {
//                val newCenter = (it.focusX / (columnWidth + spacingBetweenColumns)).toInt()
//                val oldCenter = oldFirstVisible + oldVisibleColumnsCount / 2
//
//                val newFirstVisible = when (oldFirstVisible + newCenter > oldCenter) {
//                    true -> oldFirstVisible + (oldVisibleColumnsCount / 2 + newCenter)
//                    false -> oldFirstVisible - (oldVisibleColumnsCount / 2 - newCenter)
//                }
//
//                centerScale(oldFirstVisible, newFirstVisible, 100L)
//            }
        }

        return result
    }

    // TODO remove later
    var loadedCallback: ((Boolean) -> Unit)? = null

    fun trimAudio() {
        thread {
            fileName?.split("/")?.last()?.dropLastWhile { it != '.' }?.let { title ->
                Log.e("TRIM AUDIO", "created temp, $title")

                makeSoundFileName("$title (trimmed)", ".m4a")?.let { sfName ->
                    var outPath: String? = sfName

                    outPath?.let {
                        var outFile = File(it)
                        var fallbackToWAV = false

                        try {
                            soundFile?.trimAudioFile(outFile, getPositionTime(leftSlideBar), getPositionTime(rightSlideBar))
                        } catch (e: Exception) {
                            Log.e("TRIM AUDIO", "failed, ${e.message}")

                            outFile.takeIf { of -> of.exists() }?.delete()

                            fallbackToWAV = true
                        }

                        if (fallbackToWAV) {
                            outPath = makeSoundFileName("$title (trimmed)", ".wav")

                            outFile = File(it)

                            try {
                                soundFile?.trimAudioFile(outFile, getPositionTime(leftSlideBar), getPositionTime(rightSlideBar))
                            } catch (e: Exception) {
                                Log.e("TRIM AUDIO", "failed, ${e.message}")

                                outFile.takeIf { of -> of.exists() }?.delete()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun makeSoundFileName(title: String?, extension: String) =
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.path?.let {
            val file = File( if (!it.endsWith("/")) "$it/" else it)

            file.mkdirs()

            val fileName = StringBuilder()

            title?.forEach { char ->
                if (Character.isLetterOrDigit(char)) {
                    fileName.append(char)
                }
            }

            it + fileName + System.currentTimeMillis() + extension
        }

    private fun getPositionTime(position: Int) =
        soundDuration / (columnBytes.size.takeIf { it != 0 }?.toLong() ?: 1L) * position

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

        val heightMin = histogramHeight / UByte.MAX_VALUE.toFloat()

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

    // TODO To vals
    var loadingKeepGoing = false
    var loadingProgress = 0
    var numFramesSF = 0

    private fun processAudioFile(fileName: String) {
        val path = File(fileName).absolutePath

        var loadingLastUpdateTime = System.currentTimeMillis()

        loadingKeepGoing = true

        soundFile = CheapSoundFile.create(path, object : CheapSoundFile.Companion.ProgressListener {
            override fun reportProgress(fractionComplete: Double): Boolean {
                val now = System.currentTimeMillis()
                if (now - loadingLastUpdateTime > 100) {
                    loadingProgress = (100 * fractionComplete).toInt()

                    loadingLastUpdateTime = now
                }

                return loadingKeepGoing
            }
        })


        val level = mutableListOf<UByte>()

        soundFile?.run {
            (numFrames * samplesPerFrame / sampleRate).let { soundDuration = it * 1_000L }

            numFramesSF = numFrames

            getMinMax()?.let {
                for (i in 0..numFrames) {
                    level.add((calculateHeight(i, it.first, it.second) * UByte.MAX_VALUE.toInt()).toUInt().toUByte())
                }

                // TODO avg of 10 frames will be drawn on view
//                columnBytes = level.chunked(10).map { list -> list.map { mapped -> mapped.toInt() }.average() }.map { it.toUInt().toUByte() }.toMutableList()

                // TODO every frame will be drawn on view
                // TODO this may slow drawing speed

                columnBytes = level

                maxVisibleColumnsCount = columnBytes.size / 5
                currentVisibleColumnsCount = columnBytes.size / 5 - 100
                minVisibleColumnsCount = 200

                Log.e("COLUMN BYTES", "${columnBytes.size}")
            }

            SongMetadataReader(WeakReference(context), path).run {
                soundData = SoundData(
                    path, title, artist, album, year, filetype, sampleRate, avgBitrateKbps
                )
            }

            updatePeriod = getUpdatePeriod()

            loadedCallback?.invoke(true)
        }
    }

    var updatePeriod: Long? = 0L

    private fun getUpdatePeriod() =
        soundDuration / (columnBytes.size.takeIf { it != ZERO_SIZE } ?: 1)

    private fun getMinMax(): Pair<Float, Float>? {
        soundFile?.let { file ->
            var minGain = 0F
            var maxGain = 0F
            val gainHist = IntArray(256)

            for (j in 0 until numFramesSF) {
                val gain = getGain(j, numFramesSF, file.frameGains).toInt()
                val smoothGain = if (gain < 0) 0 else if (gain > 255) 255 else gain

                if (smoothGain > maxGain)
                    maxGain = smoothGain.toFloat()

                gainHist[smoothGain]++
            }

            var sum = 0

            while (minGain < 255 && sum < numFramesSF / 20) {
                sum += gainHist[minGain.toInt()]
                minGain++
            }

            return minGain to maxGain
        } ?: return null
    }

    private fun calculateHeight(i: Int, minGain: Float, maxGain: Float): Float {
        soundFile?.let { file ->
            return getHeight(i, numFramesSF, file.frameGains, minGain, maxGain - minGain)
        } ?: return 0F
    }

    private fun getHeight(i: Int, numFrames: Int, frameGains: IntArray, minGain: Float, range: Float): Float {
        val value = (getGain(i, numFrames, frameGains) - minGain) / range

        return when {
            value < 0.0 -> 0F
            value > 1.0 -> 1f
            else -> value
        }
    }

    private fun getGain(i: Int, numFrames: Int, frameGains: IntArray): Float {
        val x = min(i, numFrames - 1)

        return if (numFrames < 2) {
            frameGains[x].toFloat()
        } else {
            when (x) {
                0 -> frameGains[0] / 2.0f + frameGains[1] / 2.0f
                numFrames - 1 -> frameGains[numFrames - 2] / 2.0f + frameGains[numFrames - 1] / 2.0f
                else -> frameGains[x - 1] / 3.0f + frameGains[x] / 3.0f + frameGains[x + 1] / 3.0f
            }
        }
    }

    private fun Paint.styleFill() = apply { style = Paint.Style.FILL }

    private fun <T> T.getIfNew(value: T?, predicate: () -> Boolean = { true }, receiver: (T) -> Unit) =
        value?.takeIf { this != it && predicate() }?.let { receiver(it) } ?: Unit

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
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val fileType: String? = null,
        val sampleRate: Int? = null,
        val averageBitrate: Int? = null
    )

    private data class ColumnSize(var top: Float, var bottom: Float)
}


