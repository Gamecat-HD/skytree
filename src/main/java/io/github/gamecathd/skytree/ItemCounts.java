package io.github.gamecathd.skytree;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** How many of each SkyBlock id the player owns, split by where it is. */
public final class ItemCounts {

    private static final boolean SKYBLOCK_API = FabricLoader.getInstance().isModLoaded("skyblock-api");

    private final Map<String, Long> total = new HashMap<>();
    private final Map<String, Map<String, Long>> where = new HashMap<>();

    public long get(String id) {
        return total.getOrDefault(id, 0L);
    }

    /** Mutable copy of all totals, for {@link CraftingTree#build} to consume. */
    public Map<String, Long> totals() {
        return new HashMap<>(total);
    }

    /** Source name → amount, e.g. {"Sacks": 1200, "Inventory": 64}. */
    public Map<String, Long> where(String id) {
        return where.getOrDefault(id, Map.of());
    }

    void add(String source, String id, long amount) {
        if (id == null || amount <= 0) return;
        total.merge(id, amount, Long::sum);
        where.computeIfAbsent(id, k -> new LinkedHashMap<>()).merge(source, amount, Long::sum);
    }

    void add(String source, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) add(source, SkyData.idOf(stack), stack.getCount());
    }

    public static ItemCounts collect() {
        ItemCounts c = new ItemCounts();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) c.add("Inventory", inv.getItem(i));
        }
        // Sacks, storage, vault etc. come from SkyBlockAPI (bundled with SkyOcean), which
        // remembers them per profile once you've opened them.
        if (SKYBLOCK_API) {
            try {
                SkyblockApiSource.addTo(c);
            } catch (Throwable t) {
                SkyTree.LOGGER.warn("Reading SkyBlockAPI storage failed, counting inventory only", t);
            }
        }
        return c;
    }
}
