package com.aegis.apa.model

/** Current app identity check, independent of whether a device exposes battery nodes. */
enum class RootAccessState {
    UNKNOWN, CHECKING, AUTHORIZED, DENIED, TIMED_OUT, UNAVAILABLE, FAILED
}
