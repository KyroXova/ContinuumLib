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
    void loadRejectsConfigurationRootFile(@TempDir Path root) throws Exception {
        Path config = root.resolve("continuumlib");
        Files.writeString(config, "not a directory");

        FilterConfigurationException failure = assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().load(config)
        );

        assertTrue(failure.getMessage().contains("not a directory"));
    }

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
    void explicitDomainCannotBeOverriddenByOtherFields(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "class",
                  "class": "example.Test",
                  "type": "block",
                  "id": "example:test"
                }
                """);

        FilterConfigurationException failure = assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );

        assertTrue(failure.getMessage().contains("incompatible with domain"));
    }

    @Test
    void rejectsConflictingAliases(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "class",
                  "class": "example.Test",
                  "className": "example.Other"
                }
                """);

        FilterConfigurationException failure = assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );

        assertTrue(failure.getMessage().contains("Use only one of 'class' or 'className'"));
    }

    @Test
    void rejectsConflictingConditionAliases(@TempDir Path root) throws Exception {
        Path file = root.resolve("rule.json");
        Files.writeString(file, """
                {
                  "domain": "source",
                  "path": "example/Test.java",
                  "when": {
                    "loader_version": ">=1",
                    "loaderVersion": ">=2"
                  }
                }
                """);

        assertThrows(
                FilterConfigurationException.class,
                () -> new FilterConfigurationReader().parseRuleFile(file)
        );
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
