package com.hardreminder.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hardreminder.BuildConfig
import com.hardreminder.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "About"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.textVersionName.text = BuildConfig.VERSION_NAME
        binding.textVersionCode.text = BuildConfig.VERSION_CODE.toString()
        binding.textPackageName.text = BuildConfig.APPLICATION_ID
    }
}
