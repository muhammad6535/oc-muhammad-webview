package com.muhammad.ocwebview

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
    private var serverUsername: String = DEFAULT_USERNAME
    private var serverPassword: String = ""

    companion object {
        private const val DEFAULT_URL = "https://oc-muhammad-server-production.up.railway.app"
        private const val DEFAULT_USERNAME = "opencode"
        private const val PREFS_NAME = "oc_webview_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "server_username"
        private const val KEY_PASSWORD = "server_password"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        serverUsername = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        serverPassword = prefs.getString(KEY_PASSWORD, "") ?: ""

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

    private fun basicAuthHeader(): String? {
        val u = serverUsername.takeIf { it.isNotBlank() } ?: return null
        val p = serverPassword.takeIf { it.isNotBlank() } ?: return null
        val credentials = "$u:$p"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private fun authHeaders(): Map<String, String> {
        val header = basicAuthHeader() ?: return emptyMap()
        return mapOf("Authorization" to header)
    }

    private fun loadUrlWithAuth(url: String? = null) {
        val target = url ?: serverUrl
        errorView.isVisible = false
        webView.isVisible = true
        val headers = authHeaders()
        if (headers.isNotEmpty()) {
            webView.loadUrl(target, headers)
        } else {
            webView.loadUrl(target)
        }
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
            settings.setSupportMultipleWindows(false)
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

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: android.webkit.HttpAuthHandler?,
                    host: String?,
                    realm: String?
                ) {
                    if (serverUsername.isNotBlank() && serverPassword.isNotBlank()) {
                        handler?.proceed(serverUsername, serverPassword)
                    } else {
                        handler?.cancel()
                    }
                }

                override fun onReceivedError(
                    view: WebView?, errorCode: Int,
                    description: String?, failingUrl: String?
                ) {
                    progressBar.isVisible = false
                    swipeRefresh.isRefreshing = false
                    if (failingUrl == serverUrl || failingUrl == serverUrl.trimEnd('/') + "/") {
                        webView.isVisible = false
                        errorView.isVisible = true
                        errorText.text = getString(
                            R.string.error_connection,
                            serverUrl,
                            description ?: getString(R.string.unknown_error)
                        )
                    }
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
        swipeRefresh.setOnRefreshListener { loadUrlWithAuth() }
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.material_dynamic_primary50
        )
    }

    private fun setupFab() {
        fabSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun loadUrl() {
        loadUrlWithAuth()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            getString(R.string.settings_change_url),
            getString(R.string.settings_credentials),
            getString(R.string.settings_toggle_theme),
            getString(R.string.settings_reload)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_title) + " — OC Muhammad")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showUrlInputDialog()
                    1 -> showCredentialsDialog()
                    2 -> toggleTheme()
                    3 -> loadUrlWithAuth()
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
                    val url = if (newUrl.startsWith("http")) newUrl else "https://$newUrl"
                    serverUrl = url
                    prefs.edit().putString(KEY_SERVER_URL, url).apply()
                    loadUrlWithAuth()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCredentialsDialog() {
        val views = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val usernameInput = views.findViewById<android.widget.EditText>(R.id.credential_username)
        val passwordInput = views.findViewById<android.widget.EditText>(R.id.credential_password)
        usernameInput.setText(serverUsername)
        passwordInput.setText(serverPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.credentials_dialog_title))
            .setView(views)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                serverUsername = usernameInput.text.toString().trim().ifBlank { DEFAULT_USERNAME }
                serverPassword = passwordInput.text.toString()
                prefs.edit()
                    .putString(KEY_USERNAME, serverUsername)
                    .putString(KEY_PASSWORD, serverPassword)
                    .apply()
                loadUrlWithAuth()
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
