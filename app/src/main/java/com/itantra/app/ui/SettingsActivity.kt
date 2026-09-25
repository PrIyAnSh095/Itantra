package com.itantra.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.itantra.app.R
import com.itantra.app.data.AppLanguage
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.data.ModelDownloadState
import com.itantra.app.data.ModelInfo
import com.itantra.app.data.VoiceOption
import com.itantra.app.databinding.ActivitySettingsBinding
import com.itantra.app.localization.AppLanguageManager
import com.itantra.app.ml.ModelManager
import com.itantra.app.ml.SystemTtsEngine
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "itantra_prefs"
        private const val KEY_VOICE_ID = "selected_voice_id"
        private const val KEY_TRANSPORT_BT = "transport_bluetooth"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var modelManager: ModelManager
    private lateinit var systemTtsEngine: SystemTtsEngine
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE) }

    private var targetImportModelName: String? = null

    private val importModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && targetImportModelName != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val success = modelManager.importModel(stream, targetImportModelName!!)
                    if (success) {
                        Toast.makeText(this, "Model imported successfully!", Toast.LENGTH_SHORT).show()
                        populateModelsList()
                    } else {
                        Toast.makeText(this, "Failed to import model.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(this)
        systemTtsEngine = SystemTtsEngine(this)

        setupToolbar()
        setupLanguageSpinners()
        setupVoicePersona()
        setupTransportRadio()
        setupModelImport()
        populateModelsList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupLanguageSpinners() {
        // App Language
        val appLanguages = AppLanguage.values()
        val appLangNames = appLanguages.map { "${it.displayName} (${it.nativeName})" }
        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, appLangNames)
        binding.settingsSpinnerAppLang.adapter = appAdapter

        val currentAppLang = AppLanguageManager.getSelectedLanguage(this)
        binding.settingsSpinnerAppLang.setSelection(appLanguages.indexOf(currentAppLang))

        binding.settingsSpinnerAppLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = appLanguages[position]
                if (selected != AppLanguageManager.getSelectedLanguage(this@SettingsActivity)) {
                    AppLanguageManager.applyAppLanguage(this@SettingsActivity, selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Communication Language
        val commLanguages = CommunicationLanguage.values()
        val commLangNames = commLanguages.map { "${it.displayName} (${it.nativeName})" }
        val commAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, commLangNames)
        binding.settingsSpinnerCommLang.adapter = commAdapter
        binding.settingsSpinnerCommLang.setSelection(commLanguages.indexOf(CommunicationLanguage.HINDI))
    }

    private fun setupVoicePersona() {
        val voices = VoiceOption.ALL_VOICES
        val voiceNames = voices.map { it.displayLabel }
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
        binding.settingsSpinnerVoice.adapter = voiceAdapter

        val savedVoiceId = prefs.getString(KEY_VOICE_ID, VoiceOption.PRIYA.id)
        val currentVoiceIndex = voices.indexOfFirst { it.id == savedVoiceId }.coerceAtLeast(0)
        binding.settingsSpinnerVoice.setSelection(currentVoiceIndex)
        binding.tvVoiceDescription.text = voices[currentVoiceIndex].description

        binding.settingsSpinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = voices[position]
                binding.tvVoiceDescription.text = selected.description
                prefs.edit().putString(KEY_VOICE_ID, selected.id).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnPreviewVoice.setOnClickListener {
            val selectedVoice = voices[binding.settingsSpinnerVoice.selectedItemPosition]
            val previewText = "नमस्ते! iTantra में आपका स्वागत है। यह ${selectedVoice.name} की आवाज़ है।"
            Toast.makeText(this, "Playing preview in ${selectedVoice.name} voice...", Toast.LENGTH_SHORT).show()

            systemTtsEngine.speak(
                text = previewText,
                language = CommunicationLanguage.HINDI,
                voiceOption = selectedVoice
            )
        }
    }

    private fun setupTransportRadio() {
        val isBt = prefs.getBoolean(KEY_TRANSPORT_BT, false)
        if (isBt) {
            binding.rbBluetooth.isChecked = true
            binding.tvTransportHint.text = "Bluetooth Classic P2P: RFCOMM SPP · No Hotspot needed"
        } else {
            binding.rbWifi.isChecked = true
            binding.tvTransportHint.text = "Wi-Fi Hotspot: 192.168.43.1:54321 · Infrastructure-free"
        }

        binding.rgTransport.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbWifi) {
                prefs.edit().putBoolean(KEY_TRANSPORT_BT, false).apply()
                binding.tvTransportHint.text = "Wi-Fi Hotspot: 192.168.43.1:54321 · Infrastructure-free"
            } else if (checkedId == R.id.rbBluetooth) {
                prefs.edit().putBoolean(KEY_TRANSPORT_BT, true).apply()
                binding.tvTransportHint.text = "Bluetooth Classic P2P: RFCOMM SPP · No Hotspot needed"
            }
        }
    }

    private fun setupModelImport() {
        binding.btnImportModel.setOnClickListener {
            val modelNames = ModelManager.AVAILABLE_MODELS.map { "${it.name} (${it.fileName})" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Model to Import")
                .setItems(modelNames) { _, which ->
                    targetImportModelName = ModelManager.AVAILABLE_MODELS[which].fileName
                    importModelLauncher.launch("*/*")
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun populateModelsList() {
        binding.layoutModelsList.removeAllViews()
        val models = modelManager.getAllModelsStatus()
        val inflater = LayoutInflater.from(this)

        for (model in models) {
            val itemView = inflater.inflate(R.layout.item_model_status, binding.layoutModelsList, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvModelName)
            val tvDetails = itemView.findViewById<TextView>(R.id.tvModelDetails)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvModelStatus)
            val btnAction = itemView.findViewById<MaterialButton>(R.id.btnModelAction)
            val layoutProgress = itemView.findViewById<View>(R.id.layoutDownloadProgress)
            val pbDownload = itemView.findViewById<ProgressBar>(R.id.pbModelDownload)
            val tvProgress = itemView.findViewById<TextView>(R.id.tvDownloadPercent)

            tvName.text = model.name
            tvDetails.text = "${model.fileName} · ~${model.expectedSizeMb} MB"

            when {
                model.isInstalled -> {
                    tvStatus.text = getString(R.string.model_installed)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.state_green))
                    btnAction.text = getString(R.string.btn_remove)
                    btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.bg_card_secondary)
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    btnAction.isEnabled = true
                    layoutProgress.visibility = View.GONE

                    btnAction.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Model")
                            .setMessage("Are you sure you want to remove ${model.name} (${model.expectedSizeMb} MB) from device storage?")
                            .setPositiveButton(R.string.btn_remove) { _, _ ->
                                modelManager.deleteModel(model.fileName)
                                populateModelsList()
                            }
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .show()
                    }
                }
                model.isBundled -> {
                    tvStatus.text = getString(R.string.model_bundled)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
                    btnAction.visibility = View.GONE
                    layoutProgress.visibility = View.GONE
                }
                model.downloadState == ModelDownloadState.DOWNLOADING -> {
                    tvStatus.text = "Downloading..."
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
                    btnAction.isEnabled = false
                    btnAction.text = "..."
                    layoutProgress.visibility = View.VISIBLE
                    pbDownload.progress = model.downloadProgress
                    tvProgress.text = "${model.downloadProgress}%"
                }
                else -> {
                    tvStatus.text = getString(R.string.model_not_installed)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.state_orange))
                    btnAction.text = getString(R.string.btn_download)
                    btnAction.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_blue)
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.text_inverse))
                    btnAction.isEnabled = true
                    layoutProgress.visibility = View.GONE

                    btnAction.setOnClickListener {
                        btnAction.isEnabled = false
                        btnAction.text = "Starting..."
                        layoutProgress.visibility = View.VISIBLE
                        pbDownload.progress = 0
                        tvProgress.text = "0%"

                        lifecycleScope.launch {
                            modelManager.downloadModel(
                                model = model,
                                onProgress = { percent, _, _ ->
                                    pbDownload.progress = percent
                                    tvProgress.text = "$percent%"
                                    tvStatus.text = "Downloading $percent%"
                                },
                                onResult = { success, error ->
                                    if (success) {
                                        Toast.makeText(this@SettingsActivity, "${model.name} installed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@SettingsActivity, "Download failed: $error", Toast.LENGTH_LONG).show()
                                    }
                                    populateModelsList()
                                }
                            )
                        }
                    }
                }
            }

            binding.layoutModelsList.addView(itemView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        systemTtsEngine.close()
    }
}
