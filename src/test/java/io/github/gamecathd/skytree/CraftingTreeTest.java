package io.github.gamecathd.skytree;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTreeTest {

    private static final Map<String, CraftingTree.Recipe> RECIPES = Map.of(
            "ENCHANTED_DIAMOND", new CraftingTree.Recipe("crafting", 1, Map.of("DIAMOND", 160L)),
            "ENCHANTED_DIAMOND_BLOCK", new CraftingTree.Recipe("crafting", 1, Map.of("ENCHANTED_DIAMOND", 160L)),
            "STICKS", new CraftingTree.Recipe("crafting", 4, Map.of("PLANK", 2L)),
            "LOOP_A", new CraftingTree.Recipe("crafting", 1, Map.of("LOOP_B", 1L)),
            "LOOP_B", new CraftingTree.Recipe("crafting", 1, Map.of("LOOP_A", 1L)));

    @Test
    void multipliesThroughLevels() {
        var root = CraftingTree.build("ENCHANTED_DIAMOND_BLOCK", 2, RECIPES::get);
        assertEquals(320, root.children.getFirst().amount);
        assertEquals(Map.of("DIAMOND", 51_200L), CraftingTree.rawMaterials(root));
    }

    @Test
    void roundsCraftsUpByOutputCount() {
        var root = CraftingTree.build("STICKS", 5, RECIPES::get);
        assertEquals(2, root.crafts());
        assertEquals(4, root.children.getFirst().amount);
    }

    @Test
    void stopsOnCycles() {
        var root = CraftingTree.build("LOOP_A", 1, RECIPES::get);
        var b = root.children.getFirst();
        assertTrue(b.children.getFirst().children.isEmpty());
        assertEquals(Map.of("LOOP_A", 1L), CraftingTree.rawMaterials(root));
    }
}
