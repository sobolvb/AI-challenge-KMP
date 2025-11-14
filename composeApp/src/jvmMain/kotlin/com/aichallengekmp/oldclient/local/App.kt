package local.ai

import android.app.Application
import local.di.ServiceLocator

class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize app-wide dependencies if needed
    }
    
    override fun onTerminate() {
        super.onTerminate()
        ServiceLocator.cleanup()
    }
}