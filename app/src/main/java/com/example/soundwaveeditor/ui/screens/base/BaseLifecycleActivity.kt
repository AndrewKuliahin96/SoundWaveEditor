package com.example.soundwaveeditor.ui.screens.base

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.extensions.*
import com.example.soundwaveeditor.extensions.show
import com.google.android.material.snackbar.Snackbar


@Suppress("LeakingThis, WrongConstant")
abstract class BaseLifecycleActivity<T : BaseViewModel>(layoutId: Int) : AppCompatActivity(layoutId), BaseView {

    companion object {
        private const val SNACK_BAR_DURATION = 5_000
        private const val SNACK_BAR_MAX_LINES = 5
        private const val SNACK_BAR_MAX_LINES_NOTIFICATION = 2
        private const val CAMERA_POSITION = 0
        private const val GALLERY_POSITION = 1
        const val NO_ID = -1
    }

    private var snackbar: Snackbar? = null

    protected abstract val viewModelClass: Class<T>

    protected abstract val containerId: Int

    protected abstract fun observeLiveData()

    private var progressView: FrameLayout? = null

    protected val viewModel: T by lazy {
        createViewModelFactory()?.let {
            ViewModelProviders.of(this, it).get(viewModelClass)
        } ?: ViewModelProviders.of(this).get(viewModelClass)
    }

    // Override if you need factory
    override fun createViewModelFactory(): ViewModelProvider.NewInstanceFactory? {
        return null
    }

    override fun onResume() {
        super.onResume()
        progressView = find(R.id.progressView)
        observeAllData()
    }

    private fun observeAllData() {
        observeLiveData()
        viewModel.isLoadingLiveData.safeObserve(this) {
            it?.let { showProgress(it) }
        }

        viewModel.errorLiveData.safeObserve(this) {
            it?.let { onError(it) }
        }
    }

    protected fun replaceFragment(fragment: Fragment, needToAddToBackStack: Boolean = true) {
        hideKeyboard()
        val name = fragment.javaClass.simpleName
        supportFragmentManager.beginTransaction().apply {
            replace(containerId, fragment, name)
            if (needToAddToBackStack) addToBackStack(name)
        }.commit()
    }

    protected fun removeFragment(fragment: Fragment) {
        hideKeyboard()
        supportFragmentManager.beginTransaction().apply {
            remove(fragment)
        }.commit()
        supportFragmentManager.popBackStack()
    }


    override fun showProgress() {
        progressView?.show()
    }

    override fun hideProgress() {
        progressView?.hide(false)
    }

    override fun showSnackbar(message: String) {
        showSnackBar(find(android.R.id.content), message)
    }

    override fun showSnackbar(res: Int) {
        showSnackBar(find(android.R.id.content), getString(res))
    }

    override fun showSnackBar(message: String, actionRes: Int, onClickCallback: () -> Unit) {
        snackbar = Snackbar.make(find(android.R.id.content), message, SNACK_BAR_DURATION)
            .apply {
                setAction(actionRes) { dismiss() }

                with(this.view) {
                    setOnClickListener {
                        onClickCallback.invoke()
                        dismiss()
                    }
                    setBackgroundResource(R.color.colorBlueGray)
                    with(find<View>(com.google.android.material.R.id.snackbar_text) as TextView) {
                        maxLines = SNACK_BAR_MAX_LINES_NOTIFICATION
                    }
                }

                show()
            }
    }

    override fun showSnackBar(res: Int, actionRes: Int, callback: () -> Unit) {
        showSnackBarWithAction(find(android.R.id.content), res, actionRes, callback)
    }

    private fun showSnackBar(message: String, clickCallback: () -> Unit) {
        showSnackBarWithAction(find(android.R.id.content), message, clickCallback)
    }

    private fun showSnackBar(rootView: View?, text: String?) {
        safeLet(rootView, text) { view, txt ->
            snackbar = Snackbar.make(view, txt, SNACK_BAR_DURATION)
                .apply {
                    setUpSnackBarView(this.view, this)
                    show()
                }
        }
    }

    fun setStatusBarColor(colorRes: Int) {
        withNotNull(window) {
            statusBarColor = ContextCompat.getColor(this@BaseLifecycleActivity, colorRes)
        }
    }

    override fun onError(error: Any) {
        hideProgress()
        when (error) {
            is String -> showSnackbar(error)
            is Throwable -> error.message?.let { showSnackbar(it) }
        }
    }

    private fun showSnackBarWithAction(rootView: View?, res: Int, actionRes: Int, callback: () -> Unit) {
        rootView?.let {
            snackbar = Snackbar.make(it, res, SNACK_BAR_DURATION).apply {
                setAction(actionRes) { callback.invoke() }
                setUpSnackBarView(this.view, this)
                show()
            }
        }
    }

    private fun showSnackBarWithAction(rootView: View?, message: String, onClickCallback: () -> Unit) {
        rootView?.let {
            snackbar = Snackbar.make(it, message, SNACK_BAR_DURATION).apply {
                with(this.view) {
                    setOnClickListener {
                        onClickCallback()
                        dismiss()
                    }
                    setBackgroundResource(R.color.colorBlueGray)
                    with(find<View>(com.google.android.material.R.id.snackbar_text) as TextView) {
                        maxLines = SNACK_BAR_MAX_LINES_NOTIFICATION
                    }
                }

                show()
            }
        }
    }

    private fun setUpSnackBarView(snackBarView: View, snackBar: Snackbar) = with(snackBarView) {
        setOnClickListener { snackBar.dismiss() }
        setBackgroundResource(R.color.colorBlueGray)
        with(find<View>(com.google.android.material.R.id.snackbar_text) as TextView) {
            maxLines = SNACK_BAR_MAX_LINES
        }
    }

    override fun hideSnackBar() {
        snackbar?.let { if (it.isShown) it.dismiss() }
    }

    override fun showProgress(isShow: Boolean) {
        if (isShow) showProgress() else hideProgress()
    }

    override fun showAlert(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                           positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        AlertDialog.Builder(this).apply {
            setMessage(message)
            setCancelable(cancelable)
            title?.let { setTitle(it) }
            setPositiveButton(positiveRes?.let { it } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
            negativeRes?.let { setNegativeButton(it) { _, _ -> negativeFun() } }
            show()
        }
    }

    override fun showAlertFromHtml(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?, positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        AlertDialog.Builder(this).apply {
            setMessage(message.fromHtml())
            setCancelable(cancelable)
            title?.let { setTitle(it.fromHtml()) }
            setPositiveButton(positiveRes?.let { it } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
            negativeRes?.let { setNegativeButton(it) { _, _ -> negativeFun() } }
            show()
        }
    }

    override fun showSingleChoiceAlert(title: String?, cancelable: Boolean, items: Int?,
                                       itemsList: List<CharSequence>?, checkedItem: Int,
                                       callback: (language: String?, position: Int) -> Unit,
                                       positiveRes: Int?, negativeRes: Int?, negativeFun: () -> Unit,
                                       positiveFun: () -> Unit) {
        AlertDialog.Builder(this).apply {
            title?.let { setTitle(it) }
            setCancelable(cancelable)
            val listener = DialogInterface.OnClickListener { dialog, which ->
                positiveRes ?: run {
                    callback.invoke(itemsList?.get(which)?.toString()
                        ?: items?.let { getStringArray(it)[which] }, which)
                    dialog.dismiss()
                }
            }

            positiveRes?.let {
                setPositiveButton(it) { dialog, _ ->
                    val selectedPosition = (dialog as AlertDialog).listView.checkedItemPosition
                    callback.invoke(null, selectedPosition)
                }
            }
            negativeRes?.let {
                setNegativeButton(it) { _, _ -> negativeFun() }
            }
            itemsList?.let {
                setSingleChoiceItems(it.toTypedArray(), checkedItem, listener)
                show()
            } ?: items?.let {
                setSingleChoiceItems(it, checkedItem, listener)
                show()
            }
        }
    }

    override fun showMultiChoiceAlert(title: String?, cancelable: Boolean, itemsRes: Int?,
                                      positiveRes: Int?, checkedItems: BooleanArray,
                                      callback: (BooleanArray) -> Unit) {
        AlertDialog.Builder(this).apply {
            title?.let { setTitle(it) }
            setCancelable(cancelable)
            itemsRes?.let {
                setMultiChoiceItems(itemsRes, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
            }
            setPositiveButton(positiveRes?.let { it }
                ?: R.string.dialog_ok) { _, _ ->
                callback.invoke(checkedItems)
            }
            show()
        }.create()
    }

    override fun showPhotoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit) {
        AlertDialog.Builder(this).apply {

            val listener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    CAMERA_POSITION -> cameraFunc()
                    GALLERY_POSITION -> galleryFunc()
                }
            }
            setItems(R.array.photo_array, listener)

            setCancelable(true)
            setTitle(R.string.add_photo)
            setPositiveButton(R.string.dialog_cancel) { _, _ -> }
            show()
        }
    }

    override fun showPhotoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit,
                                        cameraRes: Int, galleryRes: Int, titleRes: Int) {
        AlertDialog.Builder(this).apply {
            setItems(mutableListOf<CharSequence>().apply {
                add(getString(galleryRes))
                add(getString(cameraRes))
            }.toTypedArray()) { _, which ->
                when (which) {
                    GALLERY_POSITION -> cameraFunc()
                    else -> galleryFunc()
                }
            }

            setCancelable(true)
            setTitle(titleRes)
            setPositiveButton(R.string.dialog_cancel) { _, _ -> }
            show()
        }
    }

    override fun showVideoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit,
                                        cameraRes: Int, galleryRes: Int, titleRes: Int) {
        AlertDialog.Builder(this).apply {
            setItems(mutableListOf<CharSequence>().apply {
                add(getString(galleryRes))
                add(getString(cameraRes))
            }.toTypedArray()) { _, which ->
                when (which) {
                    GALLERY_POSITION -> cameraFunc()
                    else -> galleryFunc()
                }
            }

            setCancelable(true)
            setTitle(titleRes)
            setPositiveButton(R.string.dialog_cancel) { _, _ -> }
            show()
        }
    }

    fun recreateFragment(fragment: Fragment) {
        hideKeyboard()
        supportFragmentManager.beginTransaction().apply {
            detach(fragment)
            attach(fragment)
        }.commit()
    }

    protected inline fun <reified T : Fragment> newFragmentInstance(vararg params: Pair<String, Any?>) =
        T::class.java.newInstance().apply {
            arguments = bundleOf(*params)
        }

    protected inline fun <reified T : View> Activity.find(@IdRes id: Int): T = findViewById<T>(id)
}

