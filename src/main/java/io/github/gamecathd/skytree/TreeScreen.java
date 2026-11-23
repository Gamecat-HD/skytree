package io.github.gamecathd.skytree;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Crafting tree of one item: expandable ingredient tree on the left, summed raw materials
 * (with what you already own, hover for where) on the right.
 * Items you already own are subtracted along the way.
 * Left click a row folds it, right click opens the item's recipes in RRV, middle click
 * switches to the item's next recipe when it has several.
 */
public class TreeScreen extends Screen {

    private static final int ROW = 18;
    private static final int TOP = 32;

    private final Screen parent;
    private final String id;
    private long amount;
    private CraftingTree.Node root;
    private Map<String, CraftingTree.Total> raw;
    private ItemCounts have;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private record Row(CraftingTree.Node node, int depth, String path) {}
    /** Folded rows by path from the root, so folding survives rebuilds. */
    private final java.util.Set<String> collapsed = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private double scrollTree, scrollRaw;
    private Button pinButton;

    public TreeScreen(Screen parent, String id, long amount) {
        super(Component.literal("Crafting Tree"));
        this.parent = parent;
        this.id = id;
        this.amount = Math.max(1, amount);
        rebuild();
    }

    private void rebuild() {
        have = ItemCounts.collect();
        root = SkyData.tree(id, amount, have.totals());
        raw = CraftingTree.rawMaterials(root);
        refreshRows();
    }

    private void refreshRows() {
        rows.clear();
        addRows(root, 0, root.id);
    }

    private void addRows(CraftingTree.Node n, int depth, String path) {
        rows.add(new Row(n, depth, path));
        if (collapsed.contains(path)) return;
        for (int i = 0; i < n.children.size(); i++) {
            CraftingTree.Node c = n.children.get(i);
            addRows(c, depth + 1, path + "/" + i + ":" + c.id);
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setAmount(amount - 1)).bounds(8, 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setAmount(amount + 1)).bounds(30, 6, 20, 20).build());
        pinButton = addRenderableWidget(Button.builder(pinLabel(), b -> {
            Pins.toggle(id, amount);
            b.setMessage(pinLabel());
        }).bounds(width - 68, 6, 60, 20).build());
    }

    private Component pinLabel() {
        return Component.literal(Pins.isPinned(id) ? "Unpin" : "Pin");
    }

    private void setAmount(long a) {
        amount = Math.max(1, a);
        rebuild();
        if (Pins.isPinned(id)) Pins.set(id, amount);
    }

    private int split() {
        return width * 3 / 5;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, SkyData.name(id) + " §7x" + amount, 58, 12, 0xFFFFFFFF, true);
        g.text(font, "§8Left: fold · Right: recipes · Middle: other recipe", 8, TOP - 12, 0xFFFFFFFF, false);
        pinButton.setMessage(pinLabel());

        int split = split();
        int bottom = height - 8;
        g.fill(split, TOP, split + 1, bottom, 0x40FFFFFF);

        // Tree
        g.enableScissor(0, TOP, split, bottom);
        int y = TOP - (int) scrollTree;
        ItemStack hovered = null;
        String hoveredRaw = null;
        for (Row r : rows) {
            if (y + ROW > TOP && y < bottom) {
                int x = 8 + r.depth * 12;
                CraftingTree.Node n = r.node;
                if (!n.children.isEmpty()) g.text(font, collapsed.contains(r.path) ? "+" : "-", x, y + 4, 0xFFAAAAAA, false);
                ItemStack s = stack(n.id);
                g.item(s, x + 8, y);
                g.text(font, rowText(n), x + 28, y + 4, 0xFFFFFFFF, true);
                if (mouseY >= y && mouseY < y + ROW && mouseX < split && mouseY >= TOP && mouseY < bottom) {
                    g.fill(0, y, split, y + ROW, 0x20FFFFFF);
                    if (mouseX >= x + 8 && mouseX < x + 24) hovered = s;
                }
            }
            y += ROW;
        }
        g.disableScissor();

        // Raw materials
        g.text(font, "§lRaw materials", split + 8, TOP - 12, 0xFFFFFFFF, true);
        g.enableScissor(split + 1, TOP, width, bottom);
        y = TOP - (int) scrollRaw;
        for (Map.Entry<String, CraftingTree.Total> e : raw.entrySet()) {
            CraftingTree.Total t = e.getValue();
            ItemStack s = stack(e.getKey());
            g.item(s, split + 8, y);
            g.text(font, (t.owned() >= t.need() ? "§a" : "§c") + PinHud.fmt(t.owned()) + "§7/" + PinHud.fmt(t.need()) + " §f" + SkyData.name(e.getKey()),
                    split + 28, y + 4, 0xFFFFFFFF, true);
            if (mouseX >= split && mouseY >= y && mouseY < y + 16 && mouseY >= TOP && mouseY < bottom) {
                if (mouseX < split + 24) hovered = s;
                else hoveredRaw = e.getKey();
            }
            y += ROW;
        }
        g.disableScissor();

        if (hovered != null) {
            g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        } else if (hoveredRaw != null) {
            g.setTooltipForNextFrame(font, whereTooltip(hoveredRaw), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private static String rowText(CraftingTree.Node n) {
        StringBuilder sb = new StringBuilder(n.owned >= n.amount && n.owned > 0 ? "§a✔ " : "");
        sb.append("§e").append(PinHud.fmt(n.amount)).append("§7x ").append(SkyData.name(n.id));
        if (n.owned > 0 && n.owned < n.amount) sb.append(" §a(have ").append(PinHud.fmt(n.owned)).append(")");
        if (n.recipe != null) {
            sb.append(" §8(").append(n.recipe.kind());
            if (n.crafts() > 1) sb.append(" x").append(n.crafts());
            if (n.recipeCount > 1) sb.append(", recipe ").append(n.recipeIndex + 1).append("/").append(n.recipeCount);
            sb.append(")");
        }
        return sb.toString();
    }

    private List<Component> whereTooltip(String itemId) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(SkyData.name(itemId)));
        have.where(itemId).forEach((src, n) -> lines.add(Component.literal("§7" + src + ": §f" + PinHud.fmt(n))));
        if (lines.size() == 1) lines.add(Component.literal("§7None found"));
        return lines;
    }

    private ItemStack stack(String itemId) {
        return stacks.computeIfAbsent(itemId, SkyData::stack);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.x() >= split() || event.y() < TOP) return false;
        int i = (int) ((event.y() - TOP + scrollTree) / ROW);
        if (i < 0 || i >= rows.size()) return false;
        Row row = rows.get(i);
        CraftingTree.Node n = row.node;
        if (event.button() == 1) {
            ItemViewOverlay.INSTANCE.openRecipeView(stack(n.id), ActionType.RESULT);
        } else if (event.button() == 2) {
            if (n.recipeCount > 1) {
                Pins.nextRecipe(n.id, n.recipeCount);
                rebuild();
            }
        } else if (!n.children.isEmpty()) {
            if (!collapsed.remove(row.path)) collapsed.add(row.path);
            refreshRows();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int visible = height - 8 - TOP;
        if (x < split()) {
            scrollTree = clamp(scrollTree - dy * ROW * 2, rows.size() * ROW - visible);
        } else {
            scrollRaw = clamp(scrollRaw - dy * ROW * 2, raw.size() * ROW - visible);
        }
        return true;
    }

    private static double clamp(double v, int max) {
        return Math.max(0, Math.min(v, Math.max(0, max)));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
