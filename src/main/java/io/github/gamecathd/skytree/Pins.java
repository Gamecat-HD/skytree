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

/** Pinned recipes (SkyBlock id → wanted amount), persisted to config/skytree-pins.json. */
public final class Pins {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skytree-pins.json");
    private static final Gson GSON = new Gson();
    private static Map<String, Long> pins = new LinkedHashMap<>();

    private Pins() {}

    public static Map<String, Long> all() {
        return pins;
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
        if (!Files.exists(FILE)) return;
        try {
            Map<String, Long> loaded = GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Long>>() {}.getType());
            if (loaded != null) pins = loaded;
        } catch (Exception e) {
            SkyTree.LOGGER.warn("Could not read {}, starting without pins", FILE, e);
        }
    }

    private static void save() {
        try {
            // Write to a temp file first so a crash mid-write can't wipe the pins.
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(pins));
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SkyTree.LOGGER.warn("Could not save pins to {}", FILE, e);
        }
        PinHud.invalidate();
    }
}
