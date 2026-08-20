package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromeVisualSampleDecision {
    Allow,
    Block,
    Unavailable,
}

internal enum class ChromeVisualPresentation {
    Visible,
    Covered,
}

internal data class ChromeVisualVideoRegionKey(
    val windowId: Int,
    val pageIdentity: Long,
    val regionId: String,
)

internal class ChromeVisualVideoPolicy {
    private var pageIdentity: Long? = null
    private val entries = linkedMapOf<ChromeVisualVideoRegionKey, Entry>()

    @Synchronized
    fun beginPage(identity: Long): Boolean {
        if (pageIdentity == identity) return false
        pageIdentity = identity
        entries.clear()
        return true
    }

    @Synchronized
    fun beforeSample(
        key: ChromeVisualVideoRegionKey,
        region: ChromeVisualRegion,
        observedChange: Boolean,
    ): ChromeVisualPresentation {
        if (pageIdentity != key.pageIdentity) return ChromeVisualPresentation.Covered
        val current = entries[key]
        val entry =
            when {
                current == null && entries.size < MaximumRegions -> Entry(region, dynamic = observedChange)
                current == null -> return ChromeVisualPresentation.Covered
                current.region != region -> Entry(region, dynamic = current.dynamic || observedChange)
                else -> current.apply { if (observedChange) dynamic = true }
            }
        entries[key] = entry
        return entry.presentation
    }

    @Synchronized
    fun record(
        key: ChromeVisualVideoRegionKey,
        decision: ChromeVisualSampleDecision,
    ): ChromeVisualPresentation {
        val entry = entries[key] ?: return ChromeVisualPresentation.Covered
        when (decision) {
            ChromeVisualSampleDecision.Allow -> {
                entry.safeSamples = (entry.safeSamples + 1).coerceAtMost(RequiredSafeSamples)
                entry.presentation =
                    if (entry.safeSamples >= RequiredSafeSamples) {
                        ChromeVisualPresentation.Visible
                    } else {
                        ChromeVisualPresentation.Covered
                    }
            }
            ChromeVisualSampleDecision.Block,
            ChromeVisualSampleDecision.Unavailable,
            -> {
                entry.safeSamples = 0
                entry.presentation = ChromeVisualPresentation.Covered
            }
        }
        return entry.presentation
    }

    @Synchronized
    fun regionsNeedingConfirmation(
        windowId: Int,
        identity: Long,
        available: List<ChromeVisualRegion>,
    ): List<ChromeVisualRegion> {
        if (pageIdentity != identity) return emptyList()
        val byId = available.associateBy(ChromeVisualRegion::id)
        return entries
            .filter { (key, entry) ->
                key.windowId == windowId &&
                    key.pageIdentity == identity &&
                    entry.safeSamples == 1 &&
                    entry.presentation == ChromeVisualPresentation.Covered
            }
            .mapNotNull { (key, _) -> byId[key.regionId] }
    }

    @Synchronized
    fun failActiveRegions(): List<ChromeVisualRegion> =
        entries.values.map { entry ->
            entry.safeSamples = 0
            entry.presentation = ChromeVisualPresentation.Covered
            entry.region
        }

    @Synchronized
    fun hasDynamicRegions(): Boolean = entries.values.any(Entry::dynamic)

    @Synchronized
    fun clear() {
        pageIdentity = null
        entries.clear()
    }

    private data class Entry(
        val region: ChromeVisualRegion,
        var safeSamples: Int = 0,
        var presentation: ChromeVisualPresentation = ChromeVisualPresentation.Covered,
        var dynamic: Boolean = false,
    )

    private companion object {
        const val RequiredSafeSamples = 2
        const val MaximumRegions = 8
    }
}
