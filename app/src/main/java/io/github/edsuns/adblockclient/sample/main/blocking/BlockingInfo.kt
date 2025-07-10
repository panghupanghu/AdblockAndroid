package io.github.edsuns.adblockclient.sample.main.blocking

data class BlockingInfo(
    var allRequests: Int = 0,
    var blockedRequests: Int = 0,
    val blockedUrlMap: LinkedHashMap<String, String> = LinkedHashMap()
)
