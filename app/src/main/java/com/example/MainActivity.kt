package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ai.configureAppCheck
import com.example.system.BrowserLaunchContract
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.theme.PCViewPortTheme

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels {
        BrowserViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must configure Firebase App Check with launch Intent before any UI or ViewModel usage
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            PCViewPortTheme {
                BrowserScreen(viewModel = browserViewModel)
            }
        }

        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            BrowserLaunchContract.ACTION_NEW_INCOGNITO -> {
                browserViewModel.openNewTab(isIncognito = true)
            }
            BrowserLaunchContract.ACTION_AI_ANALYZE -> {
                browserViewModel.openAiAnalystPage()
            }
            BrowserLaunchContract.ACTION_DAILY_NEWS -> {
                browserViewModel.openNewsPage()
            }
            BrowserLaunchContract.ACTION_OPEN_PWA -> {
                val url = intent.getStringExtra(BrowserLaunchContract.EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    browserViewModel.openNewTab(url = url, select = true)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUrl = intent.dataString
                if (!dataUrl.isNullOrBlank()) {
                    browserViewModel.openNewTab(url = dataUrl, select = true)
                }
            }
        }
    }
}
