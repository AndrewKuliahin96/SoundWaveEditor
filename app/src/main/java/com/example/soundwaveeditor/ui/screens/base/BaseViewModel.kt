package com.example.soundwaveeditor.ui.screens.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable


abstract class BaseViewModel(app: Application) : AndroidViewModel(app) {
    protected val compositeDisposable = CompositeDisposable()
    val errorLiveData = MutableLiveData<Any?>()
    val isLoadingLiveData = MediatorLiveData<Boolean>()

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}
