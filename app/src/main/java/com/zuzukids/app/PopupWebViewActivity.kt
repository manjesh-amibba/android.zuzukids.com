package com.zuzukids.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.zuzukids.app.R

class PopupWebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup_web_view)

        val webView = findViewById<WebView>(R.id.popupWebView)
        val url = intent.getStringExtra("url") ?: return

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }
}
