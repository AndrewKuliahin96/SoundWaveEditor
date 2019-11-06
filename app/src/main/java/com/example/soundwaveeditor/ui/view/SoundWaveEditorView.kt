package com.example.soundwaveeditor.ui.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.extensions.getColorCompat
import com.example.soundwaveeditor.extensions.pxToDp
import com.example.soundwaveeditor.soundfile.CheapSoundFile
import com.example.soundwaveeditor.soundfile.SongMetadataReader
import com.example.soundwaveeditor.utils.SimpleScaleListener
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import java.io.File
import java.lang.ref.WeakReference
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.min


@ExperimentalUnsignedTypes
class SoundWaveEditorView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private const val MINUTES_TWO_SYMBOLS_TIME_FORMAT = "%02d:%02d"
        private const val MINUTES_ONE_SYMBOL_TIME_FORMAT = "%01d:%02d"
        private const val DEFAULT_VERTICAL_PADDING_RATIO = 0.25F
        private const val DEFAULT_HISTOGRAM_TOP_PADDING = 0.1F
        private const val DEFAULT_TIME_TEXT_SIZE = 14F
        private const val DEFAULT_COLUMNS_RATIO = 1F
        private const val ZERO_SIZE_F = 0F
        private const val ZERO_SIZE_L = 0L
        private const val ZERO_SIZE = 0
        private const val DEFAULT_MAX_TRIM_LENGTH_IN_SEC = 15
        private const val DEFAULT_MIN_TRIM_LENGTH_IN_SEC = 5
        private const val DEFAULT_FIXED_CHUNKS_STRATEGY = 10
        private const val TOUCH_SQUARE_PLACEHOLDER_DP = 8F
        private const val DEFAULT_SLIDE_BARS_PADDING = 100
        private const val DEFAULT_MAX_COLUMNS_COUNT = 1_000
        private const val DEFAULT_MIN_COLUMNS_COUNT = 200
        private const val DEFAULT_COLUMNS_COUNT = 500
        private const val MOVE_CENTER = 16
        private const val MOVE_RIGHT = 8
        private const val MOVE_LEFT = 4
        private const val MOVE_SLIDE = 2
        private const val NO_MOVE = -1

        interface LoadingProgressListener {
            fun onLoading(percentsLoaded: Int)
            fun onLoaded(soundFile: SoundData)
            fun onLoadingError(ex: Exception)
        }

        interface PlayingListener {
            fun onPlay(timeMs: Long? = null)
            fun onSeek(timeMs: Long)
            fun onPause(timeMs: Long)
            fun onStop()
        }

        interface TrimmingListener {
            fun onTrimStart(startMs: Long, endMs: Long)
            fun onTrimEnd()
            fun onTrimError(ex: Exception)
        }
    }

    private var escortingAnimator: ValueAnimator? = null
    private val scaleDetector: ScaleGestureDetector

    private val histogramTopPaddingRatioRange = 0F..2F
    private val verticalPaddingRatioRange = 0F..0.5F
    private val columnsRatioRange = 0F..1F

    private var zoomLevelCorrection = TOUCH_SQUARE_PLACEHOLDER_DP
    private var histogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var halfOfHistogramTopPadding = DEFAULT_HISTOGRAM_TOP_PADDING
    private var spacingBetweenColumns = DEFAULT_COLUMNS_RATIO
    private var columnWidth = DEFAULT_COLUMNS_RATIO
    private var columnRadius = ZERO_SIZE_F

    private var fWidth = ZERO_SIZE_F
    private var fHeight = ZERO_SIZE_F

    private var histogramYAxis = ZERO_SIZE_F
    private var histogramHeight = ZERO_SIZE_F

    private var tapCoordinateX = ZERO_SIZE_F
    private var position = ZERO_SIZE_F
    private var movin = NO_MOVE
    private var isScaling = false

    private val histogramBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
    private val inactiveColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
    private val activeColumnsPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
    private val playBarColumnPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
    private val slideBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill()
    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).styleFill().apply {
        textSize = DEFAULT_TIME_TEXT_SIZE
    }

    private var histogramBackgroundRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)
    private var drawingRectF = RectF(ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F, ZERO_SIZE_F)

    private var textRect = Rect(ZERO_SIZE, ZERO_SIZE, ZERO_SIZE, ZERO_SIZE)

    private var columns = mutableListOf<ColumnSize>()

    private var leftSlideBar = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            checkSlideBars(value, rightSlideBar) && value in 1 until rightSlideBar - distanceBetweenSlideBarsInColumns
        }) { field = it }

    private var rightSlideBar = DEFAULT_COLUMNS_COUNT - DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, {
            checkSlideBars(leftSlideBar, value) && when (columns.size) {
                0 -> true
                else -> value in leftSlideBar + distanceBetweenSlideBarsInColumns until columns.size
            }
        }) { field = it }

    private var columnBytes = mutableListOf<UByte>()
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    private var soundFile: CheapSoundFile? = null
        set(value) = field.getIfNew(value) { field = it }

    private var currentPlayTimeMs = 0L
        set(value) = field.getIfNew(value) {
            field = it

            if (needToPlayInSlideBars) {
                (it * columnBytes.size / soundDuration).toInt().takeIf { newPlayPos -> newPlayPos in leftSlideBar .. rightSlideBar }?.let { smth ->
                    playingPosition = (it * columnBytes.size / soundDuration).toInt()
                } ?: let {
                    // TODO seekTo callback with time of right slide bar

                    Log.e("OUT", "cllback() inv")
                }
            } else {
                playingPosition = (it * columnBytes.size / soundDuration).toInt()
            }
        }

    private var playingPosition = ZERO_SIZE
        set(value) {
            field = value
            invalidate()
        }

    private var loadingKeepGoing = false
    private var numFramesSF = 0

    var loadingProgressListener: LoadingProgressListener? = null
    var trimmingListener: TrimmingListener?= null
    var playingListener: PlayingListener? = null

    var chunkGroupingStrategy = ChunkGroupingStrategy.GROUPING_AVERAGE
    var chunkingStrategy = ChunkingStrategy.CHUNKING_AUTO

    var fixedChunksStrategy = DEFAULT_FIXED_CHUNKS_STRATEGY

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

    var playBarColor = android.R.color.holo_red_light
        set(value) = field.getIfNewAndInvalidate(value) {
            field = it
            playBarColumnPaint.color = it
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
        set(value) = field.getIfAndInvalidate(value, { value >= currentVisibleColumnsCount }) {
            field = it
        }

    var minVisibleColumnsCount = DEFAULT_MIN_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(value, { value < currentVisibleColumnsCount }) {
            field = it
        }

    var currentVisibleColumnsCount = DEFAULT_COLUMNS_COUNT
        set(value) = field.getIfAndInvalidate(
            value, { value in (minVisibleColumnsCount + 1) until maxVisibleColumnsCount }) {
            field = it
        }

    var distanceBetweenSlideBarsInColumns = DEFAULT_SLIDE_BARS_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in 1 until currentVisibleColumnsCount }) {
            field = it
        }

    var soundDuration = ZERO_SIZE_L
        set(value) = field.getIfAndInvalidate(value, { value > 0L }) { field = it }

    var needToEscortPlayPosition = false
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var needToRoundColumns = false
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var needToShowTime = false
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var needToPlayInSlideBars = false
        set(value) = field.getIfNewAndInvalidate(value) { field = it }

    var histogramTopPaddingRatio = DEFAULT_HISTOGRAM_TOP_PADDING
        set(value) = field.getIfAndInvalidate(value, { value in histogramTopPaddingRatioRange }) {
            field = it
        }

    var columnSpacingRatio = DEFAULT_COLUMNS_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in columnsRatioRange }) { field = it }

    var columnVerticalPaddingRatio = DEFAULT_VERTICAL_PADDING_RATIO
        set(value) = field.getIfAndInvalidate(value, { value in verticalPaddingRatioRange }) {
            field = it
        }

    var firstVisibleColumn = ZERO_SIZE
        set(value) = field.getIfAndInvalidate(value, {
            value in 0..columnBytes.size - currentVisibleColumnsCount
        }) { field = it }

    var minTrimLengthInSeconds = DEFAULT_MIN_TRIM_LENGTH_IN_SEC
        set(value) = field.getIfAndInvalidate(value, { value in 1 until maxTrimLengthInSeconds }) {
            field = it
        }

    var maxTrimLengthInSeconds = DEFAULT_MAX_TRIM_LENGTH_IN_SEC
        set(value) = field.getIfAndInvalidate(value, {
            value > minTrimLengthInSeconds
        }) { field = it }

    var soundData: SoundData? = null
        set(value) = field.getIfNew(value) { field = it }

    var inputFilePath: String? = null
        set(value) = field.getIfNewAndInvalidate(value) { field = value }

    var outputFilePath: String? = null
        set(value) = field.getIfNewAndInvalidate(value) { field = value }

    // TODO refactor all callbacks
    // TODO replace this callback by interface
    val updatingCallback = { time: Long ->
        currentPlayTimeMs += time
    }

    var seekingCallback: ((Int) -> Unit)? = null

    var updatePeriod: Long? = 0L

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SoundWaveEditorView, 0, 0).apply {
            timeTextSize = getDimension(
                R.styleable.SoundWaveEditorView_timeTextSize, DEFAULT_TIME_TEXT_SIZE
            )

            timeTextColor = getColor(
                R.styleable.SoundWaveEditorView_timeTextColor,
                context.getColorCompat(android.R.color.black)
            )

            histogramBackgroundColor = getColor(
                R.styleable.SoundWaveEditorView_histogramBackground,
                context.getColorCompat(android.R.color.transparent)
            )

            activeColumnsColor = getColor(
                R.styleable.SoundWaveEditorView_activeColumnsColor,
                context.getColorCompat(android.R.color.holo_red_light)
            )

            playBarColor = getColor(
                R.styleable.SoundWaveEditorView_playBarColor,
                context.getColorCompat(android.R.color.holo_red_light)
            )

            inactiveColumnsColor = getColor(
                R.styleable.SoundWaveEditorView_inactiveColumnsColor,
                context.getColorCompat(android.R.color.darker_gray)
            )

            slideBarsColor = getColor(
                R.styleable.SoundWaveEditorView_slideBarsColor,
                context.getColorCompat(android.R.color.holo_red_light)
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

            needToEscortPlayPosition =
                getBoolean(R.styleable.SoundWaveEditorView_needToEscortPlayPosition, false)

            needToRoundColumns =
                getBoolean(R.styleable.SoundWaveEditorView_needToRoundColumns, false)

            needToShowTime =
                getBoolean(R.styleable.SoundWaveEditorView_needToShowTime, false)

            needToPlayInSlideBars =
                getBoolean(R.styleable.SoundWaveEditorView_needToPlayInSlideBars, false)

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

        scaleDetector = ScaleGestureDetector(context, object : SimpleScaleListener() {
            override fun onScaleBegin(scaleDetector: ScaleGestureDetector?) =
                (scaleDetector?.let { it.currentSpanX > it.currentSpanY } ?: false).also {
                    isScaling = it
                }

            override fun onScale(scaleDetector: ScaleGestureDetector?) = scaleDetector?.let { detector ->
                val newCurrentColumnsCount = (currentVisibleColumnsCount / detector.scaleFactor.absoluteValue).toInt()

                if (firstVisibleColumn + newCurrentColumnsCount > columns.size) {
                    firstVisibleColumn += currentVisibleColumnsCount - newCurrentColumnsCount
                }

                currentVisibleColumnsCount = newCurrentColumnsCount

                getWidthAndSpacing()
                invalidate()

                return true
            } ?: false
        })
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        getElementsSizes(width, height)
    }

    override fun onDraw(canvas: Canvas) = canvas.run {
        drawRect(histogramBackgroundRectF, histogramBackgroundPaint)

        val lastVisibleItem = firstVisibleColumn + currentVisibleColumnsCount

        if (needToEscortPlayPosition) {
            checkPositionEscorting()
        }

        columns.takeIf { it.size >= lastVisibleItem }
            ?.subList(firstVisibleColumn, lastVisibleItem)
            ?.forEachIndexed { index, column ->
                var isSlideBar = false
                val absoluteIndexPosition = index + firstVisibleColumn

                when (absoluteIndexPosition) {
                    playingPosition -> playBarColumnPaint.apply { isSlideBar = true }
                    in leftSlideBar + 1 until rightSlideBar -> activeColumnsPaint
                    else -> if (absoluteIndexPosition == leftSlideBar || absoluteIndexPosition == rightSlideBar) {
                        slideBarPaint.apply { isSlideBar = true }
                    } else {
                        inactiveColumnsPaint
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

        if (needToShowTime) {
            setOf(playingPosition - firstVisibleColumn, leftSlideBar - firstVisibleColumn, rightSlideBar - firstVisibleColumn).forEach {
                getTimeAndPosition(it) { text, x, y -> drawText(text, x, y, timeTextPaint) }
            }
        }
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
            val columnWidthDp = columnWidth.toDp()
            val columnWidthAndSpacing = columnWidth + spacingBetweenColumns
            val leftSlideBarPositionDp = ((leftSlideBar - firstVisibleColumn) * columnWidthAndSpacing).toDp()
            val rightSlideBarPositionDp = ((rightSlideBar - firstVisibleColumn) * columnWidthAndSpacing).toDp()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val rawTapCoordinateXdP = event.x.toDp()

                    movin = when (rawTapCoordinateXdP) {
                        in 0F .. leftSlideBarPositionDp.minusCorrection(), in rightSlideBarPositionDp + columnWidthDp.plusCorrection() .. fWidth.toDp() -> MOVE_SLIDE
                        in leftSlideBarPositionDp.plusCorrection() .. rightSlideBarPositionDp.minusCorrection() -> MOVE_CENTER
                        else -> {
                            val leftSlideBarDpRange =
                                (leftSlideBarPositionDp.minusCorrection() .. leftSlideBarPositionDp + columnWidthDp.plusCorrection())

                            val rightSlideBarDpRange =
                                (rightSlideBarPositionDp.minusCorrection() .. rightSlideBarPositionDp + columnWidthDp.plusCorrection())

                            when {
                                leftSlideBarDpRange.covers(rawTapCoordinateXdP.minusCorrection(), rawTapCoordinateXdP.plusCorrection()) -> MOVE_LEFT
                                rightSlideBarDpRange.covers(rawTapCoordinateXdP.minusCorrection(), rawTapCoordinateXdP.plusCorrection()) -> MOVE_RIGHT
                                else -> NO_MOVE
                            }
                        }
                    }

                    tapCoordinateX = rawTapCoordinateXdP
                    position = event.x
                }

                MotionEvent.ACTION_MOVE -> {
                    ((event.x - position) / columnWidthAndSpacing).toInt().let {
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

                MotionEvent.ACTION_UP -> {
                    val size = event.size * 100
                    val actionUpDp = event.x.toDp()
                    val isTap = tapCoordinateX in (actionUpDp - size .. actionUpDp + size)

                    if (movin == NO_MOVE && isTap) {
                        (event.x / columnWidthAndSpacing + firstVisibleColumn).toInt().let {
                            getTimeMsFromPosition(it).let { seekPos ->
                                seekingCallback?.invoke(seekPos.toInt())
                                currentPlayTimeMs = seekPos
                            }
                        }
                    }
                }
            }
        }

        return eventResult
    }

    fun trimAudio() = outputFilePath?.let {
        val outFile = File(it)

        require(outFile.exists()) { "Output file must not be null" }

        thread {
            try {
                val startTime = getTimeMsFromPosition(leftSlideBar)
                val endTime = getTimeMsFromPosition(rightSlideBar)

                trimmingListener?.onTrimStart(startTime, endTime)
                soundFile?.trimAudioFile(outFile, startTime, endTime)
                trimmingListener?.onTrimEnd()

                Log.e("TRIM AUDIO", "created, ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("TRIM AUDIO", "failed, ${e.message}")

                trimmingListener?.onTrimError(e)

                outFile.takeIf { of -> of.exists() }?.delete()
            }
        }
    }

    fun play(time: Long) {
        Log.e("Inner play", "with time, ok")

        playingListener?.onPlay(time)
    }

    fun play() {
        Log.e("Inner play", "with no params, ok")

        playingListener?.onPlay()
    }

    fun pause() {
        Log.e("Inner pause", "ok, timeMs: $currentPlayTimeMs")

        playingListener?.onPause(currentPlayTimeMs)
    }

    fun stop() {
        Log.e("Inner stop", "ok")

        playingListener?.onStop()
    }

    fun seek(timeMs: Long) {
        Log.e("Inner seek", "ok")

        playingListener?.onSeek(timeMs)
    }

    private fun checkPositionEscorting() {
        if (escortingAnimator?.isRunning != true && !isScaling && movin == NO_MOVE) {
            val initCenter = firstVisibleColumn + currentVisibleColumnsCount / 2

            if (playingPosition.toFloat() in initCenter - zoomLevelCorrection .. initCenter + zoomLevelCorrection) {
                firstVisibleColumn = playingPosition - currentVisibleColumnsCount / 2
            } else {
                startAnimator(firstVisibleColumn, firstVisibleColumn + (playingPosition - initCenter))
            }
        }
    }

    private fun startAnimator(startValue: Int, endValue: Int) {
        if (escortingAnimator == null) {
            escortingAnimator = ValueAnimator()
        }

        escortingAnimator?.apply {
            cancel()
            setIntValues(startValue, endValue)

            duration = (endValue - startValue).absoluteValue.toLong() * 3L

            addUpdateListener {
                (it.animatedValue as? Int)?.let { value ->
                    if (!isScaling && movin == NO_MOVE) {
                        firstVisibleColumn = value
                    }
                }
            }

            start()
        }
    }

    private fun getTimeMsFromPosition(position: Int) =
        soundDuration / getColumnBytesSizeOrOne() * position

    private fun getPositionFromTimeMs(time: Long) =
        ((getColumnBytesSizeOrOne() / soundDuration.toFloat()) * time).toInt()

    private fun getColumnBytesSizeOrOne() = columnBytes.size.takeIf { it != 0 }?.toLong() ?: 1L

    private fun getTimeAndPosition(position: Int, result: (String, Float, Float) -> Unit) {
        columnBytes.takeIf { it.isNotEmpty() }?.let {
            val timeText = getTimeText(position + firstVisibleColumn)

            timeTextPaint.run {
                getTextBounds(timeText, ZERO_SIZE, timeText.length, textRect)

                var xPos =
                    position * columnWidth + position * spacingBetweenColumns + halfOfHistogramTopPadding

                val rightSlideBarPos = (rightSlideBar - firstVisibleColumn) * (columnWidth + spacingBetweenColumns)
                val textWidth = xPos + textRect.right.toFloat() * 2

                val isLeftSlideBar = position == leftSlideBar - firstVisibleColumn && textWidth >= rightSlideBarPos
                val isRightSlideBar = position == rightSlideBar - firstVisibleColumn && textWidth >= fWidth

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
        zoomLevelCorrection = TOUCH_SQUARE_PLACEHOLDER_DP + columns.size / currentVisibleColumnsCount
    }

    fun processAudioFile() = inputFilePath?.let {
        require(File(it).exists()) { "Input file must not be null" }

        try {
            val path = File(it).absolutePath

            loadingKeepGoing = true

            soundFile = CheapSoundFile.create(path, object : CheapSoundFile.Companion.ProgressListener {
                override fun reportProgress(fractionComplete: Double): Boolean {
                    loadingProgressListener?.onLoading((100 * fractionComplete).toInt())

                    return loadingKeepGoing
                }
            })

            soundFile?.run {
                soundDuration = (numFrames * samplesPerFrame / sampleRate).toMs()
                numFramesSF = numFrames

                getMinMax()?.let { minMax ->
                    val level = mutableListOf<UByte>()

                    for (i in 0..numFrames) {
                        level.add((calculateHeight(i, minMax.first, minMax.second) * UByte.MAX_VALUE.toInt()).toUInt().toUByte())
                    }

                    columnBytes = when (chunkingStrategy) {
                        ChunkingStrategy.CHUNKING_FIXED -> level.chunkedUBytes(fixedChunksStrategy)
                        ChunkingStrategy.CHUNKING_AUTO -> level.chunkedUBytes(level.size / 1_000)
                        ChunkingStrategy.CHUNKING_NONE -> level
                    }

                    val minTrimLengthPos = getPositionFromTimeMs(minTrimLengthInSeconds.toMs())
                    val maxTrimLengthPos = getPositionFromTimeMs(maxTrimLengthInSeconds.toMs())

                    if (distanceBetweenSlideBarsInColumns !in minTrimLengthPos .. maxTrimLengthPos) {
                        distanceBetweenSlideBarsInColumns = minTrimLengthPos
                    }

                    val halfOfTrimSec = (minTrimLengthInSeconds + maxTrimLengthInSeconds) * 500

                    rightSlideBar = getPositionFromTimeMs(getTimeMsFromPosition(leftSlideBar) + halfOfTrimSec)
                }

                SongMetadataReader(WeakReference(context), path).run {
                    soundData = SoundData(path, title, artist, album, year, fileType, sampleRate, avgBitrateKbps)
                }

                updatePeriod = getUpdatingPeriod()

                soundData?.let { sd -> loadingProgressListener?.onLoaded(sd) }
            }
        } catch (ex: Exception) {
            loadingProgressListener?.onLoadingError(ex)
        }
    }

    private fun checkSlideBars(leftPos: Int, rightPos: Int) =
        if (movin == MOVE_CENTER) {
            true
        } else {
            (getTimeMsFromPosition(rightPos) - getTimeMsFromPosition(leftPos)).toSec() in minTrimLengthInSeconds..maxTrimLengthInSeconds
        }

    private fun getUpdatingPeriod() =
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

    private fun Int.toMs() = this * 1_000L
    private fun Long.toSec() = (this / 1_000).toInt()

    private fun Float.toDp() = context.pxToDp(this)

    private fun Float.plusCorrection() = plus(TOUCH_SQUARE_PLACEHOLDER_DP + zoomLevelCorrection)
    private fun Float.minusCorrection() = minus(TOUCH_SQUARE_PLACEHOLDER_DP - zoomLevelCorrection)

    private fun <T : Comparable<T>> ClosedRange<T>.covers(start: T, end: T) =
        start in this || end in this

    private fun MutableList<UByte>.chunkedUBytes(chunkSize: Int) =
        chunked(chunkSize)
            .map { list -> list.map { mapped -> mapped.toInt() }.group() }
            .map { average -> average.toUInt().toUByte() }
            .toMutableList()

    private fun Iterable<Int>.group(): Double = when (chunkGroupingStrategy) {
        ChunkGroupingStrategy.GROUPING_MIN -> min()?.toDouble() ?: first().toDouble()
        ChunkGroupingStrategy.GROUPING_MAX -> max()?.toDouble() ?: first().toDouble()
        ChunkGroupingStrategy.GROUPING_AVERAGE -> average()
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

    enum class ChunkingStrategy { CHUNKING_FIXED, CHUNKING_AUTO, CHUNKING_NONE; }
    enum class ChunkGroupingStrategy { GROUPING_MIN, GROUPING_MAX, GROUPING_AVERAGE; }

    interface StatefulModel : Parcelable

    @Parcelize
    data class ColumnSize(var top: Float, var bottom: Float) : StatefulModel

    @Parcelize
    data class SoundData(
        val filePath: String,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val fileType: String? = null,
        val sampleRate: Int? = null,
        val averageBitrate: Int? = null) : StatefulModel

    @Parcelize
    data class SavingModel(
        var histogramTopPadding: Float,
        var halfOfHistogramTopPadding: Float,
        var spacingBetweenColumns: Float,
        var columnWidth: Float,
        var columnRadius: Float,
        var viewWidth: Float,
        var viewHeight: Float,
        var histogramYAxis: Float,
        var histogramHeight: Float,
        var backgroundColor: Int,
        var activeColumnsColor: Int,
        var inactiveColumnsColor: Int,
        var playBarColumnColor: Int,
        var slideBarColor: Int,
        var timeTextColor: Int,
        var histogramBackgroundRectF: RectF,
        var drawingRectF: RectF,
        var textRect: Rect,
        var columns: MutableList<ColumnSize>,
        var timeTextSize: Float,
        var maxVisibleColumnsCount: Int,
        var minVisibleColumnsCount: Int,
        var currentVisibleColumnsCount: Int,
        var distanceBetweenSlideBarsInColumns: Int,
        var soundDuration: Long,
        var needToRoundColumns: Boolean = false,
        var needToShowTime: Boolean = false,
        var histogramTopPaddingRatio: Float,
        var columnSpacingRatio: Float,
        var columnVerticalPaddingRatio: Float,
        var firstVisibleColumn: Int,
        var leftSlideBar: Int,
        var rightSlideBar: Int,
        var minTrimLengthInSeconds: Int,
        var maxTrimLengthInSeconds: Int,
        var columnBytes: @RawValue MutableList<UByte>,
        var inputFilePath: String?,
        var outputFilePath: String?,
        var soundFile: @RawValue CheapSoundFile?,
        var soundData: SoundData?,
        var currentPlayTimeMs: Long,
        var playingPosition: Int,
        var updatePeriod: Long?,
        var numFramesSF: Int,
        var chunkingStrategy: ChunkingStrategy,
        var chunkGroupingStrategy: ChunkGroupingStrategy) : StatefulModel

    fun getModelState() = SavingModel(
        histogramTopPadding,
        halfOfHistogramTopPadding,
        spacingBetweenColumns,
        columnWidth,
        columnRadius,
        fWidth,
        fHeight,
        histogramYAxis,
        histogramHeight,
        histogramBackgroundColor,
        activeColumnsColor,
        inactiveColumnsColor,
        playBarColor,
        slideBarsColor,
        timeTextColor,
        histogramBackgroundRectF,
        drawingRectF,
        textRect,
        columns,
        timeTextSize,
        maxVisibleColumnsCount,
        minVisibleColumnsCount,
        currentVisibleColumnsCount,
        distanceBetweenSlideBarsInColumns,
        soundDuration,
        needToRoundColumns,
        needToShowTime,
        histogramTopPaddingRatio,
        columnSpacingRatio,
        columnVerticalPaddingRatio,
        firstVisibleColumn,
        leftSlideBar,
        rightSlideBar,
        minTrimLengthInSeconds,
        maxTrimLengthInSeconds,
        columnBytes,
        inputFilePath,
        outputFilePath,
        soundFile,
        soundData,
        currentPlayTimeMs,
        playingPosition,
        updatePeriod,
        numFramesSF,
        chunkingStrategy,
        chunkGroupingStrategy).apply {

        // TODO need to set listeners state PlayingListener.pause(time) etc

        Log.e("onRestoreState", "saving model: $this")
    }

    fun setModelState(model: SavingModel) = model.let { savedModel ->
        histogramTopPadding = savedModel.histogramTopPadding
        halfOfHistogramTopPadding = savedModel.halfOfHistogramTopPadding
        spacingBetweenColumns = savedModel.spacingBetweenColumns
        columnWidth = savedModel.columnWidth
        columnRadius = savedModel.columnRadius
        fWidth = savedModel.viewWidth
        fHeight = savedModel.viewHeight
        histogramYAxis = savedModel.histogramYAxis
        histogramHeight = savedModel.histogramHeight
        histogramBackgroundColor = savedModel.backgroundColor
        activeColumnsColor = savedModel.activeColumnsColor
        inactiveColumnsColor = savedModel.inactiveColumnsColor
        playBarColor = savedModel.playBarColumnColor
        slideBarsColor = savedModel.slideBarColor
        timeTextColor = savedModel.timeTextColor
        histogramBackgroundRectF = savedModel.histogramBackgroundRectF
        drawingRectF = savedModel.drawingRectF
        textRect = savedModel.textRect
        columns = savedModel.columns
        timeTextSize = savedModel.timeTextSize
        maxVisibleColumnsCount = savedModel.maxVisibleColumnsCount
        minVisibleColumnsCount = savedModel.minVisibleColumnsCount
        currentVisibleColumnsCount = savedModel.currentVisibleColumnsCount
        distanceBetweenSlideBarsInColumns = savedModel.distanceBetweenSlideBarsInColumns
        soundDuration = savedModel.soundDuration
        needToRoundColumns = savedModel.needToRoundColumns
        needToShowTime = savedModel.needToShowTime
        histogramTopPaddingRatio = savedModel.histogramTopPaddingRatio
        columnSpacingRatio = savedModel.columnSpacingRatio
        columnVerticalPaddingRatio = savedModel.columnVerticalPaddingRatio
        firstVisibleColumn = savedModel.firstVisibleColumn
        leftSlideBar = savedModel.leftSlideBar
        rightSlideBar = savedModel.rightSlideBar
        minTrimLengthInSeconds = savedModel.minTrimLengthInSeconds
        maxTrimLengthInSeconds = savedModel.maxTrimLengthInSeconds
        columnBytes = savedModel.columnBytes
        inputFilePath = savedModel.inputFilePath
        outputFilePath = savedModel.outputFilePath
        soundFile = savedModel.soundFile
        soundData = savedModel.soundData
        currentPlayTimeMs = savedModel.currentPlayTimeMs
        playingPosition = savedModel.playingPosition
        updatePeriod = savedModel.updatePeriod
        numFramesSF = savedModel.numFramesSF
        chunkingStrategy = savedModel.chunkingStrategy
        chunkGroupingStrategy = savedModel.chunkGroupingStrategy

        // TODO need to set listeners state PlayingListener.play(time) etc

        Log.e("onRestoreState", "restoring model: $savedModel")
    }
}
