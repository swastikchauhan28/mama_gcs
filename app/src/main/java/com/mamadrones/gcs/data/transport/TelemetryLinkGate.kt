package com.mamadrones.gcs.data.transport

import javax.inject.Inject
import javax.inject.Singleton

/** One telemetry producer at a time. Release only after its parser/session has been closed. */
@Singleton
class TelemetryLinkGate @Inject constructor() {
    class Lease internal constructor(val label: String)
    private var active: Lease? = null

    @Synchronized
    fun acquire(label: String): Lease {
        check(active == null) { "Close the active ${active?.label} link before opening $label." }
        return Lease(label).also { active = it }
    }

    @Synchronized
    fun release(lease: Lease) {
        if (active === lease) active = null
    }
}
