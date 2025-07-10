package io.github.edsuns.adfilter

import com.tencent.smtt.export.external.interfaces.WebResourceResponse

/**
 * Created by Edsuns@qq.com on 2021/1/24.
 */
data class FilterResult(
    val rule: String?,
    val resourceUrl: String,
    val resourceResponse: WebResourceResponse?,
    val shouldBlock: Boolean = rule != null
)
