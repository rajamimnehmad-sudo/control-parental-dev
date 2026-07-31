package com.contentfilter.dagbrowser

internal enum class DagMediaAnalysisPriority(
    internal val rank: Int,
) {
    Visible(0),
    Nearby(1),
    Background(2),
    ;

    companion object {
        fun fromWire(value: String): DagMediaAnalysisPriority =
            when (value) {
                "visible" -> Visible
                "nearby" -> Nearby
                else -> Background
            }
    }
}

internal class DagPrioritizedMediaTask(
    private val priority: DagMediaAnalysisPriority,
    private val sequence: Long,
    private val action: () -> Unit,
) : Runnable,
    Comparable<DagPrioritizedMediaTask> {
    override fun run() = action()

    override fun compareTo(other: DagPrioritizedMediaTask): Int {
        val priorityOrder = priority.rank.compareTo(other.priority.rank)
        return if (priorityOrder != 0) priorityOrder else sequence.compareTo(other.sequence)
    }
}
