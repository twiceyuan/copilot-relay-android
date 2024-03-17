package me.yuanis.copilot.gpt.service

import android.app.Application
import me.yuanis.copilot.gpt.service.data.RelayRepo


class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RelayRepo.init(this)
    }

    companion object {
        const val TAG = "RelayService"
    }
}
