package io.github.edsuns.adblockclient.sample.main

import android.graphics.Bitmap
import com.tencent.smtt.export.external.interfaces.WebResourceRequest
import com.tencent.smtt.export.external.interfaces.WebResourceResponse
import com.tencent.smtt.sdk.URLUtil
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient


import io.github.edsuns.adfilter.AdFilter

class WebClient(private val webViewClientListener: WebViewClientListener) : WebViewClient() {

    private val filter = AdFilter.get()

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request!!.url.toString()
        return !URLUtil.isNetworkUrl(url)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val result = filter.shouldIntercept(view!!, request!!)
        webViewClientListener.onShouldInterceptRequest(result)
        return result.resourceResponse
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        filter.performScript(view, url)
        webViewClientListener.onPageStarted(url, favicon)
    }
}