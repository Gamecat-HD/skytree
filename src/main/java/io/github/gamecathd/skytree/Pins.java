package io.github.gamecathd.skytree;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pinned recipes (SkyBlock id → wanted amount) and the chosen recipe for items with several,
 * persisted to config/skytree-pins.json and config/skytree-recipes.json.
 */
public final class Pins {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skytree-pins.json");
    private static final Path CHOICES_FILE = FabricLoader.getInstance().getConfigDir().resolve("skytree-recipes.json");
    private static final Gson GSON = new Gson();
    private static Map<String, Long> pins = new LinkedHashMap<>();
    private static Map<String, Integer> choices = new LinkedHashMap<>();

    private Pins() {}

    public static Map<String, Long> all() {
        return pins;
    }

    public static Map<String, Integer> recipeChoices() {
        return choices;
    }

    /** Switches an item to its next recipe (wraps around). */
    public static void nextRecipe(String id, int recipeCount) {
        int next = (choices.getOrDefault(id, 0) + 1) % Math.max(1, recipeCount);
        if (next == 0) choices.remove(id);
        else choices.put(id, next);
        write(CHOICES_FILE, choices);
    }

    public static boolean isPinned(String id) {
        return pins.containsKey(id);
    }

    /** @return true if the item is pinned afterwards */
    public static boolean toggle(String id, long amount) {
        boolean pinned = pins.remove(id) == null;
        if (pinned) pins.put(id, Math.max(1, amount));
        save();
        return pinned;
    }

    public static void set(String id, long amount) {
        pins.put(id, Math.max(1, amount));
        save();
    }

    public static void remove(String id) {
        if (pins.remove(id) != null) save();
    }

    public static void clear() {
        pins.clear();
        save();
    }

    static void load() {
        Map<String, Long> p = read(FILE, new TypeToken<LinkedHashMap<String, Long>>() {});
        if (p != null) pins = p;
        Map<String, Integer> c = read(CHOICES_FILE, new TypeToken<LinkedHashMap<String, Integer>>() {});
        if (c != null) choices = c;
    }

    private static <T> T read(Path file, TypeToken<T> type) {
        if (!Files.exists(file)) return null;
        try {
            return GSON.fromJson(Files.readString(file), type.getType());
        } catch (Exception e) {
            SkyTree.LOGGER.warn("Could not read {}, ignoring it", file, e);
            return null;
        }
    }

    private static void save() {
        write(FILE, pins);
    }

    private static void write(Path file, Object data) {
        try {
            // Write to a temp file first so a crash mid-write can't wipe the file.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SkyTree.LOGGER.warn("Could not save {}", file, e);
        }
        PinHud.invalidate();
    }
}
