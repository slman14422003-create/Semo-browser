package com.nova.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfMigrationTest {
    @Test fun migratesAllScalarTypes() {
        val old = mapOf("b" to true, "i" to 2, "l" to 3L, "f" to 1.5f, "s" to "a\nb", "cleanpending" to true)
        assertEquals(mapOf("b" to "true", "i" to "2", "l" to "3", "f" to "1.5", "s" to "a b"), ConfStore.legacyValues(old))
    }
}
