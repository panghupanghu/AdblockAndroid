package io.github.edsuns.adblockclient

data class MatchResult(
    val shouldBlock: Boolean,

    val matchedRule: String?,

    val matchedExceptionRule: String?
)

val MatchResult.hasException: Boolean get() = matchedExceptionRule != null
