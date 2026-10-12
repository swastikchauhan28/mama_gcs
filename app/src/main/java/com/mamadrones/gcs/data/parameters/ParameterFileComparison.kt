package com.mamadrones.gcs.data.parameters

import com.mamadrones.gcs.domain.model.ParameterEntry
import com.mamadrones.gcs.domain.model.ParameterSnapshot

enum class ParameterDifferenceKind(val label: String) {
    CHANGED("VALUE TEXT CHANGED"), CURRENT_ONLY("ONLY IN CURRENT FILE"),
    REFERENCE_ONLY("ONLY IN REFERENCE FILE"), UNCHANGED("SAME VALUE TEXT")
}

data class ParameterDifference(
    val name: String,
    val reference: ParameterEntry?,
    val current: ParameterEntry?,
    val kind: ParameterDifferenceKind,
)

data class ParameterComparison(val differences: List<ParameterDifference>) {
    val counts: Map<ParameterDifferenceKind, Int> = ParameterDifferenceKind.entries.associateWith { kind ->
        differences.count { it.kind == kind }
    }
}

/** Exact raw-value comparison only; never applies defaults or interprets firmware semantics. */
object ParameterFileComparison {
    fun compare(reference: ParameterSnapshot, current: ParameterSnapshot): ParameterComparison {
        val before = reference.entries.associateBy { it.name }
        val after = current.entries.associateBy { it.name }
        return ParameterComparison((before.keys + after.keys).sorted().map { name ->
            val old = before[name]
            val new = after[name]
            val kind = when {
                old == null -> ParameterDifferenceKind.CURRENT_ONLY
                new == null -> ParameterDifferenceKind.REFERENCE_ONLY
                old.rawValue == new.rawValue -> ParameterDifferenceKind.UNCHANGED
                else -> ParameterDifferenceKind.CHANGED
            }
            ParameterDifference(name, old, new, kind)
        })
    }
}
