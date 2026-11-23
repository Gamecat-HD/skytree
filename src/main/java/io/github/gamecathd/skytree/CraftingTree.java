package io.github.gamecathd.skytree;

import java.util.ArrayList;
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

    public static final class Node {
        public final String id;
        public final long amount;
        /** Null for leaves (raw materials, unknown items, cycles). */
        public final Recipe recipe;
        public final List<Node> children = new ArrayList<>();
        public boolean collapsed;

        Node(String id, long amount, Recipe recipe) {
            this.id = id;
            this.amount = amount;
            this.recipe = recipe;
        }

        /** How many times the recipe has to be run to get {@link #amount}. */
        public long crafts() {
            return recipe == null ? 0 : ceilDiv(amount, recipe.count());
        }
    }

    private static final int MAX_DEPTH = 16;

    private CraftingTree() {}

    /** @param recipes returns the recipe used to make an id, or null if it is a raw material */
    public static Node build(String id, long amount, Function<String, Recipe> recipes) {
        return build(id, amount, recipes, new HashSet<>(), 0);
    }

    private static Node build(String id, long amount, Function<String, Recipe> recipes, Set<String> path, int depth) {
        Recipe recipe = depth < MAX_DEPTH && !path.contains(id) ? recipes.apply(id) : null;
        Node node = new Node(id, amount, recipe);
        if (recipe == null) return node;

        path.add(id);
        long crafts = node.crafts();
        for (Map.Entry<String, Long> in : recipe.inputs().entrySet()) {
            node.children.add(build(in.getKey(), in.getValue() * crafts, recipes, path, depth + 1));
        }
        path.remove(id);
        return node;
    }

    /** Sums all leaves: the raw materials needed for the whole tree, biggest first. */
    public static Map<String, Long> rawMaterials(Node root) {
        Map<String, Long> totals = new LinkedHashMap<>();
        collectLeaves(root, totals);
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        Map<String, Long> out = new LinkedHashMap<>();
        sorted.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static void collectLeaves(Node node, Map<String, Long> totals) {
        if (node.children.isEmpty()) {
            totals.merge(node.id, node.amount, Long::sum);
            return;
        }
        node.children.forEach(c -> collectLeaves(c, totals));
    }

    static long ceilDiv(long a, int b) {
        return b <= 0 ? a : (a + b - 1) / b;
    }
}
