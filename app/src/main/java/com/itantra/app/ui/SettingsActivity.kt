package com.itantra.app.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.itantra.app.R
import com.itantra.app.data.AppLanguage
import com.itantra.app.data.CommunicationLanguage
import com.itantra.app.databinding.ActivitySettingsBinding
import com.itantra.app.localization.AppLanguageManager
import com.itantra.app.ml.ModelManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var modelManager: ModelManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(this)

        setupToolbar()
        setupLanguageSpinners()
        setupTransportRadio()
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

    private fun setupTransportRadio() {
        binding.rgTransport.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbWifi) {
                // Wi-Fi
            } else if (checkedId == R.id.rbBluetooth) {
                // Bluetooth
            }
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

            tvName.text = model.name
            tvDetails.text = "${model.fileName} · ~${model.expectedSizeMb} MB"

            if (model.isInstalled) {
                tvStatus.text = getString(R.string.model_installed)
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.state_green))
            } else if (model.isBundled) {
                tvStatus.text = getString(R.string.model_bundled)
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
            } else {
                tvStatus.text = getString(R.string.model_not_installed)
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.state_orange))
            }

            binding.layoutModelsList.addView(itemView)
        }
    }
}
