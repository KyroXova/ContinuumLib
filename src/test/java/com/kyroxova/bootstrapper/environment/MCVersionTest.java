package com.kyroxova.bootstrapper.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MCVersionTest {

    @Test
    public void testVersionParsingAndComparison() {
        MCVersion v1_7_9 = MCVersion.of("1.7.9");
        MCVersion v1_12_2 = MCVersion.of("1.12.2");
        MCVersion v1_18_2 = MCVersion.of("1.18.2");
        MCVersion v1_19_2 = MCVersion.of("1.19.2");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");
        MCVersion v26_3 = MCVersion.of("26.3");

        assertTrue(v1_7_9.isBefore(v1_12_2));
        assertTrue(v1_12_2.isBefore(v1_18_2));
        assertTrue(v1_18_2.isBefore(v1_19_2));
        assertTrue(v1_19_2.isBefore(v1_20_4));
        assertTrue(v1_20_4.isBefore(v26_3));

        assertTrue(v1_18_2.isAtLeast(v1_18_2));
        assertTrue(v26_3.isAfter(v1_20_4));
        assertTrue(v1_19_2.isBetween(v1_18_2, v1_20_4));
        assertFalse(v1_7_9.isBetween(v1_18_2, v1_20_4));
    }

    @Test
    public void testEqualityAndHashing() {
        MCVersion a = MCVersion.of("1.18.2");
        MCVersion b = MCVersion.of("1.18.2");
        MCVersion c = MCVersion.of("1.19.2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
