package com.example.soundwaveeditor.ui.screens.base

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider


interface BaseView {

    fun showProgress()

    fun showProgress(isShow: Boolean)

    fun hideProgress()

    fun onError(error: Any)

    fun showSnackbar(message: String)

    fun showSnackbar(@StringRes res: Int)

    fun showSnackBar(@StringRes res: Int, @StringRes actionRes: Int, callback: () -> Unit)

    fun showSnackBar(message: String, @StringRes actionRes: Int, onClickCallback: () -> Unit)

    fun showAlert(message: CharSequence, title: CharSequence? = null, cancelable: Boolean = false,
                  positiveRes: Int? = null, positiveFun: () -> Unit = {},
                  negativeRes: Int? = null, negativeFun: () -> Unit = {})

    fun showAlertFromHtml(message: CharSequence, title: CharSequence? = null, cancelable: Boolean = false,
                          positiveRes: Int? = null, positiveFun: () -> Unit = {},
                          negativeRes: Int? = null, negativeFun: () -> Unit = {})

    fun showSingleChoiceAlert(title: String? = null, cancelable: Boolean = false,
                              itemsRes: Int? = null, itemsList: List<CharSequence>? = null,
                              checkedItem: Int = 0, callback: (selectedItem: String?, position: Int) -> Unit,
                              positiveRes: Int? = null, negativeRes: Int? = null,
                              positiveFun: () -> Unit = {}, negativeFun: () -> Unit = {})

    fun showMultiChoiceAlert(title: String? = null, cancelable: Boolean = false, itemsRes: Int? = null,
                             positiveRes: Int? = null, checkedItems: BooleanArray, callback: (BooleanArray) -> Unit)

    fun hideSnackBar()

    fun createViewModelFactory(): ViewModelProvider.NewInstanceFactory?

    fun showPhotoRequestDialog(cameraFunc: () -> Unit = {}, galleryFunc: () -> Unit = {}, cameraRes: Int, galleryRes: Int, titleRes: Int)
    fun showVideoRequestDialog(cameraFunc: () -> Unit = {}, galleryFunc: () -> Unit = {}, cameraRes: Int, galleryRes: Int, titleRes: Int)

    fun showPhotoRequestDialog(cameraFunc: () -> Unit = {}, galleryFunc: () -> Unit = {})
}
