package com.nova.browser;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class VaultBackupTest {
    @Test public void firstWriteKeepsOnlyCurrent() {
        List<String> r = VaultBackup.rotate(null, null, "a");
        assertEquals(Arrays.asList("a", null, null), r);
    }

    @Test public void shiftsOldestOut() {
        List<String> r = VaultBackup.rotate("b", "c", "a");
        assertEquals(Arrays.asList("a", "b", "c"), r);
    }

    @Test public void thirdWriteDropsFirst() {
        List<String> r = VaultBackup.rotate("c", "b", "d");
        assertEquals(Arrays.asList("d", "c", "b"), r);
    }

    @Test public void missingCurrentLeavesBackups() {
        List<String> r = VaultBackup.rotate("x", "y", null);
        assertEquals(Arrays.asList("x", "y", null), r);
    }
}
