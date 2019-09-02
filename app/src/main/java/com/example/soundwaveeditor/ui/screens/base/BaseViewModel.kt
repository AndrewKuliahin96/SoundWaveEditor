package com.example.soundwaveeditor.ui.screens.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable


abstract class BaseViewModel(app: Application) : AndroidViewModel(app) {

    val errorLD = MutableLiveData<Any>()
    val isLoadingLD = MutableLiveData<Boolean>()

    protected var compositeDisposable: CompositeDisposable? = null

    override fun onCleared() {
        clearSubscription()
        super.onCleared()
    }

    protected fun clearSubscription() {
        compositeDisposable?.apply {
            dispose()
            compositeDisposable = null
        }
    }
}
