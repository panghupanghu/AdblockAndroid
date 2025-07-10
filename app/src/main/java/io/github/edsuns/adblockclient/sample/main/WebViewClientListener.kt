package io.github.edsuns.adblockclient.sample.main

import android.graphics.Bitmap
import io.github.edsuns.adfilter.FilterResult

interface WebViewClientListener {
    fun onPageStarted(url: String?, favicon: Bitmap?)
    fun progressChanged(newProgress: Int)
    fun onShouldInterceptRequest(result: FilterResult)
}