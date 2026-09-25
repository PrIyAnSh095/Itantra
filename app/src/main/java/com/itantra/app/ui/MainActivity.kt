package com.itantra.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.itantra.app.R
import com.itantra.app.data.AppLanguage
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.data.ConnectionState
import com.itantra.app.data.RecordingState
import com.itantra.app.data.VoiceOption
import com.itantra.app.databinding.ActivityMainBinding
import com.itantra.app.localization.AppLanguageManager
import com.itantra.app.utils.PermissionManager
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PermissionManager.requestAppPermissions(this)

        setupSpinners()
        setupButtons()
        setupTransportToggle()
        setupVoiceControls()
        setupModelDownload()
        observeViewModel()
    }

    private fun setupSpinners() {
        // App UI Language Spinner
        val appLanguages = AppLanguage.values()
        val appLangNames = appLanguages.map { "${it.displayName} (${it.nativeName})" }
        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, appLangNames)
        binding.spinnerAppLang.adapter = appAdapter

        val currentAppLang = AppLanguageManager.getSelectedLanguage(this)
        binding.spinnerAppLang.setSelection(appLanguages.indexOf(currentAppLang))

        binding.spinnerAppLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = appLanguages[position]
                if (selected != AppLanguageManager.getSelectedLanguage(this@MainActivity)) {
                    AppLanguageManager.applyAppLanguage(this@MainActivity, selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Communication Language Spinner
        val commLanguages = CommunicationLanguage.values()
        val commLangNames = commLanguages.map { "${it.displayName} (${it.nativeName})" }
        val commAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, commLangNames)
        binding.spinnerCommLang.adapter = commAdapter
        binding.spinnerCommLang.setSelection(commLanguages.indexOf(CommunicationLanguage.HINDI))

        binding.spinnerCommLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = commLanguages[position]
                viewModel.setCommunicationLanguage(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTransportToggle() {
        binding.btnTransportWifi.setOnClickListener {
            viewModel.setTransportType(useWifi = true)
            updateTransportButtons(useWifi = true)
        }

        binding.btnTransportBt.setOnClickListener {
            if (!PermissionManager.hasBluetoothPermission(this)) {
                PermissionManager.requestAppPermissions(this)
            }
            viewModel.setTransportType(useWifi = false)
            updateTransportButtons(useWifi = false)
        }

        binding.btnSelectBtPeer.setOnClickListener {
            showBluetoothDevicePicker()
        }
    }

    private fun updateTransportButtons(useWifi: Boolean) {
        val activeBg = ContextCompat.getColor(this, R.color.primary_blue)
        val inactiveBg = ContextCompat.getColor(this, R.color.bg_card_secondary)
        val activeText = ContextCompat.getColor(this, R.color.text_inverse)
        val inactiveText = ContextCompat.getColor(this, R.color.text_secondary)

        if (useWifi) {
            binding.btnTransportWifi.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnTransportWifi.setTextColor(activeText)
            binding.btnTransportBt.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnTransportBt.setTextColor(inactiveText)
            binding.tvNetworkType.text = getString(R.string.network_wifi_hotspot)
            binding.btnSelectBtPeer.visibility = View.GONE
        } else {
            binding.btnTransportBt.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnTransportBt.setTextColor(activeText)
            binding.btnTransportWifi.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnTransportWifi.setTextColor(inactiveText)
            binding.tvNetworkType.text = "Bluetooth Classic P2P"

            val isHost = viewModel.isHostMode.value ?: true
            binding.btnSelectBtPeer.visibility = if (!isHost) View.VISIBLE else View.GONE
        }
    }

    private fun showBluetoothDevicePicker() {
        val devices = viewModel.getPairedBluetoothDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Paired Bluetooth Devices")
                .setMessage("No paired phones were found. Please pair phones with each other in Android Bluetooth Settings first.")
                .setPositiveButton("Open Bluetooth Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            return
        }

        val deviceNames = devices.map { "${it.first} (${it.second})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Paired Peer Phone")
            .setItems(deviceNames) { _, index ->
                val selected = devices[index]
                viewModel.selectBluetoothDevice(name = selected.first, address = selected.second)
                binding.btnSelectBtPeer.text = "📱 Peer: ${selected.first}"
                Toast.makeText(this, "Selected ${selected.first} as Bluetooth peer", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupVoiceControls() {
        binding.tvActiveVoiceName.text = viewModel.selectedVoice.value?.displayLabel ?: "Priya (Female)"

        binding.btnPreviewVoiceMain.setOnClickListener {
            val currentVoice = viewModel.selectedVoice.value ?: VoiceOption.PRIYA
            Toast.makeText(this, "Previewing ${currentVoice.name} voice...", Toast.LENGTH_SHORT).show()
            viewModel.previewVoice(currentVoice)
        }

        binding.btnChangeVoice.setOnClickListener {
            showVoicePicker()
        }
    }

    private fun showVoicePicker() {
        val voices = VoiceOption.ALL_VOICES
        val voiceItems = voices.map { "${it.name} (${if (it.gender == com.itantra.app.data.VoiceGender.FEMALE) "Female" else "Male"}) · ${it.description}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Voice Persona")
            .setItems(voiceItems) { _, index ->
                val chosenVoice = voices[index]
                viewModel.setVoiceOption(chosenVoice)
                binding.tvActiveVoiceName.text = chosenVoice.displayLabel
                Toast.makeText(this, "Voice set to ${chosenVoice.name}", Toast.LENGTH_SHORT).show()
                viewModel.previewVoice(chosenVoice)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupModelDownload() {
        binding.btnInstallModel.setOnClickListener {
            val currentLang = viewModel.commLanguage.value ?: CommunicationLanguage.HINDI
            AlertDialog.Builder(this)
                .setTitle("Download Voice Model Pack")
                .setMessage("Download offline STT and TTS neural models for ${currentLang.displayName}? (Total ~120 MB)\n\nOnce downloaded, voice recognition and speech playback run 100% offline on-device.")
                .setPositiveButton("Download Now") { _, _ ->
                    binding.btnInstallModel.isEnabled = false
                    binding.btnInstallModel.text = "Downloading..."
                    binding.layoutMainDownloadProgress.visibility = View.VISIBLE
                    binding.pbMainDownload.progress = 0
                    binding.tvMainDownloadPercent.text = "0%"

                    viewModel.downloadCurrentLanguageModels { success, errorMsg ->
                        binding.layoutMainDownloadProgress.visibility = View.GONE
                        binding.btnInstallModel.isEnabled = true
                        if (success) {
                            Toast.makeText(this, "${currentLang.displayName} models installed successfully!", Toast.LENGTH_SHORT).show()
                            binding.btnInstallModel.visibility = View.GONE
                        } else {
                            Toast.makeText(this, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                            binding.btnInstallModel.text = "Retry Download"
                        }
                    }
                }
                .setNegativeButton("Manage in Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNeutralButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        // Settings Button
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Host Mode Button
        binding.btnHostMode.setOnClickListener {
            viewModel.setHostMode(true)
            updateModeButtons(true)
        }

        // Join Mode Button
        binding.btnJoinMode.setOnClickListener {
            viewModel.setHostMode(false)
            updateModeButtons(false)
        }

        // Generate Room Code Button
        binding.btnGenerateCode.setOnClickListener {
            val randomCode = String.format(Locale.US, "%06d", Random.nextInt(100000, 999999))
            binding.etRoomCode.setText(randomCode)
            viewModel.setRoomCode(randomCode)
        }

        // Connect / Disconnect Button
        binding.btnConnect.setOnClickListener {
            val isConnected = viewModel.connectionState.value == ConnectionState.CONNECTED
            if (isConnected) {
                viewModel.disconnect()
            } else {
                val code = binding.etRoomCode.text.toString().trim()
                if (code.isBlank()) {
                    Toast.makeText(this, getString(R.string.err_enter_room_code), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.setRoomCode(code)
                viewModel.connect()
            }
        }

        // Emergency Alert Toggle
        binding.btnAlert.setOnClickListener {
            viewModel.toggleAlertMode()
        }

        // Alert Banner dismissal
        binding.tvAlertBanner.setOnClickListener {
            viewModel.dismissAlertBanner()
        }

        // Push-to-Talk (Hold-To-Speak)
        binding.btnPtt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!PermissionManager.hasRecordAudioPermission(this)) {
                        PermissionManager.requestAppPermissions(this)
                        return@setOnTouchListener true
                    }
                    if (viewModel.connectionState.value != ConnectionState.CONNECTED) {
                        Toast.makeText(this, getString(R.string.msg_cannot_speak_disconnected), Toast.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    viewModel.onPttDown()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewModel.onPttUp()
                }
            }
            true
        }
    }

    private fun updateModeButtons(isHost: Boolean) {
        val activeBg = ContextCompat.getColor(this, R.color.primary_blue)
        val inactiveBg = ContextCompat.getColor(this, R.color.bg_card_secondary)
        val activeText = ContextCompat.getColor(this, R.color.text_inverse)
        val inactiveText = ContextCompat.getColor(this, R.color.text_secondary)

        val isBt = viewModel.isBluetoothTransport.value ?: false

        if (isHost) {
            binding.btnHostMode.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnHostMode.setTextColor(activeText)
            binding.btnJoinMode.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnJoinMode.setTextColor(inactiveText)
            binding.btnGenerateCode.visibility = View.VISIBLE
            binding.btnSelectBtPeer.visibility = View.GONE
        } else {
            binding.btnJoinMode.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnJoinMode.setTextColor(activeText)
            binding.btnHostMode.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnHostMode.setTextColor(inactiveText)
            binding.btnGenerateCode.visibility = View.GONE
            binding.btnSelectBtPeer.visibility = if (isBt) View.VISIBLE else View.GONE
        }
    }

    private fun observeViewModel() {
        // Connection State
        viewModel.connectionState.observe(this) { state ->
            when (state) {
                ConnectionState.CONNECTED -> {
                    binding.tvConnectionBadge.setBackgroundResource(R.drawable.bg_badge_connected)
                    binding.tvConnectionBadge.text = getString(R.string.status_connected)
                    binding.tvConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.state_green))
                    binding.btnConnect.text = getString(R.string.btn_disconnect)
                    binding.btnConnect.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.state_red))
                    binding.btnPtt.isEnabled = true
                }
                ConnectionState.CONNECTING -> {
                    binding.tvConnectionBadge.setBackgroundResource(R.drawable.bg_badge_disconnected)
                    binding.tvConnectionBadge.text = getString(R.string.status_connecting)
                    binding.tvConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.state_orange))
                    binding.btnConnect.text = getString(R.string.btn_connect)
                    binding.btnConnect.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue))
                }
                ConnectionState.DISCONNECTED -> {
                    binding.tvConnectionBadge.setBackgroundResource(R.drawable.bg_badge_disconnected)
                    binding.tvConnectionBadge.text = getString(R.string.status_disconnected)
                    binding.tvConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.state_gray))
                    binding.btnConnect.text = getString(R.string.btn_connect)
                    binding.btnConnect.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue))
                    binding.tvRoomStatusDetail.text = getString(R.string.status_disconnected)
                    binding.btnPtt.isEnabled = false
                }
                ConnectionState.ERROR -> {
                    binding.tvConnectionBadge.setBackgroundResource(R.drawable.bg_badge_disconnected)
                    binding.tvConnectionBadge.text = getString(R.string.status_error)
                    binding.tvConnectionBadge.setTextColor(ContextCompat.getColor(this, R.color.state_red))
                    binding.btnConnect.text = getString(R.string.btn_connect)
                    binding.btnConnect.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue))
                    binding.btnPtt.isEnabled = false
                }
                else -> {}
            }
        }

        viewModel.connectionDetail.observe(this) { detail ->
            binding.tvRoomStatusDetail.text = detail
        }

        // Recording / PTT State
        viewModel.recordingState.observe(this) { state ->
            when (state) {
                RecordingState.READY -> {
                    binding.tvPttState.text = getString(R.string.state_ready)
                    binding.btnPtt.setBackgroundResource(R.drawable.bg_ptt_idle)
                    binding.btnPtt.text = getString(R.string.btn_hold_to_speak)
                }
                RecordingState.RECORDING -> {
                    binding.tvPttState.text = getString(R.string.state_recording)
                    binding.btnPtt.setBackgroundResource(R.drawable.bg_ptt_recording)
                    binding.btnPtt.text = getString(R.string.state_recording)
                }
                RecordingState.PROCESSING -> {
                    binding.tvPttState.text = getString(R.string.state_processing)
                    binding.btnPtt.setBackgroundResource(R.drawable.bg_ptt_processing)
                    binding.btnPtt.text = getString(R.string.state_processing)
                }
                RecordingState.SENDING -> {
                    binding.tvPttState.text = getString(R.string.state_sending)
                }
                RecordingState.RECEIVING -> {
                    binding.tvPttState.text = getString(R.string.state_receiving)
                }
                RecordingState.PLAYING -> {
                    binding.tvPttState.text = getString(R.string.state_playing)
                }
                else -> {}
            }
        }

        // Transcripts
        viewModel.transcriptYou.observe(this) { text ->
            if (text.isNotBlank()) {
                binding.tvTranscriptEmpty.visibility = View.GONE
                binding.tvTranscriptYou.visibility = View.VISIBLE
                binding.tvTranscriptYou.text = "${getString(R.string.label_you)}: $text"
            }
        }

        viewModel.transcriptReceived.observe(this) { text ->
            if (text.isNotBlank()) {
                binding.tvTranscriptEmpty.visibility = View.GONE
                binding.tvTranscriptReceived.visibility = View.VISIBLE
                binding.tvTranscriptReceived.text = "${getString(R.string.label_received)}: $text"
            }
        }

        // Alert States
        viewModel.isAlertActive.observe(this) { active ->
            if (active) {
                binding.btnAlert.setBackgroundResource(R.drawable.bg_alert_active_btn)
                binding.btnAlert.setTextColor(ContextCompat.getColor(this, R.color.text_inverse))
                binding.btnAlert.text = getString(R.string.btn_alert_active)
            } else {
                binding.btnAlert.setBackgroundResource(R.drawable.bg_alert_btn)
                binding.btnAlert.setTextColor(ContextCompat.getColor(this, R.color.state_red))
                binding.btnAlert.text = getString(R.string.btn_send_alert)
            }
        }

        viewModel.alertReceived.observe(this) { received ->
            binding.tvAlertBanner.visibility = if (received) View.VISIBLE else View.GONE
        }

        // Performance & Bandwidth Metrics
        viewModel.performanceMetrics.observe(this) { metrics ->
            binding.tvMetricStt.text = "${getString(R.string.metric_stt)}: ${metrics.sttLatencyMs} ms"
            binding.tvMetricNet.text = "${getString(R.string.metric_network)}: ${metrics.networkLatencyMs} ms"
            binding.tvMetricTts.text = "${getString(R.string.metric_tts)}: ${metrics.ttsLatencyMs} ms"

            val transStr = if (metrics.translationSkipped) {
                "${getString(R.string.metric_translation)}: ${getString(R.string.metric_skipped)}"
            } else {
                "${getString(R.string.metric_translation)}: ${metrics.translationLatencyMs} ms"
            }
            binding.tvMetricTrans.text = transStr

            val e2eText = if (metrics.e2eLatencyMs > 1000) {
                String.format(Locale.US, "%.2f s", metrics.e2eLatencyMs / 1000.0)
            } else {
                "${metrics.e2eLatencyMs} ms"
            }
            binding.tvMetricE2e.text = "${getString(R.string.metric_e2e)}: $e2eText"

            binding.tvMetricBandwidth.text = getString(
                R.string.metric_bytes_saved_format,
                metrics.payloadBytes,
                metrics.rawPcmBytes,
                metrics.reductionPercent
            )
        }

        // Model Status & Download Option
        viewModel.modelStatus.observe(this) { status ->
            binding.tvModelStatusBadge.text = status
        }

        viewModel.isCommModelReady.observe(this) { ready ->
            if (ready) {
                binding.tvModelStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.state_green))
                binding.btnInstallModel.visibility = View.GONE
            } else {
                binding.tvModelStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.state_orange))
                binding.btnInstallModel.visibility = View.VISIBLE
                val lang = viewModel.commLanguage.value?.displayName ?: "Hindi"
                binding.btnInstallModel.text = "📥 Download $lang Model"
            }
        }

        // Live Download Progress on Main Screen
        viewModel.downloadProgressPercent.observe(this) { percent ->
            if (percent in 1..99) {
                binding.layoutMainDownloadProgress.visibility = View.VISIBLE
                binding.pbMainDownload.progress = percent
                binding.tvMainDownloadPercent.text = "$percent%"
            } else if (percent == 100) {
                binding.layoutMainDownloadProgress.visibility = View.GONE
            }
        }

        // Voice Option Updates
        viewModel.selectedVoice.observe(this) { voice ->
            binding.tvActiveVoiceName.text = voice.displayLabel
        }

        // Transport Mode Updates
        viewModel.isBluetoothTransport.observe(this) { isBt ->
            updateTransportButtons(!isBt)
        }

        // Room Code Sync
        viewModel.roomCode.observe(this) { code ->
            if (binding.etRoomCode.text.toString() != code) {
                binding.etRoomCode.setText(code)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.REQUEST_CODE_PERMISSIONS) {
            val micGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!micGranted) {
                Toast.makeText(this, getString(R.string.err_mic_permission), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkCurrentModelStatus()
        val isBt = viewModel.isBluetoothTransport.value ?: false
        updateTransportButtons(!isBt)
    }
}
