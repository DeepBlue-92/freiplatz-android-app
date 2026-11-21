package de.svneuhausen.tennis

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * Feste Ziel-URL der Tennis-Platzreservierung.
         * Kann hier an zentraler Stelle angepasst werden.
         */
        private const val TARGET_URL = "https://platz.sv-neuhausen.de"

        /**
         * Erlaubte Hauptdomain für interne Navigation innerhalb der WebView.
         */
        private const val ALLOWED_DOMAIN = "sv-neuhausen.de"
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineContainer: LinearLayout
    private lateinit var btnRetry: Button

    private var hasPageLoadError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Nativer Splash Screen (Android 12+ API mit Fallback)
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        initViews()
        setupBackNavigation()
        setupCookies()
        setupWebView()
        setupSwipeRefresh()
        setupOfflineRetry()

        // Initiale Seite laden
        loadTargetUrl()
    }

    /**
     * Konfiguriert Edge-to-Edge: Die App nutzt 100% der Bildschirmhöhe.
     * Statusleiste transparent mit hellen Icons (für die dunkelgrüne Vereins-Kopfzeile),
     * Navigationsleiste weiß mit dunklen System-Icons.
     */
    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Helle Symbole in der Statusleiste (oben), da die Kopfzeile der Website dunkelgrün (#16422E) ist
        windowInsetsController.isAppearanceLightStatusBars = false
        // Dunkle Symbole in der Navigationsleiste (unten) auf weißem Hintergrund
        windowInsetsController.isAppearanceLightNavigationBars = true
    }

    private fun initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        offlineContainer = findViewById(R.id.offlineContainer)
        btnRetry = findViewById(R.id.btnRetry)
    }

    /**
     * Behandelt den Android-Zurück-Button sauber:
     * Geht in der Historie der WebView zurück, schließt die App erst auf der Startseite.
     */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * Aktiviert Cookie-Unterstützung (inkl. Third-Party-Cookies),
     * essentiell für Firebase-Auth und persistente PWA-Logins.
     */
    private fun setupCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // WebChromeClient steuert den oberen horizontalen Fortschrittsbalken (Variante A)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        // WebViewClient steuert Navigation, externe Links und Fehlerbehandlung
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return handleUrlNavigation(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = Uri.parse(url ?: return false)
                return handleUrlNavigation(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasPageLoadError = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Nur Hauptseiten-Fehler führen zum Offline-Screen
                if (request?.isForMainFrame == true) {
                    hasPageLoadError = true
                    showOfflineScreen()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                if (!hasPageLoadError) {
                    showWebScreen()
                }
            }
        }

        // DownloadListener für PDF-, Excel- (xlsx) oder CSV-Exporte (z.B. für Admin-Statistiken)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    setDescription(getString(R.string.download_started))
                    setTitle(filename)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Fallback: Externe Browser-App zum Download öffnen
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
        }
    }

    /**
     * Filtert URLs:
     * - sv-neuhausen.de (und Subdomains wie buchen.sv-neuhausen.de) -> in WebView anzeigen
     * - mailto:, tel:, externe Websites -> im externen System-Browser bzw. Telefon-App öffnen
     */
    private fun handleUrlNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host?.lowercase() ?: ""

        // Spezielle Schemas abfangen (Telefon, E-Mail)
        if (scheme == "tel" || scheme == "mailto") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                // Keine passende App installiert
            }
            return true
        }

        // Interne Domains direkt in der WebView anzeigen
        if (host == ALLOWED_DOMAIN || host.endsWith(".$ALLOWED_DOMAIN")) {
            return false
        }

        // Alle externen Links im Standard-Browser des Nutzers öffnen
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            // Ignorieren falls kein Browser verfügbar
        }
        return true
    }

    /**
     * Konfiguriert Pull-to-Refresh:
     * Nur aktivierbar, wenn ganz nach oben gescrollt wurde.
     */
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.club_green)
        swipeRefreshLayout.setOnRefreshListener {
            if (isNetworkAvailable()) {
                webView.reload()
            } else {
                swipeRefreshLayout.isRefreshing = false
                showOfflineScreen()
            }
        }

        // Verhindert versehentliches Auslösen beim Hochscrollen innerhalb der Seite
        webView.viewTreeObserver.addOnScrollChangedListener {
            swipeRefreshLayout.isEnabled = (webView.scrollY == 0)
        }
    }

    private fun setupOfflineRetry() {
        btnRetry.setOnClickListener {
            if (isNetworkAvailable()) {
                showWebScreen()
                if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                    webView.loadUrl(TARGET_URL)
                } else {
                    webView.reload()
                }
            } else {
                Toast.makeText(this, R.string.offline_title, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTargetUrl() {
        if (isNetworkAvailable()) {
            showWebScreen()
            webView.loadUrl(TARGET_URL)
        } else {
            showOfflineScreen()
        }
    }

    private fun showOfflineScreen() {
        swipeRefreshLayout.visibility = View.GONE
        offlineContainer.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }

    private fun showWebScreen() {
        offlineContainer.visibility = View.GONE
        swipeRefreshLayout.visibility = View.VISIBLE
    }

    /**
     * Prüft die Verfügbarkeit einer aktiven Internetverbindung.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
