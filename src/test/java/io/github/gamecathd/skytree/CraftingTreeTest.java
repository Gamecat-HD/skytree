package io.github.gamecathd.skytree;

import io.github.gamecathd.skytree.CraftingTree.Recipe;
import io.github.gamecathd.skytree.CraftingTree.Total;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTreeTest {

    private static final Map<String, List<Recipe>> RECIPES = Map.of(
            "ENCHANTED_DIAMOND", List.of(
                    new Recipe("crafting", 1, Map.of("DIAMOND", 160L)),
                    new Recipe("crafting", 9, Map.of("DIAMOND_BLOCK", 160L))),
            "ENCHANTED_DIAMOND_BLOCK", List.of(new Recipe("crafting", 1, Map.of("ENCHANTED_DIAMOND", 160L))),
            "STICKS", List.of(new Recipe("crafting", 4, Map.of("PLANK", 2L))),
            "LOOP_A", List.of(new Recipe("crafting", 1, Map.of("LOOP_B", 1L))),
            "LOOP_B", List.of(new Recipe("crafting", 1, Map.of("LOOP_A", 1L))));

    private static List<Recipe> lookup(String id) {
        return RECIPES.getOrDefault(id, List.of());
    }

    @Test
    void multipliesThroughLevels() {
        var root = CraftingTree.build("ENCHANTED_DIAMOND_BLOCK", 2, CraftingTreeTest::lookup);
        assertEquals(320, root.children.getFirst().amount);
        assertEquals(Map.of("DIAMOND", new Total(51_200, 0)), CraftingTree.rawMaterials(root));
    }

    @Test
    void roundsCraftsUpByOutputCount() {
        var root = CraftingTree.build("STICKS", 5, CraftingTreeTest::lookup);
        assertEquals(2, root.crafts());
        assertEquals(4, root.children.getFirst().amount);
    }

    @Test
    void stopsOnCycles() {
        var root = CraftingTree.build("LOOP_A", 1, CraftingTreeTest::lookup);
        var b = root.children.getFirst();
        assertTrue(b.children.getFirst().children.isEmpty());
        assertEquals(Map.of("LOOP_A", new Total(1, 0)), CraftingTree.rawMaterials(root));
    }

    @Test
    void usesChosenRecipe() {
        var root = CraftingTree.build("ENCHANTED_DIAMOND", 9, CraftingTreeTest::lookup,
                Map.of("ENCHANTED_DIAMOND", 1), new HashMap<>());
        assertEquals(1, root.recipeIndex);
        assertEquals(2, root.recipeCount);
        assertEquals(Map.of("DIAMOND_BLOCK", new Total(160, 0)), CraftingTree.rawMaterials(root));
    }

    @Test
    void subtractsOwnedIntermediatesAndSharesThePool() {
        // Own 100 enchanted diamonds and 10k diamonds; need 160 enchanted diamonds for one block.
        Map<String, Long> owned = new HashMap<>(Map.of("ENCHANTED_DIAMOND", 100L, "DIAMOND", 10_000L));
        var root = CraftingTree.build("ENCHANTED_DIAMOND_BLOCK", 1, CraftingTreeTest::lookup, Map.of(), owned);
        var ench = root.children.getFirst();
        assertEquals(100, ench.owned);
        assertEquals(60, ench.crafts());
        assertEquals(Map.of("DIAMOND", new Total(9_600, 9_600)), CraftingTree.rawMaterials(root));
        assertEquals(400L, owned.get("DIAMOND"));
    }

    @Test
    void fullyOwnedIntermediateIsNotExpandedOrListedAsRaw() {
        Map<String, Long> owned = new HashMap<>(Map.of("ENCHANTED_DIAMOND", 500L));
        var root = CraftingTree.build("ENCHANTED_DIAMOND_BLOCK", 1, CraftingTreeTest::lookup, Map.of(), owned);
        assertTrue(root.children.getFirst().children.isEmpty());
        assertEquals(Map.of(), CraftingTree.rawMaterials(root));
    }

    @Test
    void rootIsNeverSubtracted() {
        Map<String, Long> owned = new HashMap<>(Map.of("STICKS", 50L));
        var root = CraftingTree.build("STICKS", 4, CraftingTreeTest::lookup, Map.of(), owned);
        assertEquals(0, root.owned);
        assertEquals(1, root.crafts());
    }
}
