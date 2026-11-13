// === FILE: app/src/main/java/com/enterwebslinger/network/EnterwebSlingerApp.kt ===
package com.enterwebslinger.network

import android.app.Application
import com.enterwebslinger.network.core.di.AppContainer

class EnterwebSlingerApp : Application() {
    lateinit var container: AppContainer
        private set

    companion object {
        @Volatile
        var instance: EnterwebSlingerApp? = null
            private set

        @Volatile
        var appContainer: AppContainer? = null

        fun getContainer(): AppContainer {
            appContainer?.let { return it }
            val app = instance ?: run {
                try {
                    val activityThread = Class.forName("android.app.ActivityThread")
                    val currentAppMethod = activityThread.getMethod("currentApplication")
                    currentAppMethod.invoke(null) as? Application
                } catch (_: Exception) {
                    null
                }
            } ?: Application()
            val newContainer = AppContainer(app)
            appContainer = newContainer
            return newContainer
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        appContainer = container
    }
}
