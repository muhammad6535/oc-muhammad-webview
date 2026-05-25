package com.muhammad.ocwebview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorText: TextView
    private lateinit var fabSettings: FloatingActionButton
    private lateinit var prefs: SharedPreferences

    private var serverUrl: String = DEFAULT_URL

    companion object {
        private const val DEFAULT_URL = "http://127.0.0.1:4096"
        private const val PREFS_NAME = "oc_webview_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        progressBar = findViewById(R.id.progress_bar)
        errorView = findViewById(R.id.error_view)
        errorText = findViewById(R.id.error_text)
        fabSettings = findViewById(R.id.fab_settings)

        setupWebView()
        setupSwipeRefresh()
        setupFab()
        loadUrl()
    }

    private fun applyTheme() {
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, true)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.isVisible = true
                    errorView.isVisible = false
                    webView.isVisible = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.isVisible = false
                    swipeRefresh.isRefreshing = false
                }

                override fun onReceivedError(
                    view: WebView?, errorCode: Int,
                    description: String?, failingUrl: String?
                ) {
                    progressBar.isVisible = false
                    swipeRefresh.isRefreshing = false
                    webView.isVisible = false
                    errorView.isVisible = true
                    errorText.text = getString(
                        R.string.error_connection,
                        serverUrl,
                        description ?: getString(R.string.unknown_error)
                    )
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.material_dynamic_primary50
        )
    }

    private fun setupFab() {
        fabSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun loadUrl() {
        errorView.isVisible = false
        webView.isVisible = true
        webView.loadUrl(serverUrl)
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            getString(R.string.settings_change_url),
            getString(R.string.settings_toggle_theme),
            getString(R.string.settings_reload)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_title))
            .setIcon(R.drawable.ic_settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showUrlInputDialog()
                    1 -> toggleTheme()
                    2 -> webView.reload()
                }
            }
            .show()
    }

    private fun showUrlInputDialog() {
        val input = android.widget.EditText(this).apply {
            setText(serverUrl)
            hint = DEFAULT_URL
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.url_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotBlank()) {
                    val url = if (newUrl.startsWith("http")) newUrl else "http://$newUrl"
                    serverUrl = url
                    prefs.edit().putString(KEY_SERVER_URL, url).apply()
                    loadUrl()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleTheme() {
        val currentDark = prefs.getBoolean(KEY_DARK_MODE, true)
        prefs.edit().putBoolean(KEY_DARK_MODE, !currentDark).apply()
        applyTheme()
        Toast.makeText(this, if (!currentDark) R.string.dark_mode_on else R.string.light_mode_on, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
