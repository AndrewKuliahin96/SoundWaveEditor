package com.example.soundwaveeditor.ui.screens.picksound

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.soundwaveeditor.R
import android.util.Log
import androidx.lifecycle.Observer
import com.example.soundwaveeditor.extensions.safeLet
import com.example.soundwaveeditor.extensions.setClickListeners
import com.example.soundwaveeditor.extensions.setVisibility
import com.example.soundwaveeditor.ui.screens.base.BaseLifecycleFragment
import com.example.soundwaveeditor.ui.view.SoundWaveEditorView
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_pick_sound.*
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


@Suppress("SameParameterValue")
@ExperimentalUnsignedTypes
class PickSoundFragment : BaseLifecycleFragment<PickSoundViewModel>(), View.OnClickListener {

    override val viewModelClass = PickSoundViewModel::class.java
    override val layoutId = R.layout.fragment_pick_sound
    override val toolbarId = -1
    override val toolbarTitleId = -1
    override val backNavId = -1
    override val isHaveToolbar = false

    private var player: MediaPlayer? = null
    private var rxPermissions: Disposable? = null

    override fun observeLiveData() {
        viewModel.errorLiveData.observe(this, Observer {
            logE("error occurred, ${it.toString()}")
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            rxPermissions = RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        comeOn()
                    } else {
                        Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
                    }
                }

            setClickListeners(bPlay, bPause, bStop)
        }

        logE("onViewCreated")
    }

    override fun onStop() {
        super.onStop()
        logE("onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()

        logE("onDestroyView")

        rxPermissions?.dispose()
    }

    private fun logE(message: String) {
        Log.e("PICK SOUND FR", message)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.bPlay -> vSoundEditor.play()
            R.id.bPause -> vSoundEditor.pause()
            R.id.bStop -> vSoundEditor.stop()
            else -> Unit
        }
    }

    private fun comeOn() {
        showAlert("Do u to wanna pick sound to process?", "Open audio", true,
            R.string.dialog_ok, {
                vSoundEditor.setVisibility(true)
                drawHistogram()
                bTrim.setOnClickListener {
                    vSoundEditor.inputFilePath?.split("/")?.last()?.dropLastWhile { it != '.' }?.let { title ->
                        vSoundEditor.soundData?.fileType?.toLowerCase(Locale.getDefault())?.let { ext ->
                            trimAudio(title, ext)
                        }
                    }
                }
            }, R.string.dialog_cancel) { Toast.makeText(context, "Cancel", Toast.LENGTH_SHORT).show() }
    }

    override fun showAlert(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                                   positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        context?.let {
            AlertDialog.Builder(it).apply {
                setMessage(message)
                setCancelable(cancelable)
                title?.let { title -> setTitle(title) }
                setPositiveButton(positiveRes?.let { pos -> pos } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
                negativeRes?.let { neg -> setNegativeButton(neg) { _, _ -> negativeFun() } }
                show()
            }
        }
    }

    private fun trimAudio(title: String, ext: String) {
        safeLet(title, ext) { sfTitle, sfExt ->
            makeSoundFileName(sfTitle, sfExt)?.let {
                Log.e("TRIM AUDIO", "full: $it")

                vSoundEditor.outputFilePath = it
                vSoundEditor.trimAudio()
            }
        }
    }

    private fun makeSoundFileName(title: String?, extension: String) =
        context?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.path?.let {
            val externalPath = it.takeIf { it.endsWith("/") } ?: "$it/"

            File(if (it.endsWith("/")) it else "$it/").mkdirs().let { Log.e("MKDS", "r: $it") }

            "${externalPath}$title$extension".apply { File(it).mkdirs().let { r -> Log.e("MKDIRS", "res = $r") } }
        }

    @SuppressLint("SetTextI18n")
    private fun drawHistogram() {
        vSoundEditor.apply {
            val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                "/sdcard/Download/smrtdeath - Black castle (feat. New Jerzey Devil).mp3"      // Samsung
            } else {
                "/storage/sdcard1/Music/smrtdeath - Black castle (feat. New Jerzey Devil).mp3"  // Xiaomi
            }

            loadingProgressListener = object : SoundWaveEditorView.Companion.LoadingProgressListener {
                override fun onLoading(percentsLoaded: Int) {
                    Log.e("onLoading", "loaded: $percentsLoaded%")
                }

                override fun onLoaded(soundFile: SoundWaveEditorView.SoundData) = soundFile.let { sd ->
                    Log.e("onLoaded", "ok")

                    sd.averageBitrate?.let { tvBitrate.text = "Average bitrate: $it kbps" }
                    sd.sampleRate?.let { tvFrequency.text = "Sample rate: $it Hz"  }

                    soundDuration.takeIf { it > 0L }?.let {
                        val minutes = it / 1_000 / 60
                        val seconds = it / 1_000 % 60

                        tvDuration.text = "Duration: $minutes:$seconds"
                    }

                    tvInfo.text = "${sd.artist ?: "Unknown artist"} - " +
                            "${sd.title ?: "No track name"} (${sd.album ?: "Unknown album"}, " +
                            "${sd.year ?: "No year info"}) ${sd.fileType ?: "Unknown file type"}"
                }

                override fun onLoadingError(ex: Exception) {
                    Log.e("onLoadingError", "message: ${ex.message}")
                }
            }

            playingListener = object : SoundWaveEditorView.Companion.PlayingListener {
                override fun onPlay(timeMs: Long?) {
                    if (timeMs == null) {
                        Log.e("onPlay", "TIME MS == null")
                    }

                    Log.e("onPlay", "ok")
                }

                override fun onSeek(timeMs: Long) {
                    Log.e("onSeek", "ok")
                }

                override fun onPause(timeMs: Long) {
                    Log.e("onPause", "ok")
                }

                override fun onStop() {
                    Log.e("onStop", "ok")
                }
            }

            trimmingListener  = object : SoundWaveEditorView.Companion.TrimmingListener {
                override fun onTrimStart(startMs: Long, endMs: Long) {
                    Log.e("onTrimStart", "ok")
                }

                override fun onTrimEnd() {
                    Log.e("onTrimEnd", "ok")
                }

                override fun onTrimError(ex: Exception) {
                    Log.e("onError", "message: ${ex.message}")
                }
            }

            inputFilePath = path
            processAudioFile()
            player = MediaPlayer()

            // TODO replace by s-thing non-depr or remove
            player?.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player?.setDataSource(context, Uri.parse(File(path).absolutePath))

            player?.setOnCompletionListener {
                Log.e("COMPLETED", "ok")
            }

            player?.setOnPreparedListener { p ->
                p.start()

                Log.e("PREPARED", "ok")

                seekingCallback = {
                    p.seekTo(it)

                    Log.e("SEEK", "$it")
                }

                updatePeriod?.let {
                    Flowable
                        .interval(it, TimeUnit.MILLISECONDS)
                        .takeWhile { p.isPlaying }
                        .timeInterval()
                        .onBackpressureLatest()
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ t ->
                            // TODO sync with media player position
                            updatingCallback(t.time())
                        }, { e ->
                            Log.e("ERROR", "${e.message}")
                        }, {
                            Log.e("COMPLETE", "ok")
                        })
                }
            }

            player?.prepareAsync()
        }
    }
}
