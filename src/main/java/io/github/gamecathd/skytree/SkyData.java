package io.github.gamecathd.skytree;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bridge to SkyRecipes' item registry (NEU repo data). */
public final class SkyData {

    private SkyData() {}

    public static ItemRegistry registry() {
        return SkyRecipes.isDataReady() ? SkyRecipes.getItemRegistry() : null;
    }

    public static CraftingTree.Node tree(String id, long amount) {
        return CraftingTree.build(id, amount, SkyData::recipeFor);
    }

    /**
     * First crafting recipe, else first forge recipe. Vanilla items are treated as raw
     * materials so NEU's vanilla un-crafting recipes (diamond ← diamond block) don't loop.
     */
    static CraftingTree.Recipe recipeFor(String id) {
        ItemRegistry reg = registry();
        NeuItem item = reg == null ? null : reg.getOrNull(id);
        if (item == null || item.vanilla()) return null;

        List<NeuRecipe> all = new ArrayList<>();
        if (item.recipe() != null) all.add(item.recipe());
        if (item.recipes() != null) all.addAll(item.recipes());

        for (NeuRecipe r : all) {
            if (r instanceof NeuRecipe.CraftingRecipe c && isFor(id, c.overrideOutputId())) {
                return new CraftingTree.Recipe("crafting", Math.max(1, c.count()), inputs(c.grid().values()));
            }
        }
        for (NeuRecipe r : all) {
            if (r instanceof NeuRecipe.ForgeRecipe f && isFor(id, f.overrideOutputId())) {
                return new CraftingTree.Recipe("forge", Math.max(1, f.count()), inputs(f.inputs()));
            }
        }
        return null;
    }

    private static boolean isFor(String id, String overrideOutputId) {
        return overrideOutputId == null || overrideOutputId.isEmpty() || overrideOutputId.equals(id);
    }

    private static Map<String, Long> inputs(Iterable<String> refs) {
        ItemRegistry reg = registry();
        Map<String, Long> out = new LinkedHashMap<>();
        for (String raw : refs) {
            SlotRefParser.IngredientRef ref = SlotRefParser.parse(raw);
            if (ref != null && ref.count() > 0) out.merge(reg.getCanonicalName(ref.internalName()), (long) ref.count(), Long::sum);
        }
        return out;
    }

    public static ItemStack stack(String id) {
        ItemRegistry reg = registry();
        NeuItem item = reg == null ? null : reg.getOrNull(id);
        return item != null ? ItemStackBuilder.build(item, 1) : new ItemStack(Items.BARRIER);
    }

    public static String name(String id) {
        ItemRegistry reg = registry();
        NeuItem item = reg == null ? null : reg.getOrNull(id);
        return item != null ? item.displayName() : id;
    }

    /** SkyBlock id of a stack (live Hypixel item or SkyRecipes display stack), or null. */
    public static String idOf(ItemStack stack) {
        String id = SkyblockIdExtractor.extract(stack);
        ItemRegistry reg = registry();
        return id == null || reg == null ? id : reg.getCanonicalName(id);
    }

    /**
     * How many of each id the player carries.
     * shortcut: inventory only, no sacks/storage; add sack data if someone asks.
     */
    public static Map<String, Long> inventoryCounts() {
        Map<String, Long> counts = new HashMap<>();
        if (Minecraft.getInstance().player == null) return counts;
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            String id = s.isEmpty() ? null : idOf(s);
            if (id != null) counts.merge(id, (long) s.getCount(), Long::sum);
        }
        return counts;
    }
}
