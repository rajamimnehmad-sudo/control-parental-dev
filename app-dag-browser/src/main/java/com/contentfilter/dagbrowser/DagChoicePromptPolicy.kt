package com.contentfilter.dagbrowser

import org.mozilla.geckoview.GeckoSession

internal data class DagChoicePromptRow(
    val choice: GeckoSession.PromptDelegate.ChoicePrompt.Choice,
    val label: String,
    val enabled: Boolean,
    val selected: Boolean,
)

internal object DagChoicePromptPolicy {
    fun flatten(
        choices: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
        unnamedLabel: String,
        groupLabels: List<String> = emptyList(),
    ): List<DagChoicePromptRow> =
        buildList {
            choices.forEach { choice ->
                if (choice.separator) return@forEach
                val label = choice.label.trim().take(MaxLabelLength)
                val children = choice.items
                if (children != null) {
                    val nextGroups = if (label.isBlank()) groupLabels else groupLabels + label
                    addAll(flatten(children, unnamedLabel, nextGroups))
                } else {
                    val visibleLabel =
                        (groupLabels + label.takeIf(String::isNotBlank).orEmpty())
                            .filter(String::isNotBlank)
                            .joinToString(GroupSeparator)
                            .ifBlank { unnamedLabel }
                            .take(MaxLabelLength)
                    add(DagChoicePromptRow(choice, visibleLabel, !choice.disabled, choice.selected))
                }
            }
        }

    private const val MaxLabelLength = 200
    private const val GroupSeparator = " — "
}
