package com.example.soundwaveeditor.ui.screens.base

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.example.soundwaveeditor.extensions.NO_FLAGS
import com.example.soundwaveeditor.extensions.hideKeyboard
import com.example.soundwaveeditor.ui.screens.base.BaseLifecycleActivity.Companion.NO_ID
import com.google.android.material.textfield.TextInputLayout


abstract class BaseLifecycleFragment<T : BaseViewModel> : Fragment(), BaseView {

    companion object {
        const val NO_TOOLBAR = -1
        const val NO_TITLE = -1
        const val NO_BACK_NAV = -1
        private const val ZERO_HOUR_VALUE = 0

        private const val EMPTY_STRING_VALUE = ""
    }

    abstract val viewModelClass: Class<T>

    protected val viewModel: T by lazy {
        createViewModelFactory()?.let {
            ViewModelProviders.of(this, it).get(viewModelClass)
        } ?: ViewModelProviders.of(this).get(viewModelClass)
    }

    protected abstract fun observeLiveData()

    protected abstract val layoutId: Int
    protected abstract val toolbarId: Int
    protected abstract val toolbarTitleId: Int
    protected abstract val backNavId: Int
    protected abstract val isHaveToolbar: Boolean

    protected open val changeRoleId: Int = NO_ID

    private var baseView: BaseView? = null
    private val textWatchers: Map<EditText?, TextWatcher> = mutableMapOf()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        baseView = bindInterfaceOrThrow<BaseView>(parentFragment, context)
    }

    override fun onDetach() {
        baseView = null
        super.onDetach()
    }

    // Override if you need factory
    override fun createViewModelFactory(): ViewModelProvider.NewInstanceFactory? {
        return null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(layoutId, container, false)
        hideKeyboard(view)
        return view
    }

    override fun onResume() {
        super.onResume()
        view?.let { hideKeyboard(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeAllData()
    }

    private fun observeAllData() {
        observeLiveData()
        viewModel.isLoadingLiveData.observe(this, Observer<Boolean> {
            it?.let { if (userVisibleHint) showProgress(it) }
        })

        viewModel.errorLiveData.observe(this, Observer {
            it?.let {
                onError(it)
                viewModel.errorLiveData.value = null
            }
        })
    }

    override fun showProgress() {
        baseView?.showProgress()
    }

    override fun hideProgress() {
        baseView?.hideProgress()
    }

    override fun showSnackbar(message: String) {
        baseView?.showSnackbar(message)
    }

    override fun showSnackbar(message: Int) {
        baseView?.showSnackbar(message)
    }

    override fun showSnackBar(message: String, actionRes: Int, onClickCallback: () -> Unit) {
        baseView?.showSnackBar(message, actionRes, onClickCallback)
    }

    override fun showAlert(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                           positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        baseView?.showAlert(message, title, cancelable, positiveRes, positiveFun, negativeRes, negativeFun)
    }

    override fun hideSnackBar() {
        baseView?.hideSnackBar()
    }

    override fun showProgress(isShow: Boolean) {
        if (isShow) showProgress() else hideProgress()
    }

    override fun showSnackBar(res: Int, actionRes: Int, callback: () -> Unit) {
        baseView?.showSnackBar(res, actionRes, callback)
    }

    override fun showVideoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit, cameraRes: Int, galleryRes: Int, titleRes: Int) {
        baseView?.showVideoRequestDialog(cameraFunc, galleryFunc, cameraRes, galleryRes, titleRes)
    }

    override fun onError(error: Any) {
        baseView?.onError(error)
    }

    override fun showPhotoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit,
                                        cameraRes: Int, galleryRes: Int, titleRes: Int) {
        baseView?.showPhotoRequestDialog(cameraFunc, galleryFunc, cameraRes, galleryRes, titleRes)
    }

    override fun showPhotoRequestDialog(cameraFunc: () -> Unit, galleryFunc: () -> Unit) {
        baseView?.showPhotoRequestDialog(cameraFunc, galleryFunc)
    }

    override fun showAlertFromHtml(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?, positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        baseView?.showAlertFromHtml(message, title, cancelable, positiveRes, positiveFun, negativeRes, negativeFun)
    }

    override fun showSingleChoiceAlert(title: String?, cancelable: Boolean, itemsRes: Int?,
                                       itemsList: List<CharSequence>?, checkedItem: Int,
                                       callback: (selectedItem: String?, position: Int) -> Unit,
                                       positiveRes: Int?, negativeRes: Int?,
                                       positiveFun: () -> Unit, negativeFun: () -> Unit) {
        baseView?.showSingleChoiceAlert(title, cancelable, itemsRes, itemsList, checkedItem,
            callback, positiveRes, negativeRes, positiveFun, negativeFun)
    }

    override fun showMultiChoiceAlert(title: String?, cancelable: Boolean, itemsRes: Int?,
                                      positiveRes: Int?, checkedItems: BooleanArray, callback: (BooleanArray) -> Unit) {
        baseView?.showMultiChoiceAlert(title, cancelable, itemsRes, positiveRes, checkedItems, callback)
    }

    override fun onStop() {
        hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        textWatchers.forEach { (key, value) -> key?.removeTextChangedListener(value) }
        super.onDestroyView()
    }

    protected fun hideError(til: TextInputLayout) {
        til.error = EMPTY_STRING_VALUE
        til.isErrorEnabled = false
    }

    protected fun showError(til: TextInputLayout, error: String) {
        if (til.error != error) {
            til.isErrorEnabled = true
            til.error = error
        }
    }

    protected inline fun <reified T> bindInterfaceOrThrow(vararg objects: Any?):
            T = objects.find { it is T } as T
        ?: throw NotImplementedInterfaceException(T::class.java)

    private fun hideKeyboard(view: View) {
        (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).apply {
            hideSoftInputFromWindow(view.windowToken, NO_FLAGS)
        }
    }

    protected fun replaceFragment(fragment: Fragment, containerId: Int, needToAddToBackStack: Boolean = true) {
        hideKeyboard()
        val name = fragment.javaClass.simpleName
        val fragmentPopped = childFragmentManager.popBackStackImmediate(name, 0)
        if (!fragmentPopped) {
            childFragmentManager.beginTransaction().apply {
                replace(containerId, fragment, name)
                if (needToAddToBackStack) addToBackStack(name)
            }.commitAllowingStateLoss()
        }
    }

    protected fun recreateFragment(fragment: Fragment) {
        hideKeyboard()
        childFragmentManager.beginTransaction().apply {
            detach(fragment)
            attach(fragment)
        }.commit()
    }

    /**
     * Extension for adding [TextWatcher] to [EditText] which will be cleared when fragment is destroyed.
     */
    fun EditText.addTextWatcher(watcher: TextWatcher) = this.apply {
        textWatchers.plus(this to watcher)
        addTextChangedListener(watcher)
    }

    protected fun actionWebsite(url: String) {
        if (!TextUtils.isEmpty(url)) {
            val webPage = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, webPage)
            activity?.packageManager?.let { pm ->
                intent.resolveActivity(pm)?.let {
                    startActivity(intent)
                } ?: showSnackbar("EROROR OOR ")
            }
        }
    }
}
