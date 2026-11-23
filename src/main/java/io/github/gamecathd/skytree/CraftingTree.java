package io.github.gamecathd.skytree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds a recursive crafting tree from a recipe lookup. Pure logic, no Minecraft classes,
 * so it stays unit-testable.
 */
public final class CraftingTree {

    /** One way to make an item: {@code inputs} (id → amount) per craft, yielding {@code count}. */
    public record Recipe(String kind, int count, Map<String, Long> inputs) {}

    /** Summed need of one raw material and how much of it you already own. */
    public record Total(long need, long owned) {}

    public static final class Node {
        public final String id;
        /** How many are needed here. */
        public final long amount;
        /** How many of {@link #amount} are covered by items you already own. */
        public long owned;
        /** Null for leaves (raw materials, unknown items, cycles, fully owned). */
        public Recipe recipe;
        /** Which of the item's recipes is used, and how many there are. */
        public int recipeIndex, recipeCount;
        public final List<Node> children = new ArrayList<>();

        Node(String id, long amount) {
            this.id = id;
            this.amount = amount;
        }

        /** How many times the recipe has to be run for what isn't owned yet. */
        public long crafts() {
            return recipe == null ? 0 : ceilDiv(amount - owned, recipe.count());
        }
    }

    private static final int MAX_DEPTH = 16;

    private CraftingTree() {}

    /**
     * @param recipes all recipes that make an id (empty for raw materials)
     * @param choice  id → index of the recipe to use (missing = first)
     * @param owned   items you own; consumed as the tree uses them, so one stack is never
     *                counted twice. The root itself is never taken from it: you want to
     *                craft {@code amount} more.
     */
    public static Node build(String id, long amount, Function<String, List<Recipe>> recipes,
                             Map<String, Integer> choice, Map<String, Long> owned) {
        return build(id, amount, recipes, choice, owned, new HashSet<>(), 0);
    }

    /** Without inventory and recipe choices. */
    public static Node build(String id, long amount, Function<String, List<Recipe>> recipes) {
        return build(id, amount, recipes, Map.of(), new HashMap<>());
    }

    private static Node build(String id, long amount, Function<String, List<Recipe>> recipes,
                              Map<String, Integer> choice, Map<String, Long> owned, Set<String> path, int depth) {
        Node node = new Node(id, amount);
        if (depth > 0) {
            node.owned = Math.min(amount, owned.getOrDefault(id, 0L));
            if (node.owned > 0) owned.merge(id, -node.owned, Long::sum);
        }
        List<Recipe> options = recipes.apply(id);
        node.recipeCount = options == null ? 0 : options.size();
        if (node.recipeCount == 0 || node.owned >= amount || depth >= MAX_DEPTH || path.contains(id)) return node;

        node.recipeIndex = Math.floorMod(choice.getOrDefault(id, 0), options.size());
        node.recipe = options.get(node.recipeIndex);

        path.add(id);
        long crafts = node.crafts();
        for (Map.Entry<String, Long> in : node.recipe.inputs().entrySet()) {
            node.children.add(build(in.getKey(), in.getValue() * crafts, recipes, choice, owned, path, depth + 1));
        }
        path.remove(id);
        return node;
    }

    /** Sums all leaves: the raw materials still needed for the whole tree, biggest first. */
    public static Map<String, Total> rawMaterials(Node root) {
        Map<String, Total> totals = new HashMap<>();
        collectLeaves(root, totals);
        List<Map.Entry<String, Total>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().need(), a.getValue().need()));
        Map<String, Total> out = new LinkedHashMap<>();
        sorted.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static void collectLeaves(Node node, Map<String, Total> totals) {
        // A fully owned intermediate is not a raw material; there is nothing left to gather.
        if (node.children.isEmpty()) {
            if (node.recipeCount == 0 || node.owned < node.amount) {
                totals.merge(node.id, new Total(node.amount, node.owned),
                        (a, b) -> new Total(a.need() + b.need(), a.owned() + b.owned()));
            }
            return;
        }
        node.children.forEach(c -> collectLeaves(c, totals));
    }

    static long ceilDiv(long a, int b) {
        return b <= 0 ? a : (a + b - 1) / b;
    }
}
