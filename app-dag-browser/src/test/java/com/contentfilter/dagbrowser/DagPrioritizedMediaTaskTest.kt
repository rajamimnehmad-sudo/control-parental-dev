package com.contentfilter.dagbrowser

import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class DagPrioritizedMediaTaskTest {
    @Test
    fun `visible work jumps ahead while equal priorities remain fifo`() {
        val executed = mutableListOf<String>()
        val queue = PriorityBlockingQueue<DagPrioritizedMediaTask>()

        queue += task("background", 0, executed)
        queue += task("nearby-first", 1, executed, DagMediaAnalysisPriority.Nearby)
        queue += task("visible", 2, executed, DagMediaAnalysisPriority.Visible)
        queue += task("nearby-second", 3, executed, DagMediaAnalysisPriority.Nearby)

        while (queue.isNotEmpty()) queue.take().run()

        assertEquals(
            listOf("visible", "nearby-first", "nearby-second", "background"),
            executed,
        )
    }

    @Test
    fun `unknown wire priority stays safely in background`() {
        assertEquals(DagMediaAnalysisPriority.Visible, DagMediaAnalysisPriority.fromWire("visible"))
        assertEquals(DagMediaAnalysisPriority.Nearby, DagMediaAnalysisPriority.fromWire("nearby"))
        assertEquals(DagMediaAnalysisPriority.Background, DagMediaAnalysisPriority.fromWire("unknown"))
    }

    private fun task(
        name: String,
        sequence: Long,
        output: MutableList<String>,
        priority: DagMediaAnalysisPriority = DagMediaAnalysisPriority.Background,
    ) = DagPrioritizedMediaTask(priority, sequence) { output += name }
}
