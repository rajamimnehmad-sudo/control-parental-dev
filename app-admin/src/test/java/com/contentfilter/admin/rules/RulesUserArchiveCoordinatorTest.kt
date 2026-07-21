package com.contentfilter.admin.rules

import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.engine.SyncResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesUserArchiveCoordinatorTest {
    @Test
    fun `remote archive success still cleans apps and syncs when device cleanup fails`() =
        runBlocking {
            var installedAppsCleaned = false
            var fullSyncExecuted = false
            val coordinator =
                RulesUserArchiveCoordinator.forTest(
                    archiveRemote = { RemoteResult.Success(Unit) },
                    deleteDevice = { error("Room delete failed") },
                    deleteInstalledApps = { installedAppsCleaned = true },
                    syncDevices = {
                        fullSyncExecuted = true
                        SyncResult(success = true, message = "ok")
                    },
                )

            val result = coordinator.archiveUser("device-a")

            assertEquals(
                ArchiveUserResult.RemoteSuccessWithLocalRepairPending(
                    setOf(ArchiveLocalRepairStep.Device),
                ),
                result,
            )
            assertTrue(installedAppsCleaned)
            assertTrue(fullSyncExecuted)
        }
}
