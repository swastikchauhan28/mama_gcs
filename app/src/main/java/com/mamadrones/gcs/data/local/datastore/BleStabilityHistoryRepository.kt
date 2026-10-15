package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class BleStabilityRun(
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val outcome: String,
    val durationMillis: Long,
    val disconnectReason: String?,
)

private val Context.bleStabilityStore by preferencesDataStore(name = "ble_stability_history")

@Singleton
class BleStabilityHistoryRepository internal constructor(
    private val store: DataStore<Preferences>,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.bleStabilityStore)
    private val runsKey = stringPreferencesKey("runs_v2")
    private val legacyRunsKey = stringPreferencesKey("runs_v1")
    private val activeRunStartedAtKey = longPreferencesKey("active_run_started_at_v1")

    val runs: Flow<List<BleStabilityRun>> = store.data
        .map(::readRuns)
        .catch { emit(emptyList()) }

    suspend fun beginRun(startedAtEpochMillis: Long) {
        store.edit { preferences ->
            preferences[activeRunStartedAtKey] = startedAtEpochMillis
        }
    }

    suspend fun finishRun(run: BleStabilityRun) {
        store.edit { preferences ->
            writeRuns(preferences, (readRuns(preferences) + run).takeLast(MAX_SAVED_RUNS))
            if (preferences[activeRunStartedAtKey] == run.startedAtEpochMillis) {
                preferences.remove(activeRunStartedAtKey)
            }
        }
    }

    suspend fun recoverInterruptedRun(nowEpochMillis: Long = System.currentTimeMillis()) {
        store.edit { preferences ->
            val startedAt = preferences[activeRunStartedAtKey] ?: return@edit
            val interrupted = BleStabilityRun(
                startedAtEpochMillis = startedAt,
                finishedAtEpochMillis = nowEpochMillis,
                outcome = "INTERRUPTED",
                durationMillis = (nowEpochMillis - startedAt).coerceAtLeast(0L),
                disconnectReason = "App process ended while the test was running.",
            )
            writeRuns(preferences, (readRuns(preferences) + interrupted).takeLast(MAX_SAVED_RUNS))
            preferences.remove(activeRunStartedAtKey)
        }
    }

    private fun encode(runs: List<BleStabilityRun>): String = runs.joinToString("\n") { run ->
        val reason = run.disconnectReason?.take(MAX_REASON_LENGTH)?.toByteArray(StandardCharsets.UTF_8)
            ?.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }.orEmpty()
        listOf(run.startedAtEpochMillis, run.finishedAtEpochMillis, run.outcome, run.durationMillis, reason).joinToString("|")
    }

    private fun readRuns(preferences: Preferences): List<BleStabilityRun> =
        preferences[runsKey]?.let(::decode)
            ?: preferences[legacyRunsKey]?.let(::decodeLegacy).orEmpty()

    private fun writeRuns(preferences: androidx.datastore.preferences.core.MutablePreferences, runs: List<BleStabilityRun>) {
        preferences[runsKey] = encode(runs)
        preferences.remove(legacyRunsKey)
    }

    private fun decode(value: String): List<BleStabilityRun> = value.lineSequence().mapNotNull { line ->
        val fields = line.split('|', limit = 5)
        if (fields.size != 5) return@mapNotNull null
        val startedAt = fields[0].toLongOrNull() ?: return@mapNotNull null
        val finishedAt = fields[1].toLongOrNull() ?: return@mapNotNull null
        val duration = fields[3].toLongOrNull() ?: return@mapNotNull null
        val reason = runCatching {
            String(Base64.decode(fields[4], Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        BleStabilityRun(startedAt, finishedAt, fields[2].take(24), duration.coerceAtLeast(0L), reason)
    }.toList().takeLast(MAX_SAVED_RUNS)

    private fun decodeLegacy(value: String): List<BleStabilityRun> = value.lineSequence().mapNotNull { line ->
        val fields = line.split('|', limit = 4)
        if (fields.size != 4) return@mapNotNull null
        val finishedAt = fields[0].toLongOrNull() ?: return@mapNotNull null
        val duration = fields[2].toLongOrNull() ?: return@mapNotNull null
        val reason = runCatching {
            String(Base64.decode(fields[3], Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        BleStabilityRun(
            startedAtEpochMillis = (finishedAt - duration).coerceAtLeast(0L),
            finishedAtEpochMillis = finishedAt,
            outcome = fields[1].take(24),
            durationMillis = duration.coerceAtLeast(0L),
            disconnectReason = reason,
        )
    }.toList().takeLast(MAX_SAVED_RUNS)

    private companion object {
        const val MAX_SAVED_RUNS = 20
        const val MAX_REASON_LENGTH = 300
    }
}
