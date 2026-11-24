package com.example.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.BrowserSettings
import java.util.concurrent.Executor

object NetworkAndProxyHelper {

    fun isSystemVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun applyProxySettings(
        settings: BrowserSettings,
        onComplete: (success: Boolean, message: String?) -> Unit
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onComplete(false, "WebView ProxyController is not supported on this device.")
            return
        }

        val executor = Executor { command -> command.run() }
        if (!settings.proxyEnabled) {
            ProxyController.getInstance().clearProxyOverride(executor) {
                onComplete(true, null)
            }
            return
        }

        val host = settings.proxyHost.trim()
        val port = settings.proxyPort.trim().toIntOrNull()
        if (host.isEmpty() || port == null || port !in 1..65535) {
            onComplete(false, "Invalid proxy host or port.")
            return
        }

        val proxyUrl = "${settings.proxyScheme.prefix}$host:$port"
        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule(proxyUrl)
            .build()

        ProxyController.getInstance().setProxyOverride(proxyConfig, executor) {
            onComplete(true, "Proxy active: $proxyUrl")
        }
    }
}
