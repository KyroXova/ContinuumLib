package com.kyroxova.continuumlib.filter;

import com.kyroxova.continuumlib.filter.config.FilterConfigurationException;
import com.kyroxova.continuumlib.filter.config.FilterConfigurationReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FilterConfigurationReaderTest {
    @Test
    void rejectsUnknownRuleKeys(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "source",
                  "path": "example/Test.java",
                  "domian": "source"
                }
                """);

        FilterConfigurationException failure = assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );

        assertTrue(failure.getMessage().contains("Unknown key 'domian'"));
    }

    @Test
    void rejectsUnknownConditionKeys(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "source",
                  "path": "example/Test.java",
                  "when": {
                    "minecraft": ">=1.20",
                    "minecrafft": ">=1.21"
                  }
                }
                """);

        FilterConfigurationException failure = assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );

        assertTrue(failure.getMessage().contains("Unknown key 'minecrafft'"));
    }

    @Test
    void rejectsNonScalarStringFields(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "source",
                  "path": ["example/Test.java"]
                }
                """);

        assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );
    }
}
