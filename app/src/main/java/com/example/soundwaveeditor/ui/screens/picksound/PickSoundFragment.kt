package com.example.soundwaveeditor.ui.screens.picksound

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.extensions.setVisibility
import com.example.soundwaveeditor.ui.screens.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_pick_sound.*
import kotlin.random.Random


@Suppress("SameParameterValue")
@ExperimentalUnsignedTypes
class PickSoundFragment : BaseFragment(LAYOUT_ID) {

    companion object {
        private const val LAYOUT_ID = R.layout.fragment_pick_sound

        fun getInstance(args: Bundle? = null) = PickSoundFragment().apply {
            args?.let { arguments = args }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        showAlert("Do u wanna pick sound to process?", "Open audio", true,
            R.string.dialog_ok, {
                vSoundEditor.setVisibility(true)
                drawHistogram()
            },
            R.string.dialog_cancel, {
                Toast.makeText(context, "Cancel", Toast.LENGTH_SHORT).show()
            })
    }

    private fun showAlert(message: CharSequence, dialogTitle: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                          positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        context?.let {
            AlertDialog.Builder(it).apply {
                setMessage(message)
                setCancelable(cancelable)
                dialogTitle?.let { title -> setTitle(title) }
                setPositiveButton(positiveRes?.let { pos -> pos } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
                negativeRes?.let { neg -> setNegativeButton(neg) { _, _ -> negativeFun() } }
                show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun drawHistogram() {
        vSoundEditor.apply {

            // TODO remove later: this thing not needed, cause we will get bytes from audio soundfile
//            columnBytes = getShorts(1_900).toMutableList()

            // Optional, but good practice as minVis... and current...
            maxVisibleColumnsCount = 800
            currentVisibleColumnsCount = 300

            // OK
            firstVisibleColumn = 0

            // TODO place in center? (need to talk about it)
            rightSlideBar = 150
            leftSlideBar = 50

            // TODO get sound duration from audio soundfile, that we pass into view
            soundDuration = 265_000
        }
    }
}
