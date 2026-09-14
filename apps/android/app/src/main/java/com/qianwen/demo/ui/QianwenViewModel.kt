package com.qianwen.demo.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider

@Deprecated("moved to Java: QianwenViewModel")
fun qianwenViewModelFactory(application: Application): ViewModelProvider.Factory {
    return QianwenViewModel.factory(application)
}
