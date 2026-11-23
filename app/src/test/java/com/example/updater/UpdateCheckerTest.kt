package com.example.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `isNewer returns true when latest version is greater than current`() {
        assertTrue(UpdateChecker.isNewer(latest = "1.2.2", current = "1.2.1"))
    }

    @Test
    fun `isNewer returns false when latest version equals current`() {
        assertFalse(UpdateChecker.isNewer(latest = "1.2.2", current = "1.2.2"))
    }

    @Test
    fun `isNewer returns false when latest version is older than current`() {
        assertFalse(UpdateChecker.isNewer(latest = "1.2.1", current = "1.2.2"))
    }

    @Test
    fun `isNewer compares numeric segments without lexicographic ordering`() {
        assertTrue(UpdateChecker.isNewer(latest = "1.10.0", current = "1.9.9"))
        assertTrue(UpdateChecker.isNewer(latest = "2.0.0", current = "1.9.9"))
        assertFalse(UpdateChecker.isNewer(latest = "1.9.9", current = "1.10.0"))
    }

    @Test
    fun `isNewer strips leading v prefix before comparing`() {
        assertTrue(UpdateChecker.isNewer(latest = "v1.2.2", current = "1.2.1"))
        assertFalse(UpdateChecker.isNewer(latest = "v1.2.1", current = "1.2.2"))
        assertFalse(UpdateChecker.isNewer(latest = "v1.2.2", current = "v1.2.2"))
    }
}
