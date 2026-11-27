package com.nova.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SignatureCheckTest {
    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static List<byte[]> l(String... s) {
        byte[][] out = new byte[s.length][];
        for (int i = 0; i < s.length; i++) out[i] = b(s[i]);
        return Arrays.asList(out);
    }

    private static final List<byte[]> NONE = Collections.<byte[]>emptyList();

    @Test
    public void sameSingleSigner() {
        assertTrue(SignatureCheck.accepts(l("A"), false, l("A"), false, NONE));
    }

    @Test
    public void differentSingleSigner() {
        assertFalse(SignatureCheck.accepts(l("A"), false, l("B"), false, NONE));
        assertFalse(SignatureCheck.accepts(l("A"), false, l("B"), false, l("B")));
    }

    @Test
    public void rotationAcceptedWhenInstalledIsInHistory() {
        assertTrue(SignatureCheck.accepts(l("A"), false, l("B"), false, l("A", "B")));
    }

    @Test
    public void rotationRejectedWhenInstalledNotInHistory() {
        assertFalse(SignatureCheck.accepts(l("C"), false, l("B"), false, l("A", "B")));
    }

    @Test
    public void emptyInputsRejected() {
        assertFalse(SignatureCheck.accepts(NONE, false, l("A"), false, NONE));
        assertFalse(SignatureCheck.accepts(l("A"), false, NONE, false, NONE));
        assertFalse(SignatureCheck.accepts(NONE, false, NONE, false, NONE));
    }

    @Test
    public void multiSignerRequiresEqualSets() {
        assertTrue(SignatureCheck.accepts(l("A", "B"), true, l("B", "A"), true, NONE));
        assertFalse(SignatureCheck.accepts(l("A", "B"), true, l("A"), true, NONE));
        assertFalse(SignatureCheck.accepts(l("A", "B"), true, l("A", "B", "C"), true, NONE));
        assertFalse(SignatureCheck.accepts(l("A", "B"), true, l("A", "C"), true, NONE));
    }

    @Test
    public void multiSignerIgnoresHistory() {
        assertFalse(SignatureCheck.accepts(l("A", "B"), true, l("A", "C"), true, l("A", "B")));
    }

    @Test
    public void singleVersusMultiRejected() {
        assertFalse(SignatureCheck.accepts(l("A"), false, l("A", "B"), true, NONE));
        assertFalse(SignatureCheck.accepts(l("A", "B"), true, l("A"), false, NONE));
        assertFalse(SignatureCheck.accepts(l("A"), false, l("A", "B"), false, NONE));
    }
}
