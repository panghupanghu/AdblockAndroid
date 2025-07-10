package io.github.edsuns.adfilter

import io.github.edsuns.adfilter.util.sha1
import kotlinx.serialization.Serializable

@Serializable
data class Filter internal constructor(
    val url: String,
) {
    val id by lazy { url.sha1 }

    var name: String = ""
        internal set

    var isEnabled: Boolean = false
        internal set

    var downloadState = DownloadState.NONE
        internal set

    var updateTime: Long = -1L
        internal set

    var filtersCount: Int = 0
        internal set

    var checksum: String = ""
        internal set

    fun hasDownloaded() = updateTime > 0
}

enum class DownloadState {
    ENQUEUED, DOWNLOADING, INSTALLING, SUCCESS, FAILED, CANCELLED, NONE;

    val isRunning
        get() = when (this) {
            ENQUEUED, DOWNLOADING, INSTALLING -> true
            else -> false
        }
}