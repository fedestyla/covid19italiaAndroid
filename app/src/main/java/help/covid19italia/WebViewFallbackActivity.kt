package help.covid19italia

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.androidbrowserhelper.trusted.LauncherActivityMetadata
import java.util.*


class WebViewFallbackActivity : AppCompatActivity() {
    private var mLaunchUrl: Uri? = null
    private var mStatusBarColor = 0
    private var mWebView: WebView? = null
    private val mExtraOrigins: MutableList<Uri> =
        ArrayList()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mLaunchUrl = this.intent
            .getParcelableExtra(KEY_LAUNCH_URI)
        require("https" == mLaunchUrl!!.scheme) { "launchUrl scheme must be 'https'" }

        setupFullscreenMode()

        if (intent.hasExtra(KEY_EXTRA_ORIGINS)) {
            val extraOrigins: List<String>? =
                intent.getStringArrayListExtra(KEY_EXTRA_ORIGINS)
            if (extraOrigins != null) {
                for (extraOrigin in extraOrigins) {
                    val extraOriginUri = Uri.parse(extraOrigin)
                    if (!"https".equals(extraOriginUri.scheme, ignoreCase = true)) {
                        Log.w(
                            TAG,
                            "Only 'https' origins are accepted. Ignoring extra origin: "
                                    + extraOrigin
                        )
                        continue
                    }
                    mExtraOrigins.add(extraOriginUri)
                }
            }
        }
        mWebView = WebView(this)
        mWebView!!.webViewClient = createWebViewClient()
        val webSettings = mWebView!!.settings
        setupWebSettings(webSettings)
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(mWebView, layoutParams)
        if (savedInstanceState != null) {
            mWebView!!.restoreState(savedInstanceState)
            return
        }
        mWebView!!.loadUrl(mLaunchUrl.toString())
    }

    private fun setupFullscreenMode() {
        val decorView = setFullscreen()
        decorView
            .setOnSystemUiVisibilityChangeListener { setFullscreen() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setFullscreen()
        }
    }

    private fun setFullscreen(): View {
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        return decorView
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView!!.canGoBack()) {
            mWebView!!.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        if (mWebView != null) {
            mWebView!!.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mWebView != null) {
            mWebView!!.onResume()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mWebView != null) {
            mWebView!!.saveState(outState)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onRenderProcessGone(
                view: WebView, detail: RenderProcessGoneDetail
            ): Boolean {
                val vg = view.parent as ViewGroup

                // Remove crashed WebView from the hierarchy
                // and ensure it is destroyed.
                vg.removeView(view)
                view.destroy()

                // Create a new instance, and ensure it also
                // handles crashes - in this case, re-using
                // the current WebViewClient
                mWebView = WebView(view.context)
                mWebView!!.webViewClient = this
                val webSettings = mWebView!!.settings
                setupWebSettings(webSettings)
                vg.addView(mWebView)

                // With the crash recovered, decide what to do next.
                // We are sending a toast and loading the origin
                // URL, in this example.
                Toast.makeText(
                    view.context, "Recovering from crash",
                    Toast.LENGTH_LONG
                ).show()
                mWebView!!.loadUrl(mLaunchUrl.toString())
                return true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val launchUrl = mLaunchUrl
                val navigationUrl = request.url

                // If the user is navigation to a different origin, use CCT to handle the navigation
                //
                // URIs with the `data` scheme are handled in the WebView.
                // The "Demo" item in https://jakearchibald.github.io/svgomg/ is one example of this
                // usage
                if ("data" != navigationUrl.scheme &&
                    !uriOriginsMatch(navigationUrl, launchUrl) &&
                    !matchExtraOrigins(navigationUrl)
                ) {
                    val intent = CustomTabsIntent.Builder()
                        .setToolbarColor(mStatusBarColor)
                        .build()
                    intent.launchUrl(this@WebViewFallbackActivity, navigationUrl)
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            private fun matchExtraOrigins(navigationUri: Uri): Boolean {
                for (uri in mExtraOrigins) {
                    if (uriOriginsMatch(uri, navigationUri)) {
                        return true
                    }
                }
                return false
            }

            private fun uriOriginsMatch(
                uriA: Uri,
                uriB: Uri?
            ): Boolean {
                return uriA.scheme.equals(uriB!!.scheme, ignoreCase = true) &&
                        uriA.host.equals(uriB.host, ignoreCase = true) && uriA.port == uriB.port
            }
        }
    }

    companion object {
        private val TAG = WebViewFallbackActivity::class.java.simpleName
        private const val KEY_PREFIX =
            "com.google.browser.examples.twawebviewfallback.WebViewFallbackActivity."
        private const val KEY_LAUNCH_URI =
            KEY_PREFIX + "LAUNCH_URL"
        private const val KEY_NAVIGATION_BAR_COLOR =
            KEY_PREFIX + "KEY_NAVIGATION_BAR_COLOR"
        private const val KEY_STATUS_BAR_COLOR =
            KEY_PREFIX + "KEY_STATUS_BAR_COLOR"
        private const val KEY_EXTRA_ORIGINS =
            KEY_PREFIX + "KEY_EXTRA_ORIGINS"

        fun createLaunchIntent(
            context: Context?,
            launchUrl: Uri?,
            launcherActivityMetadata: LauncherActivityMetadata
        ): Intent {
            val intent = Intent(context, WebViewFallbackActivity::class.java)
            intent.putExtra(KEY_LAUNCH_URI, launchUrl)
            intent.putExtra(
                KEY_STATUS_BAR_COLOR,
                ContextCompat.getColor(context!!, launcherActivityMetadata.statusBarColorId)
            )
            intent.putExtra(
                KEY_NAVIGATION_BAR_COLOR,
                ContextCompat.getColor(context, launcherActivityMetadata.navigationBarColorId)
            )
            if (launcherActivityMetadata.additionalTrustedOrigins != null) {
                val extraOrigins =
                    ArrayList(launcherActivityMetadata.additionalTrustedOrigins)
                intent.putStringArrayListExtra(
                    KEY_EXTRA_ORIGINS,
                    extraOrigins
                )
            }
            return intent
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun setupWebSettings(webSettings: WebSettings) {
            // Those settings are disabled by default.
            webSettings.javaScriptEnabled = true
            webSettings.domStorageEnabled = true
            webSettings.databaseEnabled = true
        }
    }
}