package io.github.edsuns.adfilter

import com.tencent.smtt.export.external.interfaces.WebResourceResponse

data class FilterResult(
    val rule: String?,
    val resourceUrl: String,
    val resourceResponse: WebResourceResponse?,
    val shouldBlock: Boolean = rule != null
)
