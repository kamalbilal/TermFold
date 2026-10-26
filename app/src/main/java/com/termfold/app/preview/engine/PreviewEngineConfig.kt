package com.termfold.app.preview.engine

/** Preview and agent browser always use GeckoView. */
object PreviewEngineConfig {
    const val GECKOVIEW_VERSION = "141.0.20250806102122"

    fun useGeckoPreview(url: String? = null): Boolean = true
}
