package io.github.gamecathd.skytree;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCountsTest {
    @Test
    void sumsAcrossSourcesAndKeepsBreakdown() {
        ItemCounts c = new ItemCounts();
        c.add("Inventory", "DIAMOND", 64);
        c.add("Sacks", "DIAMOND", 1000);
        c.add("Inventory", "DIAMOND", 10);
        c.add("Sacks", "COAL", 0);
        assertEquals(1074, c.get("DIAMOND"));
        assertEquals(Map.of("Inventory", 74L, "Sacks", 1000L), c.where("DIAMOND"));
        assertEquals(0, c.get("COAL"));
    }
}
