package com.newera.androidneweraapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var retryLayout: LinearLayout
    private lateinit var lottieView: LottieAnimationView
    private lateinit var retryButton: Button
    private lateinit var splashView: LottieAnimationView
    private lateinit var progressBar: ProgressBar

    private val targetUrl = "https://neweracap.ph"

    // Timeout fallback
    private val pageLoadTimeout: Long = 20000 // 20 seconds
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var isPageLoaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Splash screen
        splashView = LottieAnimationView(this).apply {
            setAnimation("splash.json")
            repeatCount = 0
            playAnimation()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // Spinner
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // WebView
        webView = WebView(this).apply {
            visibility = View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setupWebView()

        // Retry layout
        retryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        lottieView = LottieAnimationView(this).apply {
            setAnimation("no_connection.json")
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
            layoutParams = LinearLayout.LayoutParams(150, 150)
        }

        retryButton = Button(this).apply {
            text = "Try Again"
            textSize = 14f // Smaller text
            setPadding(30, 20, 30, 20) // Tighter padding
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                if (isConnected()) {
                    retryLayout.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    webView.loadUrl(targetUrl)
                }
            }
        }



        retryLayout.addView(lottieView)
        retryLayout.addView(retryButton)

        rootLayout.addView(webView)
        rootLayout.addView(retryLayout)
        rootLayout.addView(splashView)
        rootLayout.addView(progressBar)

        setContentView(rootLayout)

        if (isConnected()) {
            webView.loadUrl(targetUrl)
        } else {
            splashView.visibility = View.GONE
            showRetryLayout()
        }

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

    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    view?.stopLoading()
                    splashView.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    showRetryLayout()
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (splashView.visibility != View.VISIBLE) {
                    progressBar.visibility = View.VISIBLE
                }

                isPageLoaded = false
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                timeoutHandler = Handler(Looper.getMainLooper())
                timeoutRunnable = Runnable {
                    if (!isPageLoaded) {
                        webView.stopLoading()
                        splashView.visibility = View.GONE
                        progressBar.visibility = View.GONE
                        showRetryLayout()
                    }
                }
                timeoutHandler?.postDelayed(timeoutRunnable!!, pageLoadTimeout)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                // Visibility handled in ChromeClient
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    if (splashView.visibility != View.VISIBLE) {
                        progressBar.visibility = View.VISIBLE
                    }
                } else {
                    webView.postDelayed({
                        splashView.visibility = View.GONE
                        progressBar.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                    }, 200)
                }
            }
        }
    }

    private fun isConnected(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showRetryLayout() {
        webView.visibility = View.GONE
        retryLayout.visibility = View.VISIBLE
        lottieView.playAnimation()
    }
}
