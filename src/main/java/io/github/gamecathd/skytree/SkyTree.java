package io.github.gamecathd.skytree;

import cc.cassian.rrv.api.overlay.OverlayView;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyTree implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("skytree");

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skytree", "main"));
    static final KeyMapping TREE_KEY = new KeyMapping("key.skytree.tree", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    static final KeyMapping PIN_KEY = new KeyMapping("key.skytree.pin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    /** Screens opened from chat commands must wait a tick, or closing the chat replaces them. */
    private static Screen pending;
    /** RRV's overlay and the container screen may both see one key press; act once. */
    private static KeyEvent lastEvent;

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(TREE_KEY);
        KeyMappingHelper.registerKeyMapping(PIN_KEY);
        Pins.load();
        SkyRecipes.addDataReadyListener(r -> PinHud.invalidate());

        // Items in RRV's item list / bookmarks.
        OverlayView.registerGlobalOverlayKeybindSlotHandler((event, slot, overlay) -> onKey(event, slot.getStack()));

        // Items in any container (inventory, Hypixel menus).
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                var slot = container.hoveredSlot;
                return slot == null || !onKey(event, slot.getItem());
            });
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                int w2 = PinHud.width(mc.font);
                if (container.leftPos >= w2 + 8) PinHud.render(g, 4, container.topPos);
            });
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skytree", "pins"), (g, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            PinHud.render(g, 4, mc.getWindow().getGuiScaledHeight() / 4);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pending != null) {
                mc.gui.setScreen(pending);
                pending = null;
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
                ClientCommands.literal("skytree")
                        .then(itemArg().executes(c -> openTree(c, 1))
                                .then(ClientCommands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(c -> openTree(c, LongArgumentType.getLong(c, "amount")))))
                        .then(ClientCommands.literal("pin").then(itemArg().executes(c -> pin(c, 1))
                                .then(ClientCommands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(c -> pin(c, LongArgumentType.getLong(c, "amount"))))))
                        .then(ClientCommands.literal("unpin").then(itemArg().executes(c -> {
                            Pins.remove(item(c));
                            return 1;
                        })))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            Pins.clear();
                            c.getSource().sendFeedback(Component.literal("§7[SkyTree] All pins removed."));
                            return 1;
                        }))));
    }

    /** @return true if the key was ours and the stack had a SkyBlock id. */
    private static boolean onKey(KeyEvent event, ItemStack stack) {
        boolean tree = TREE_KEY.matches(event);
        if (!tree && !PIN_KEY.matches(event)) return false;
        if (event == lastEvent) return true;
        String id = stack == null || stack.isEmpty() ? null : SkyData.idOf(stack);
        if (id == null || SkyData.registry() == null) return false;
        lastEvent = event;

        Minecraft mc = Minecraft.getInstance();
        if (tree) {
            mc.gui.setScreen(new TreeScreen(mc.gui.screen(), id, 1));
        } else {
            boolean pinned = Pins.toggle(id, 1);
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§7[SkyTree] " + (pinned ? "Pinned " : "Unpinned ") + SkyData.name(id)));
            }
        }
        return true;
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> itemArg() {
        return ClientCommands.argument("item", StringArgumentType.word()).suggests((c, b) -> {
            var reg = SkyData.registry();
            return reg == null ? b.buildFuture()
                    : SharedSuggestionProvider.suggest(reg.getCanonicalItems().stream().map(i -> i.internalName()), b);
        });
    }

    private static String item(CommandContext<FabricClientCommandSource> c) {
        return StringArgumentType.getString(c, "item").toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean known(CommandContext<FabricClientCommandSource> c, String id) {
        var reg = SkyData.registry();
        if (reg != null && reg.contains(id)) return true;
        c.getSource().sendError(Component.literal(reg == null ? "SkyRecipes data is still loading." : "Unknown item: " + id));
        return false;
    }

    private static int openTree(CommandContext<FabricClientCommandSource> c, long amount) {
        String id = item(c);
        if (!known(c, id)) return 0;
        pending = new TreeScreen(null, id, amount);
        return 1;
    }

    private static int pin(CommandContext<FabricClientCommandSource> c, long amount) {
        String id = item(c);
        if (!known(c, id)) return 0;
        Pins.set(id, amount);
        c.getSource().sendFeedback(Component.literal("§7[SkyTree] Pinned " + SkyData.name(id)));
        return 1;
    }
}
