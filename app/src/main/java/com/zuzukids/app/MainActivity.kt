package com.zuzukids.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.zuzukids.app.ui.theme.WebToAppsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private var webView: WebView? = null

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("813633489518-etc6natiltntfh5qlisspr5h80711be8.apps.googleusercontent.com")
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        disableScreenshots()
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        val launchUrl = intent?.data?.toString() ?: "https://app.zuzukids.com/"
        
        val css = ""
        val js = ""

        setContent {
            WebToAppsTheme {
                SetSystemBarsColor()
                Column(
                    modifier = Modifier.fillMaxSize().padding(WindowInsets.systemBars.asPaddingValues()), 
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        WebViewContainer(launchUrl, css, js) { wv ->
                            webView = wv
                        }
                    }
                }
            }
        }
    }

    private fun startGoogleLogin() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                sendTokenToBackend(idToken)
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendTokenToBackend(idToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.zuzukids.com/auth/google")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = "{\"google_token\": \"$idToken\"}"
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload)
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Success, handle cookies and reload webview
                    val cookies = conn.headerFields["Set-Cookie"]
                    withContext(Dispatchers.Main) {
                        cookies?.forEach { cookie ->
                            CookieManager.getInstance().setCookie("https://app.zuzukids.com/", cookie)
                        }
                        webView?.reload()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Backend error: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun googleLogin() {
            runOnUiThread {
                startGoogleLogin()
            }
        }
    }
}

@Composable
fun WebViewContainer(url: String, css: String, js: String, onWebViewCreated: (WebView) -> Unit) {
    var wv by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current  
    var lastBackPressTime by remember { mutableStateOf(0L) }
    
    BackHandler(enabled = true) {
        val current = wv
        if (current?.canGoBack() == true) {
            current.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 1500L) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                configureWebView()
                val activity = ctx as? MainActivity
                if (activity != null) {
                    addJavascriptInterface(activity.WebAppInterface(), "Android")
                }
                onWebViewCreated(this)

                if(!NetworkUtils.isOnline(ctx)) {
                    loadUrl("file:///android_asset/no-internet.html")
                } else {
                    loadUrl(url)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->                   
            wv = view
        }
    )
}

fun WebView.configureWebView() {
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            
            val action = getUrlPatterns().firstOrNull { it.regex.matches(url) }?.action
            return when (action) {
                BrowserType.EXTERNAL -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    true
                }
                BrowserType.IN_APP_BROWSER -> {
                    val intent = Intent(context, PopupWebViewActivity::class.java)
                    intent.putExtra("url", url)
                    context.startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
    webChromeClient = WebChromeClient()
    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = true
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
        allowContentAccess = true
        loadWithOverviewMode = true
        useWideViewPort = true
        javaScriptCanOpenWindowsAutomatically = true
        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.7204.180 Mobile Safari/537.36"
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
     
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.MATCH_PARENT
    )
}

fun getUrlPatterns(): List<RegexPatternAction> = listOf()

enum class BrowserType {
    INTERNAL,
    EXTERNAL,
    IN_APP_BROWSER
}

data class RegexPatternAction(val regex: Regex, val action: BrowserType)

fun ComponentActivity.disableScreenshots() {
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
}

fun ComponentActivity.setOrientation(orientation: Int) {
    requestedOrientation = orientation
}
