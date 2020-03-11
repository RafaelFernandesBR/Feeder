package com.nononsenseapps.feeder.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.lifecycleScope
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.base.KodeinAwareFragment
import com.nononsenseapps.feeder.db.room.ID_UNSET
import com.nononsenseapps.feeder.model.FeedItemViewModel
import com.nononsenseapps.feeder.util.Prefs
import com.nononsenseapps.feeder.util.openLinkInBrowser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import org.kodein.di.generic.instance
import org.threeten.bp.Duration

const val ARG_URL = "url"

@FlowPreview
class ReaderWebViewFragment : KodeinAwareFragment() {
    private var webView: WebView? = null
    var url: String = ""
    private var enclosureUrl: String? = null
    private var shareActionProvider: ShareActionProvider? = null
    private var isWebViewAvailable: Boolean = false
    private var feedId: Long = ID_UNSET

    private val prefs: Prefs by instance()
    private val viewModel: FeedItemViewModel by instance(arg = this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { arguments ->
            url = arguments.getString(ARG_URL, null) ?: ""
            enclosureUrl = arguments.getString(ARG_ENCLOSURE, null)
            feedId = arguments.getLong(ARG_FEED_ID, ID_UNSET)
        }

        setHasOptionsMenu(true)

        if (feedId > ID_UNSET) {
            lifecycleScope.launchWhenResumed {
                // Update reading time every 2 seconds
                val time = Duration.ofSeconds(2)
                while (true) {
                    delay(time.toMillis())
                    viewModel.addReadingTimeToFeed(feedId, time)
                }
            }
        }
    }

    /**
     * Called to instantiate the view. Creates and returns the WebView.
     */
    @SuppressLint("SetJavaScriptEnabled", "Recycle")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        webView?.destroy()

        val rootView = inflater.inflate(R.layout.fragment_reader_webview, container, false)

        webView = rootView?.findViewById(R.id.webview)

        // Important to create webview before setting cookie policy on Android18
        CookieManager.getInstance().setAcceptCookie(false)
        webView?.settings?.javaScriptEnabled = prefs.javascriptEnabled
        webView?.settings?.builtInZoomControls = true
        webView?.webViewClient = WebViewClientHandler

        if (url.isNotBlank()) {
            isWebViewAvailable = true
            webView?.loadUrl(url)
        }

        return rootView
    }

    /**
     * Called when the fragment is visible to the user and actively running. Resumes the WebView.
     */
    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    /**
     * Called when the fragment is no longer resumed. Pauses the WebView.
     */
    override fun onResume() {
        webView?.onResume()
        super.onResume()
    }

    /**
     * Called when the WebView has been detached from the fragment.
     * The WebView is no longer available after this time.
     */
    override fun onDestroyView() {
        isWebViewAvailable = false
        super.onDestroyView()
    }

    /**
     * Called when the fragment is no longer in use. Destroys the internal state of the WebView.
     */
    override fun onDestroy() {
        webView?.destroy()
        webView = null
        WebViewClientHandler.onPageStartedListener = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.webview, menu)

        // Locate MenuItem with ShareActionProvider
        val shareItem = menu.findItem(R.id.action_share)

        // Fetch and store ShareActionProvider
        shareActionProvider = MenuItemCompat.getActionProvider(shareItem) as ShareActionProvider

        // Update share intent everytime a page is loaded
        WebViewClientHandler.onPageStartedListener = { url: String? ->
            if (url != null) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, url)
                shareActionProvider?.setShareIntent(shareIntent)
            }
        }
        // Invoke it immediately with current url
        WebViewClientHandler.onPageStartedListener?.invoke(url)

        // Show/Hide enclosure
        menu.findItem(R.id.action_open_enclosure).isVisible = enclosureUrl != null

        // Set state of javascript
        menu.findItem(R.id.action_toggle_javascript)?.let { menuItem ->
            menuItem.setChecked(prefs.javascriptEnabled)
        }

        // Don't forget super call here
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean =
            when (menuItem.itemId) {
                R.id.action_toggle_javascript -> {
                    prefs.javascriptEnabled = (!prefs.javascriptEnabled).also {
                        menuItem.isChecked = it
                        webView?.settings?.javaScriptEnabled = it
                        webView?.reload()
                    }
                    true
                }
                R.id.action_open_in_browser -> {
                    // Use the currently visible page as the url
                    val link = webView?.url ?: url
                    context?.let { context ->
                        openLinkInBrowser(context, link)
                    }

                    true
                }
                R.id.action_open_enclosure -> {
                    enclosureUrl?.let { link ->
                        context?.let { context ->
                            openLinkInBrowser(context, link)
                        }
                    }

                    true
                }
                else -> super.onOptionsItemSelected(menuItem)
            }

    /**
     * Returns true if the web view navigated back, false otherwise
     */
    fun goBack(): Boolean {
        webView?.apply {
            if (canGoBack()) {
                goBack()
                return true
            }
        }
        return false
    }
}

private object WebViewClientHandler : WebViewClient() {
    var onPageStartedListener: ((String?) -> Unit)? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onPageStartedListener?.invoke(url)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        // prevent links from loading in external web browser
        return false
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // prevent links from loading in external web browser
        return false
    }
}

fun View.setPadding(left: Int? = null,
                    top: Int? = null,
                    right: Int? = null,
                    bottom: Int? = null) {
    this.setPadding(left ?: this.paddingLeft,
            top ?: this.paddingTop,
            right ?: this.paddingRight,
            bottom ?: this.paddingBottom)
}
