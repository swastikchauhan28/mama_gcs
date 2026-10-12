package com.mamadrones.gcs.data.parameters

import org.junit.Assert.*
import org.junit.Test

class ParameterFileComparisonTest {
    private fun read(text: String) = ParameterFileReader.read(text.byteInputStream())

    @Test fun classifiesEveryKindWithReferenceToCurrentDirection() {
        val reference = read("D 4\nA 1\nB 2")
        val current = read("C 3\nB 2\nA 7")
        val result = ParameterFileComparison.compare(reference, current)
        assertEquals(listOf("A", "B", "C", "D"), result.differences.map { it.name })
        assertEquals(listOf(ParameterDifferenceKind.CHANGED, ParameterDifferenceKind.UNCHANGED,
            ParameterDifferenceKind.CURRENT_ONLY, ParameterDifferenceKind.REFERENCE_ONLY), result.differences.map { it.kind })
        assertTrue(result.counts.values.all { it == 1 })
        assertEquals("1", result.differences[0].reference!!.rawValue)
        assertEquals("7", result.differences[0].current!!.rawValue)
        assertEquals(2, result.differences[0].reference!!.line)
        assertEquals(3, result.differences[0].current!!.line)
        assertNull(result.differences[2].reference)
        assertNull(result.differences[3].current)
    }

    @Test fun exactTextComparisonDoesNotGuessNumericEquivalence() {
        val reference = read("A 1\nB 1000\nC 0xFF\nD -0")
        val current = read("A 1.0\nB 1e3\nC 255\nD 0")
        assertEquals(4, ParameterFileComparison.compare(reference, current).counts[ParameterDifferenceKind.CHANGED])
    }

    @Test fun commentsOrderingAndSpacingDoNotCreateValueDifferences() {
        val reference = read("# old\nB,2\nA,1")
        val current = read("A   1 # comment\nB\t2")
        assertNotEquals(reference.sha256, current.sha256)
        val result = ParameterFileComparison.compare(reference, current)
        assertEquals(2, result.counts[ParameterDifferenceKind.UNCHANGED])
        assertEquals(0, result.counts[ParameterDifferenceKind.CHANGED])
    }

    @Test fun identicalFileAndSwappedRolesAreDeterministic() {
        val a = read("A 1\nB 2")
        val b = read("A 3\nC 4")
        assertTrue(ParameterFileComparison.compare(a, a).differences.all { it.kind == ParameterDifferenceKind.UNCHANGED })
        val forward = ParameterFileComparison.compare(a, b).differences.associateBy { it.name }
        val reverse = ParameterFileComparison.compare(b, a).differences.associateBy { it.name }
        assertEquals(forward.getValue("A").current, reverse.getValue("A").reference)
        assertEquals(ParameterDifferenceKind.REFERENCE_ONLY, forward.getValue("B").kind)
        assertEquals(ParameterDifferenceKind.CURRENT_ONLY, reverse.getValue("B").kind)
        assertEquals("1", a.entries.first().rawValue)
    }

    @Test fun maximumDisjointInputsHaveBoundedUnionWithoutDroppingEntries() {
        val a = read((0 until 10_000).joinToString("\n") { "A$it 1" })
        val b = read((0 until 10_000).joinToString("\n") { "B$it 2" })
        val comparison = ParameterFileComparison.compare(a, b)
        assertEquals(20_000, comparison.differences.size)
        assertEquals(10_000, comparison.counts[ParameterDifferenceKind.CURRENT_ONLY])
        assertEquals(10_000, comparison.counts[ParameterDifferenceKind.REFERENCE_ONLY])
    }
}
