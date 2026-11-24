package com.example.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R

object PwaShortcutHelper {

    fun isPinningSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    fun requestPinPwaShortcut(
        context: Context,
        title: String,
        url: String
    ): Boolean {
        if (!isPinningSupported(context)) return false
        val safeTitle = title.ifBlank { Uri.parse(url).host ?: "Web App" }.take(30)
        val shortcutId = "pwa_${url.hashCode()}"
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = BrowserLaunchContract.ACTION_OPEN_PWA
            putExtra(BrowserLaunchContract.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(safeTitle)
            .setLongLabel(safeTitle)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(launchIntent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
