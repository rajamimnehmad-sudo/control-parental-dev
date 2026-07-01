package com.contentfilter.core.domain.model

/**
 * Update state for direct APK and future store-based channels.
 */
enum class UpdateState {
    Current,
    OptionalUpdateAvailable,
    RequiredUpdateAvailable,
    Unknown,
}
