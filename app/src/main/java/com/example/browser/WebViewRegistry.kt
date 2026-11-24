package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.BrowserSettings

interface WebViewCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onReceivedTitle(tabId: String, title: String)
    fun onReceivedIcon(tabId: String, icon: Bitmap?)
    fun onHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onOpenNewTabRequested(url: String)
    fun onAdBlocked(tabId: String, blockedUrl: String)
    fun onConsoleMessage(tabId: String, entry: ConsoleLogEntry)
    fun onDownloadRequested(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    )
    fun onGeolocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback
    )
}

class WebViewRegistry {
    private val webViews = mutableMapOf<String, DesktopWebView>()
    private val contextWrappers = mutableMapOf<String, MutableContextWrapper>()
    private val documentStartHandlers = mutableMapOf<String, ScriptHandler>()
    private val tabDesktopModes = mutableMapOf<String, Boolean>()
    private val tabIncognitoModes = mutableMapOf<String, Boolean>()

    @Volatile
    private var currentSettings: BrowserSettings = BrowserSettings()

    @Volatile
    private var viewportWidthDp: Float? = null

    @Volatile
    private var viewportHeightDp: Float? = null

    fun get(tabId: String): DesktopWebView? = webViews[tabId]

    fun updateSettingsSnapshot(settings: BrowserSettings) {
        currentSettings = settings
    }

    fun updateViewportSizeDp(widthDp: Float, heightDp: Float) {
        if (widthDp <= 100f || heightDp <= 100f) return
        val prevW = viewportWidthDp
        val prevH = viewportHeightDp
        val changed = prevW == null || prevH == null ||
            kotlin.math.abs(widthDp - prevW) > 2f ||
            kotlin.math.abs(heightDp - prevH) > 2f
        viewportWidthDp = widthDp
        viewportHeightDp = heightDp
        if (changed) {
            webViews.forEach { (tabId, webView) ->
                val isDesktop = tabDesktopModes[tabId] ?: true
                if (isDesktop) {
                    val fitScale = computeFitScale(webView.context, currentSettings.desktopViewportWidthPx)
                    val desktopH = computeDesktopHeightPx(webView.context, currentSettings.desktopViewportWidthPx)
                    webView.setInitialScale((fitScale * 100).toInt().coerceIn(15, 100))
                    syncDocumentStartViewportScript(tabId, webView, true)
                    if (currentSettings.javaScriptEnabled) {
                        webView.evaluateJavascript(
                            BrowserScripts.buildDesktopViewportScript(
                                currentSettings.desktopViewportWidthPx,
                                fitScale,
                                desktopH
                            ),
                            null
                        )
                    }
                }
            }
        }
    }

    private fun computeFitScale(context: Context, desktopWidthPx: Int): Float {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val containerDp = viewportWidthDp ?: (context.resources.displayMetrics.widthPixels / density)
        return (containerDp / desktopWidthPx.toFloat()).coerceIn(0.15f, 1.0f)
    }

    private fun computeDesktopHeightPx(context: Context, desktopWidthPx: Int): Int {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        val containerW = viewportWidthDp ?: (metrics.widthPixels / density)
        val containerH = viewportHeightDp ?: (metrics.heightPixels / density)
        val aspect = (containerH / containerW.coerceAtLeast(1f)).coerceIn(0.4f, 3.0f)
        return (desktopWidthPx * aspect).toInt().coerceIn(480, 6000)
    }

    private fun syncDocumentStartViewportScript(tabId: String, webView: DesktopWebView, isDesktopMode: Boolean) {
        documentStartHandlers.remove(tabId)?.remove()
        if (!isDesktopMode) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val fitScale = computeFitScale(webView.context, currentSettings.desktopViewportWidthPx)
            val desktopH = computeDesktopHeightPx(webView.context, currentSettings.desktopViewportWidthPx)
            val script = BrowserScripts.buildDesktopViewportScript(
                currentSettings.desktopViewportWidthPx,
                fitScale,
                desktopH
            )
            runCatching {
                val handler = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
                documentStartHandlers[tabId] = handler
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(
        context: Context,
        tabId: String,
        initialUrl: String,
        isDesktopMode: Boolean,
        isIncognito: Boolean = false,
        settingsSnapshot: BrowserSettings,
        callbacks: WebViewCallbacks
    ): DesktopWebView {
        currentSettings = settingsSnapshot
        tabDesktopModes[tabId] = isDesktopMode
        tabIncognitoModes[tabId] = isIncognito
        webViews[tabId]?.let { existing ->
            contextWrappers[tabId]?.baseContext = context
            applySettingsToWebView(existing, existing.settings, isDesktopMode, settingsSnapshot, isIncognito)
            return existing
        }

        val contextWrapper = MutableContextWrapper(context)
        contextWrappers[tabId] = contextWrapper

        val webView = DesktopWebView(contextWrapper).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            CookieManager.getInstance().setAcceptCookie(!isIncognito)
            configureSettings(settings, isDesktopMode, currentSettings, isIncognito)
            applySettingsToWebView(this, settings, isDesktopMode, currentSettings, isIncognito)
            if (isDesktopMode) {
                val fitScale = computeFitScale(context, currentSettings.desktopViewportWidthPx)
                setInitialScale((fitScale * 100).toInt().coerceIn(15, 100))
            } else {
                setInitialScale(0)
            }
            syncDocumentStartViewportScript(tabId, this, isDesktopMode)

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                if (!url.isNullOrBlank()) {
                    callbacks.onDownloadRequested(
                        url = url,
                        userAgent = userAgent.orEmpty(),
                        contentDisposition = contentDisposition.orEmpty(),
                        mimeType = mimetype.orEmpty()
                    )
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString()
                    val isMainFrame = request?.isForMainFrame == true
                    if (AdBlocker.shouldBlock(reqUrl, isMainFrame, currentSettings.adBlockerEnabled)) {
                        callbacks.onAdBlocked(tabId, reqUrl.orEmpty())
                        return AdBlocker.emptyResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "http" || scheme == "https" || scheme == "about") {
                        return false
                    }
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        contextWrapper.startActivity(intent)
                        true
                    } catch (_: Exception) {
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!url.isNullOrBlank()) {
                        callbacks.onPageStarted(tabId, url)
                    }
                    val activeSettings = currentSettings
                    val desktopActive = tabDesktopModes[tabId] ?: true
                    if (activeSettings.javaScriptEnabled && view != null) {
                        if (desktopActive) {
                            val fitScale = computeFitScale(view.context, activeSettings.desktopViewportWidthPx)
                            val desktopH = computeDesktopHeightPx(view.context, activeSettings.desktopViewportWidthPx)
                            view.evaluateJavascript(
                                BrowserScripts.buildDesktopViewportScript(
                                    activeSettings.desktopViewportWidthPx,
                                    fitScale,
                                    desktopH
                                ),
                                null
                            )
                        }
                        if (activeSettings.mockLocationEnabled) {
                            view.evaluateJavascript(
                                BrowserScripts.buildGeolocationShim(
                                    activeSettings.mockLatitude,
                                    activeSettings.mockLongitude
                                ),
                                null
                            )
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val activeSettings = currentSettings
                    val desktopActive = tabDesktopModes[tabId] ?: true
                    if (activeSettings.javaScriptEnabled && view != null) {
                        if (desktopActive) {
                            val fitScale = computeFitScale(view.context, activeSettings.desktopViewportWidthPx)
                            val desktopH = computeDesktopHeightPx(view.context, activeSettings.desktopViewportWidthPx)
                            view.evaluateJavascript(
                                BrowserScripts.buildDesktopViewportScript(
                                    activeSettings.desktopViewportWidthPx,
                                    fitScale,
                                    desktopH
                                ),
                                null
                            )
                        }
                        if (activeSettings.mockLocationEnabled) {
                            view.evaluateJavascript(
                                BrowserScripts.buildGeolocationShim(
                                    activeSettings.mockLatitude,
                                    activeSettings.mockLongitude
                                ),
                                null
                            )
                        }
                    }
                    val resolvedUrl = url ?: view?.url.orEmpty()
                    callbacks.onPageFinished(
                        tabId = tabId,
                        url = resolvedUrl,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false
                    )
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    callbacks.onHistoryUpdated(
                        tabId = tabId,
                        url = url ?: view?.url.orEmpty(),
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false
                    )
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detail?.didCrash() == true
                    } else {
                        false
                    }
                    Log.w(TAG, "WebView render process gone for tab=$tabId didCrash=$didCrash")
                    val lastUrl = view?.url ?: initialUrl
                    destroy(tabId)
                    if (lastUrl.isNotBlank()) {
                        callbacks.onPageFinished(
                            tabId = tabId,
                            url = lastUrl,
                            canGoBack = false,
                            canGoForward = false
                        )
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    callbacks.onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        callbacks.onReceivedTitle(tabId, title)
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    callbacks.onReceivedIcon(tabId, icon)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        callbacks.onConsoleMessage(tabId, ConsoleLogEntry.fromWebConsole(consoleMessage))
                    }
                    return super.onConsoleMessage(consoleMessage)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (callback == null) return
                    val safeOrigin = origin.orEmpty()
                    if (currentSettings.mockLocationEnabled) {
                        callback.invoke(safeOrigin, true, false)
                    } else {
                        callbacks.onGeolocationPermissionRequested(safeOrigin, callback)
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val hitUrl = view?.hitTestResult?.extra
                    if (!hitUrl.isNullOrBlank()) {
                        callbacks.onOpenNewTabRequested(hitUrl)
                        return false
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val tempWebView = WebView(view?.context ?: return false)
                    tempWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString()
                            if (!targetUrl.isNullOrBlank()) {
                                callbacks.onOpenNewTabRequested(targetUrl)
                            }
                            tempWebView.destroy()
                            return true
                        }
                    }
                    transport.webView = tempWebView
                    resultMsg.sendToTarget()
                    return true
                }
            }

            if (initialUrl.isNotBlank()) {
                post {
                    if (url == null) {
                        loadUrl(initialUrl)
                    }
                }
            }
        }

        webViews[tabId] = webView
        return webView
    }

    fun applySettingsToAll(
        settings: BrowserSettings,
        tabModes: Map<String, Boolean>,
        reloadActiveTabId: String? = null
    ) {
        currentSettings = settings
        webViews.forEach { (tabId, webView) ->
            val isDesktop = tabModes[tabId] ?: true
            val isIncognito = tabIncognitoModes[tabId] ?: false
            tabDesktopModes[tabId] = isDesktop
            applySettingsToWebView(webView, webView.settings, isDesktop, settings, isIncognito)
            syncDocumentStartViewportScript(tabId, webView, isDesktop)
            if (isDesktop) {
                val fitScale = computeFitScale(webView.context, settings.desktopViewportWidthPx)
                val desktopH = computeDesktopHeightPx(webView.context, settings.desktopViewportWidthPx)
                webView.setInitialScale((fitScale * 100).toInt().coerceIn(15, 100))
                if (settings.javaScriptEnabled) {
                    webView.evaluateJavascript(
                        BrowserScripts.buildDesktopViewportScript(
                            settings.desktopViewportWidthPx,
                            fitScale,
                            desktopH
                        ),
                        null
                    )
                }
            } else {
                webView.setInitialScale(0)
            }
            if (settings.mockLocationEnabled && settings.javaScriptEnabled) {
                webView.evaluateJavascript(
                    BrowserScripts.buildGeolocationShim(
                        settings.mockLatitude,
                        settings.mockLongitude
                    ),
                    null
                )
            }
            if (reloadActiveTabId == tabId) {
                webView.reload()
            }
        }
    }

    fun evaluateScript(
        tabId: String,
        code: String,
        asEsModule: Boolean,
        onResult: (String) -> Unit
    ) {
        val webView = webViews[tabId]
        if (webView == null) {
            onResult("No active WebView attached for this tab.")
            return
        }
        val scriptToRun = if (asEsModule) {
            BrowserScripts.buildEsModuleInjectionScript(code)
        } else {
            code
        }
        webView.evaluateJavascript(scriptToRun) { rawResult ->
            onResult(rawResult ?: "undefined")
        }
    }

    fun evaluateRawJs(
        tabId: String,
        script: String,
        onResult: (String?) -> Unit
    ) {
        val webView = webViews[tabId]
        if (webView == null) {
            onResult(null)
            return
        }
        webView.evaluateJavascript(script) { rawResult ->
            onResult(rawResult)
        }
    }

    fun captureScreenshot(tabId: String): Bitmap? {
        val webView = webViews[tabId] ?: return null
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun findInPage(tabId: String, query: String) {
        val webView = webViews[tabId] ?: return
        if (query.isBlank()) {
            webView.clearMatches()
        } else {
            webView.findAllAsync(query)
        }
    }

    fun findNext(tabId: String, forward: Boolean) {
        webViews[tabId]?.findNext(forward)
    }

    fun clearFindInPage(tabId: String) {
        webViews[tabId]?.clearMatches()
    }

    fun clearAllBrowsingData() {
        webViews.values.forEach { webView ->
            webView.clearCache(true)
            webView.clearFormData()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    fun detachContext(tabId: String) {
        val wrapper = contextWrappers[tabId] ?: return
        wrapper.baseContext = wrapper.applicationContext
    }

    fun applyDesktopMode(tabId: String, isDesktopMode: Boolean, reload: Boolean = true) {
        tabDesktopModes[tabId] = isDesktopMode
        val webView = webViews[tabId] ?: return
        configureViewportAndUserAgent(webView.settings, isDesktopMode)
        syncDocumentStartViewportScript(tabId, webView, isDesktopMode)
        if (isDesktopMode) {
            val fitScale = computeFitScale(webView.context, currentSettings.desktopViewportWidthPx)
            webView.setInitialScale((fitScale * 100).toInt().coerceIn(15, 100))
        } else {
            webView.setInitialScale(0)
            if (currentSettings.javaScriptEnabled) {
                webView.evaluateJavascript(BrowserScripts.buildMobileViewportRestoreScript(), null)
            }
        }
        if (reload) {
            webView.reload()
        }
    }

    fun loadUrl(tabId: String, url: String) {
        webViews[tabId]?.loadUrl(url)
    }

    fun goBack(tabId: String): Boolean {
        val webView = webViews[tabId] ?: return false
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun goForward(tabId: String): Boolean {
        val webView = webViews[tabId] ?: return false
        if (webView.canGoForward()) {
            webView.goForward()
            return true
        }
        return false
    }

    fun reload(tabId: String) {
        webViews[tabId]?.reload()
    }

    fun stopLoading(tabId: String) {
        webViews[tabId]?.stopLoading()
    }

    fun destroy(tabId: String) {
        documentStartHandlers.remove(tabId)?.remove()
        tabDesktopModes.remove(tabId)
        val wasIncognito = tabIncognitoModes.remove(tabId) == true
        contextWrappers.remove(tabId)
        val webView = webViews.remove(tabId) ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.clearHistory()
        webView.clearFormData()
        if (wasIncognito) {
            webView.clearCache(true)
            val hasOtherIncognito = tabIncognitoModes.values.any { it }
            if (!hasOtherIncognito) {
                CookieManager.getInstance().setAcceptCookie(true)
            }
        }
        webView.removeAllViews()
        webView.destroy()
    }

    fun destroyAll() {
        val ids = webViews.keys.toList()
        ids.forEach { destroy(it) }
    }

    companion object {
        private const val TAG = "WebViewRegistry"

        @SuppressLint("SetJavaScriptEnabled")
        @Suppress("DEPRECATION")
        fun configureSettings(
            settings: WebSettings,
            isDesktopMode: Boolean,
            browserSettings: BrowserSettings = BrowserSettings(),
            isIncognito: Boolean = false
        ) {
            settings.javaScriptEnabled = browserSettings.javaScriptEnabled
            settings.domStorageEnabled = !isIncognito
            settings.saveFormData = !isIncognito
            settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            configureViewportAndUserAgent(settings, isDesktopMode)
            applyForceDark(settings, browserSettings.forceDarkWebPages)
        }

        @Suppress("DEPRECATION")
        fun applySettingsToWebView(
            webView: DesktopWebView,
            settings: WebSettings,
            isDesktopMode: Boolean,
            browserSettings: BrowserSettings,
            isIncognito: Boolean = false
        ) {
            webView.disableSoftKeyboard = browserSettings.disableSoftKeyboard
            settings.javaScriptEnabled = browserSettings.javaScriptEnabled
            settings.domStorageEnabled = !isIncognito
            settings.saveFormData = !isIncognito
            settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView,
                !isIncognito && !browserSettings.blockThirdPartyCookies
            )
            configureViewportAndUserAgent(settings, isDesktopMode)
            applyForceDark(settings, browserSettings.forceDarkWebPages)
        }

        @Suppress("DEPRECATION")
        fun applyForceDark(settings: WebSettings, enabled: Boolean) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    settings,
                    if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
        }

        fun configureViewportAndUserAgent(settings: WebSettings, isDesktopMode: Boolean) {
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = isDesktopMode
            settings.userAgentString = if (isDesktopMode) {
                BrowserConfig.DESKTOP_USER_AGENT
            } else {
                BrowserConfig.MOBILE_USER_AGENT
            }
        }
    }
}
