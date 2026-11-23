package io.github.gamecathd.skytree;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Draws the pinned recipes with have/need counts from the player's inventory. */
public final class PinHud {

    private record Line(String id, long need) {}
    private record Pin(String id, long amount, ItemStack icon, String name, List<Line> lines) {}

    private static List<Pin> cache;
    private static Map<String, Long> have = Map.of();
    private static long haveAt;

    private PinHud() {}

    public static void invalidate() {
        cache = null;
    }

    private static List<Pin> pins() {
        if (cache != null) return cache;
        if (SkyData.registry() == null) return List.of();
        List<Pin> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : Pins.all().entrySet()) {
            CraftingTree.Node node = SkyData.tree(e.getKey(), e.getValue());
            List<Line> lines = new ArrayList<>();
            // Direct ingredients of the recipe; a pinned raw material just tracks itself.
            if (node.children.isEmpty()) lines.add(new Line(node.id, node.amount));
            for (CraftingTree.Node c : node.children) lines.add(new Line(c.id, c.amount));
            out.add(new Pin(node.id, node.amount, SkyData.stack(node.id), SkyData.name(node.id), lines));
        }
        return cache = out;
    }

    public static int width(Font font) {
        int w = 0;
        for (Pin p : pins()) {
            w = Math.max(w, 20 + font.width(header(p)));
            for (Line l : p.lines) w = Math.max(w, 6 + font.width(line(l)));
        }
        return w + 8;
    }

    public static void render(GuiGraphicsExtractor g, int x, int y) {
        List<Pin> pins = pins();
        if (pins.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        if (System.currentTimeMillis() - haveAt > 500) {
            have = SkyData.inventoryCounts();
            haveAt = System.currentTimeMillis();
        }

        int w = width(font);
        int h = 4;
        for (Pin p : pins) h += 18 + p.lines.size() * 10 + 2;
        g.fill(x, y, x + w, y + h, 0x90000000);

        int cy = y + 4;
        for (Pin p : pins) {
            g.item(p.icon, x + 4, cy);
            g.text(font, header(p), x + 22, cy + 4, 0xFFFFFFFF, true);
            cy += 18;
            for (Line l : p.lines) {
                g.text(font, line(l), x + 10, cy, 0xFFFFFFFF, true);
                cy += 10;
            }
            cy += 2;
        }
    }

    private static String header(Pin p) {
        boolean done = p.lines.stream().allMatch(l -> have.getOrDefault(l.id, 0L) >= l.need);
        return (done ? "§a✔ " : "") + p.name + (p.amount > 1 ? " §7x" + p.amount : "");
    }

    private static String line(Line l) {
        long n = have.getOrDefault(l.id, 0L);
        return (n >= l.need ? "§a" : "§c") + fmt(n) + "§7/" + fmt(l.need) + " §f" + SkyData.name(l.id);
    }

    static String fmt(long n) {
        if (n >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fm", n / 1e6);
        if (n >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", n / 1e3);
        return Long.toString(n);
    }
}
