package com.example

import android.app.Application
import android.content.Context
import com.example.core.di.AppContainer

class BDPayHubApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        instanceContainer = container
    }

    companion object {
        @Volatile
        private var instanceContainer: AppContainer? = null

        fun getContainer(context: Context): AppContainer {
            return instanceContainer ?: synchronized(this) {
                instanceContainer ?: AppContainer(context.applicationContext).also {
                    instanceContainer = it
                }
            }
        }

        fun setContainerForTesting(testContainer: AppContainer) {
            instanceContainer = testContainer
        }
    }
}
