package com.jjx.common.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasonSanitizerTest {

    @Test
    void sanitizesExplicitBlacklistOnly() {
        assertEquals("", ReasonSanitizer.sanitize("  选多行可整批合格  "));
        assertEquals("整批合格但有轻微色差", ReasonSanitizer.sanitize("整批合格但有轻微色差"));
    }

    @Test
    void validatesSupplementBySharedRules() {
        assertFalse(ReasonSanitizer.isValidSupplement("!!!", "外观：不合格"));
        assertFalse(ReasonSanitizer.isValidSupplement("外观：不合格", "外观：不合格"));
        assertTrue(ReasonSanitizer.isValidSupplement("外箱压痕", "外观：不合格"));
        assertTrue(ReasonSanitizer.isValidSupplement("a".repeat(200), ""));
        assertFalse(ReasonSanitizer.isValidSupplement("a".repeat(201), ""));
    }

    @Test
    void frontendBlacklistMatchesBackendBlacklist() throws Exception {
        Path frontend = Path.of("..", "jjx-web", "src", "utils", "reasonSanitizer.ts");
        String source = Files.readString(frontend, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("'((?:\\\\.|[^'])*)'").matcher(source);
        Set<String> frontendBlacklist = new LinkedHashSet<>();
        while (matcher.find()) {
            String value = matcher.group(1).replace("\\'", "'");
            if (value.contains("整批合格")) frontendBlacklist.add(value);
        }
        assertEquals(ReasonSanitizer.BLACKLIST, frontendBlacklist);
    }
}
