package io.github.gamecathd.skytree;

import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.accessory.AccessoryBagItem;
import tech.thatgravyboat.skyblockapi.api.profile.items.equipment.EquipmentAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.loadout.ArmorWardrobeAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.loadout.EquipmentWardrobeAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.loadout.WardrobeSlot;
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.PlayerStorageInstance;
import tech.thatgravyboat.skyblockapi.api.profile.items.storage.StorageAPI;
import tech.thatgravyboat.skyblockapi.api.profile.items.vault.VaultAPI;

import java.util.Map;

/** Only loaded when SkyBlockAPI is present (see {@link ItemCounts#collect()}). */
final class SkyblockApiSource {

    private SkyblockApiSource() {}

    static void addTo(ItemCounts c) {
        var reg = SkyData.registry();
        for (Map.Entry<String, Integer> e : SacksAPI.INSTANCE.getSackItems().entrySet()) {
            // Hypixel writes damage variants as LOG:1, NEU as LOG-1.
            String id = e.getKey().replace(':', '-');
            c.add("Sacks", reg == null ? id : reg.getCanonicalName(id), e.getValue());
        }
        for (PlayerStorageInstance page : StorageAPI.INSTANCE.getEnderchests()) page.getItems().forEach(s -> c.add("Ender Chest", s));
        for (PlayerStorageInstance page : StorageAPI.INSTANCE.getBackpacks()) page.getItems().forEach(s -> c.add("Backpacks", s));
        VaultAPI.INSTANCE.getItems().forEach(s -> c.add("Vault", s));
        // The worn set is already counted via inventory armor slots / equipment.
        for (WardrobeSlot slot : ArmorWardrobeAPI.INSTANCE.getSlots()) {
            if (!Integer.valueOf(slot.getId()).equals(ArmorWardrobeAPI.INSTANCE.getCurrentSlot())) slot.getSlots().forEach(s -> c.add("Wardrobe", s));
        }
        for (WardrobeSlot slot : EquipmentWardrobeAPI.INSTANCE.getSlots()) {
            if (!Integer.valueOf(slot.getId()).equals(EquipmentWardrobeAPI.INSTANCE.getCurrentSlot())) slot.getSlots().forEach(s -> c.add("Wardrobe", s));
        }
        EquipmentAPI.INSTANCE.getNormalEquipment().values().forEach(s -> c.add("Equipment", s));
        for (AccessoryBagItem a : AccessoryBagAPI.INSTANCE.getItems()) c.add("Accessory Bag", a.getItem());
    }
}
