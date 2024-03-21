package me.yuanis.copilot.gpt.service.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.yuanis.copilot.gpt.service.App.Companion.TAG
import me.yuanis.copilot.gpt.service.R
import me.yuanis.copilot.gpt.service.RelayService
import me.yuanis.copilot.gpt.service.data.RelayRepo
import me.yuanis.copilot.gpt.service.databinding.ActivityMainBinding
import me.yuanis.copilot.gpt.service.server.ServerStatus
import me.yuanis.copilot.gpt.service.server.mockRequestRelayService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()

        checkPermissions()

        // Check if the configuration exists and start the service
        if (checkParams(isSilent = true)) {
            startService()
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkPermissions() {
        // Check if the app has the permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        // Check if the app is ignoring battery optimizations
        if (isIgnoringBatteryOptimizations().not()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun initViews() {
        binding.etCopilotToken.setText(RelayRepo.getCopilotToken())
        binding.etServerPort.setText(RelayRepo.getServerPort().toString())

        RelayService.isServerRunning.observe(this) { status ->
            val isRunning = status.isRunning()
            binding.swServerStatus.isChecked = isRunning
            binding.etCopilotToken.isEnabled = isRunning.not()
            binding.etServerPort.isEnabled = isRunning.not()
            binding.btnUpdate.isEnabled = isRunning.not()
            binding.btnTest.isEnabled = isRunning
            updateServerStatus(status, RelayService.processedRequestsCount.value)
        }

        RelayService.processedRequestsCount.observe(this) {
            updateServerStatus(RelayService.isServerRunning.value, it)
        }

        binding.swServerStatus.setOnCheckedChangeListener { _, isChecked ->
            toggleService(isChecked)
        }

        binding.btnUpdate.setOnClickListener {
            if (checkParams().not()) {
                return@setOnClickListener
            }
            updateCurrentConfig()
        }

        binding.btnTest.setOnClickListener {
            lifecycleScope.launch {
                val result = runCatching {
                    mockRequestRelayService()
                }.onFailure {
                    Log.d(TAG, "test failed", it)
                }.getOrDefault(false)
                val message = if (result) "Test success" else "Test failed"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServerStatus(serverStatus: ServerStatus?, handleCount: Int?) {
        val status = serverStatus ?: ServerStatus.Stopped
        val count = handleCount ?: 0

        binding.tvServerStatus.text = if (status is ServerStatus.Running) {
            getString(R.string.server_is_running_status, status.port.toString(), count.toString())
        } else {
            getString(R.string.server_is_not_running)
        }
    }

    private fun updateCurrentConfig() {
        val copilotToken = binding.etCopilotToken.text.toString()
        RelayRepo.saveCopilotToken(copilotToken)
        val port = binding.etServerPort.text.toString().toInt()
        RelayRepo.saveServerPort(port)
    }

    private fun startService() {
        if (checkParams().not()) {
            RelayService.postStartFailed()
            return
        }
        updateCurrentConfig()
        if (RelayService.isServerRunning.value?.isRunning() != true) {
            startService(Intent(this, RelayService::class.java))
            return
        }
    }

    private fun checkParams(isSilent: Boolean = false): Boolean {
        fun EditText.showError(message: String) {
            if (isSilent.not()) {
                error = message
            }
        }
        if (binding.etCopilotToken.text.toString().isBlank()) {
            binding.etCopilotToken.showError("Please input Copilot token")
            return false
        }
        val portString = binding.etServerPort.text.toString()
        if (portString.isBlank()) {
            binding.etServerPort.showError("Please input server port")
            return false
        }
        val port = try {
            binding.etServerPort.text.toString().toInt()
        } catch (e: Throwable) {
            binding.etServerPort.showError("Please input valid server port")
            return false
        }

        if (port < 1024 || port > 65535) {
            binding.etServerPort.showError("Please input valid server port")
            return false
        }

        return true
    }

    private fun toggleService(isStart: Boolean) {
        if (isStart) {
            startService()
        } else {
            stopService()
        }
    }

    private fun stopService() {
        if (RelayService.isServerRunning.value?.isRunning() == true) {
            stopService(Intent(this, RelayService::class.java))
            return
        }
    }
}
