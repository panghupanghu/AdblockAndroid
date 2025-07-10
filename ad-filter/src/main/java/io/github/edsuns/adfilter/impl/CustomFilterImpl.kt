package io.github.edsuns.adfilter.impl

import io.github.edsuns.adfilter.CustomFilter
import io.github.edsuns.adfilter.util.RuleIterator

internal class CustomFilterImpl constructor(
    private val filterDataLoader: FilterDataLoader,
    data: String? = null
) : CustomFilter, RuleIterator(data) {

    override fun flush() {
        val blacklistStr = dataBuilder.toString()
        if (blacklistStr.isNotBlank()) {
            val rawData = blacklistStr.toByteArray()
            filterDataLoader.loadCustomFilter(rawData)
        }
    }
}