package com.foldos.launcher.core

import android.app.Application
import android.content.Context
import com.foldos.launcher.data.AppGraphImpl

/** Application entry point. Holds the [AppGraph] for the process. */
class FoldOsApplication : Application() {

    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraphImpl(this)
    }

    companion object {
        fun from(context: Context): FoldOsApplication =
            context.applicationContext as FoldOsApplication
    }
}
