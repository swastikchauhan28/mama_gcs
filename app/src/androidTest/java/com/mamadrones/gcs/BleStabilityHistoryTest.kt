package com.mamadrones.gcs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.BleStabilityHistoryRepository
import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Isolated real DataStore files; never modifies the operator's history. */
class BleStabilityHistoryTest {
    private fun runRecord(id: Long, outcome: String = "PASSED", reason: String? = null) =
        BleStabilityRun(id, id + 600_000, outcome, 600_000, reason)

    private fun withStore(block: suspend (File) -> Unit) = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "ble-test-${UUID.randomUUID()}.preferences_pb")
        try { block(file) } finally { file.delete() }
    }

    private suspend fun open(file: File, block: suspend (BleStabilityHistoryRepository,
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try { block(BleStabilityHistoryRepository(store), store) } finally { job.cancelAndJoin() }
    }

    @Test fun completedRunSurvivesReopeningWithoutFalseInterruption() = withStore { file ->
        val record = runRecord(1000, "STOPPED", "Manual stop | line\nUnicode ✓")
        open(file) { repo, _ -> repo.beginRun(1000); repo.finishRun(record) }
        open(file) { repo, _ ->
            repo.recoverInterruptedRun(900_000)
            assertEquals(listOf(record), repo.runs.first())
        }
    }

    @Test fun abandonedRunRecoversExactlyOnceAndNeverBecomesPassed() = withStore { file ->
        open(file) { repo, _ -> repo.beginRun(1000) }
        open(file) { repo, _ ->
            repo.recoverInterruptedRun(700_000)
            repo.recoverInterruptedRun(800_000)
            val record = repo.runs.first().single()
            assertEquals("INTERRUPTED", record.outcome)
            assertEquals(699_000L, record.durationMillis)
        }
    }

    @Test fun backwardsWallClockRecoveryClampsDuration() = withStore { file ->
        open(file) { repo, _ ->
            repo.beginRun(2000)
            repo.recoverInterruptedRun(1000)
            assertEquals(0L, repo.runs.first().single().durationMillis)
        }
    }

    @Test fun retainsLatestTwentyAndBoundsDisconnectReason() = withStore { file ->
        open(file) { repo, _ ->
            (1L..25L).forEach { repo.finishRun(runRecord(it, "FAILED", "x".repeat(400))) }
            val records = repo.runs.first()
            assertEquals((6L..25L).toList(), records.map { it.startedAtEpochMillis })
            assertTrue(records.all { it.disconnectReason!!.length == 300 })
        }
    }

    @Test fun migratesLegacyHistoryAndSkipsMalformedRecords() = withStore { file ->
        open(file) { repo, store ->
            store.edit { it[stringPreferencesKey("runs_v1")] = "broken\n601000|PASSED|600000|" }
            assertEquals(runRecord(1000), repo.runs.first().single())
            repo.beginRun(900_000)
            repo.recoverInterruptedRun(905_000)
            val preferences = store.data.first()
            assertFalse(preferences.contains(stringPreferencesKey("runs_v1")))
            assertEquals(listOf("PASSED", "INTERRUPTED"), repo.runs.first().map { it.outcome })
        }
    }

    @Test fun finishingOlderRunDoesNotEraseNewerActiveMarker() = withStore { file ->
        open(file) { repo, _ ->
            repo.beginRun(2000)
            repo.finishRun(runRecord(1000))
            repo.recoverInterruptedRun(3000)
            assertEquals(listOf(1000L, 2000L), repo.runs.first().map { it.startedAtEpochMillis })
        }
    }
}
