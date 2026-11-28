package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class ExtensionAssetsTest {
    private static final String[] BANNED = {
        "window.__nova", "window.__nv", "__semoCfg", ".__nvl", ".__nvc", ".__nvr", ".__nv ", ".__nv=", ".__nbp", ".__nbw"
    };

    private static Path assets() {
        Path p = Paths.get("src/main/assets");
        if (!Files.isDirectory(p)) p = Paths.get("app/src/main/assets");
        return p;
    }

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> s = Files.walk(assets())) {
            return s.filter(f -> f.toString().endsWith(".js")).collect(Collectors.toList());
        }
    }

    @Test
    public void assetsFound() throws IOException {
        assertTrue(Files.isDirectory(assets()));
        assertTrue(scripts().size() > 10);
    }

    @Test
    public void noGlobalsOrElementMarkersInAssets() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path f : scripts()) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            for (String b : BANNED) if (text.contains(b)) hits.add(f.getFileName() + " contains " + b);
        }
        assertEquals(hits.toString(), 0, hits.size());
    }

    @Test
    public void noConfigScriptsRemain() throws IOException {
        File dir = assets().resolve("extensions/semo").toFile();
        String[] names = dir.list();
        assertTrue(names != null);
        for (String n : names) assertFalse(n, n.startsWith("cfg-"));
        assertFalse(Arrays.asList(names).contains("main-smooth.js"));
        assertFalse(Arrays.asList(names).contains("main-extras.js"));
    }

    @Test
    public void everyScriptGroupHasItsBundle() {
        File dir = assets().resolve("extensions/semo").toFile();
        for (String n : new String[] {"frames", "frames-ns", "core", "dark-1", "dark-2"}) {
            assertTrue(n, new File(dir, "main-" + n + ".js").isFile());
        }
        for (int mask = 1; mask < 16; mask++) {
            assertTrue("page " + mask, new File(dir, "main-page-" + mask + ".js").isFile());
            assertTrue("misc " + mask, new File(dir, "main-misc-" + mask + ".js").isFile());
        }
        assertTrue(new File(dir, "hub.js").isFile());
        assertTrue(new File(dir, "rules-ads.json").isFile());
        assertTrue(new File(dir, "bypass.txt").isFile());
    }

    @Test
    public void backgroundHasNoBlockingWebRequest() throws IOException {
        Path dir = assets().resolve("extensions/semo");
        String bg = new String(Files.readAllBytes(dir.resolve("background.js")), StandardCharsets.UTF_8);
        String mf = new String(Files.readAllBytes(dir.resolve("manifest.json")), StandardCharsets.UTF_8);
        assertFalse(bg.contains("webRequest"));
        assertFalse(mf.contains("webRequest"));
        assertTrue(mf.contains("declarativeNetRequest"));
    }

    @Test
    public void mutationObserversLiveOnlyInTheSharedHub() throws IOException {
        Path dir = assets().resolve("extensions/semo");
        for (String n : new String[] {"page.js", "pw.js"}) {
            String t = new String(Files.readAllBytes(dir.resolve(n)), StandardCharsets.UTF_8);
            assertFalse(n, t.contains("new MutationObserver"));
        }
    }
}
