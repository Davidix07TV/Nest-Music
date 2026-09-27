package com.nestmusic.music.lossless

import timber.log.Timber

class FlacLogger(private val tag: String) {
    fun d(msg: String) = Timber.tag(tag).d(msg)
    fun i(msg: String) = Timber.tag(tag).i(msg)
    fun w(msg: String) = Timber.tag(tag).w(msg)
    fun e(msg: String, t: Throwable? = null) {
        if (t != null) Timber.tag(tag).e(t, msg) else Timber.tag(tag).e(msg)
    }
}
