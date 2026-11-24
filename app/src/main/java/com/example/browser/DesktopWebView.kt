package com.example.browser

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

class DesktopWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    @Volatile
    var disableSoftKeyboard: Boolean = false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (disableSoftKeyboard) {
            return null
        }
        return super.onCreateInputConnection(outAttrs)
    }
}
