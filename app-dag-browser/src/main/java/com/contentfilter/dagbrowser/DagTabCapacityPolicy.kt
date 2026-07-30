package com.contentfilter.dagbrowser

internal object DagTabCapacityPolicy {
    const val MaxTabs = 50
    const val ThumbnailWidth = 200
    const val ThumbnailHeight = 300
    const val BytesPerPixel = 4
    const val MaxThumbnailMemoryBytes =
        MaxTabs * ThumbnailWidth * ThumbnailHeight * BytesPerPixel

    fun canCreate(currentCount: Int): Boolean = currentCount in 0 until MaxTabs
}
