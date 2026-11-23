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
import java.util.List;
import java.util.Map;

/**
 * Crafting tree of one item: expandable ingredient tree on the left, summed raw materials
 * (with what you already own, hover for where) on the right.
 * Left click a row folds it, right click opens the item's recipes in RRV.
 */
public class TreeScreen extends Screen {

    private static final int ROW = 18;
    private static final int TOP = 32;

    private final Screen parent;
    private final String id;
    private long amount;
    private CraftingTree.Node root;
    private Map<String, Long> raw;
    private ItemCounts have;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private record Row(CraftingTree.Node node, int depth) {}
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
        root = SkyData.tree(id, amount);
        raw = CraftingTree.rawMaterials(root);
        have = ItemCounts.collect();
        refreshRows();
    }

    private void refreshRows() {
        rows.clear();
        addRows(root, 0);
    }

    private void addRows(CraftingTree.Node n, int depth) {
        rows.add(new Row(n, depth));
        if (!n.collapsed) n.children.forEach(c -> addRows(c, depth + 1));
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
                if (!n.children.isEmpty()) g.text(font, n.collapsed ? "+" : "-", x, y + 4, 0xFFAAAAAA, false);
                ItemStack s = stack(n.id);
                g.item(s, x + 8, y);
                String info = n.recipe == null ? "" : " §8(" + n.recipe.kind() + (n.crafts() > 1 ? " x" + n.crafts() : "") + ")";
                g.text(font, "§e" + PinHud.fmt(n.amount) + "§7x " + SkyData.name(n.id) + info, x + 28, y + 4, 0xFFFFFFFF, true);
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
        for (Map.Entry<String, Long> e : raw.entrySet()) {
            long n = have.get(e.getKey());
            ItemStack s = stack(e.getKey());
            g.item(s, split + 8, y);
            g.text(font, (n >= e.getValue() ? "§a" : "§c") + PinHud.fmt(n) + "§7/" + PinHud.fmt(e.getValue()) + " §f" + SkyData.name(e.getKey()),
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
        CraftingTree.Node n = rows.get(i).node;
        if (event.button() == 1) {
            ItemViewOverlay.INSTANCE.openRecipeView(stack(n.id), ActionType.RESULT);
        } else if (!n.children.isEmpty()) {
            n.collapsed = !n.collapsed;
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
