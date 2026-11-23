# SkyTree

A small client-side add-on for [SkyRecipes](https://modrinth.com/mod/enhanced-skyrecipes) (Hypixel SkyBlock recipes in [RRV](https://modrinth.com/mod/rrv)).
SkyTree adds:

- **Crafting trees**: the full crafting and forge tree of an item, with the summed **raw materials** and how many of each you already carry.
- **Pinned recipes**: pin an item and its ingredients stay on your HUD (and next to open menus) with live have/need counts from your inventory.

All item and recipe data comes from SkyRecipes (NEU repo), so SkyTree has no data of its own.

## Usage

| Action | How |
|---|---|
| Open crafting tree | Hover an item (RRV item list, bookmarks, inventory or any SkyBlock menu) and press **Y** |
| Pin / unpin a recipe | Hover an item and press **P** |
| Tree via command | `/skytree <ITEM_ID> [amount]`, e.g. `/skytree HYPERION` |
| Pin via command | `/skytree pin <ITEM_ID> [amount]`, `/skytree unpin <ITEM_ID>`, `/skytree clear` |

In the tree screen:

- **Left click** a row to fold or unfold it, **right click** to open that item's recipes in RRV.
- **− / +** change the amount you want to craft; the whole tree and raw-material list scale with it.
- **Pin** pins the item with the current amount.

Keys can be changed under *Options → Controls → SkyTree*. Pins are saved in `config/skytree-pins.json`.

## Notes

- The tree uses an item's first crafting recipe, otherwise its first forge recipe. Vanilla items count as raw materials.
- "Have" counts only look at your inventory, not sacks or storage.

## Requirements

Minecraft 26.2, Fabric Loader, Fabric API, SkyRecipes ≥ 0.5.13, RRV.

## Building

```sh
./gradlew build   # needs JDK 25; jar in build/libs/
```

## License

GPL-3.0, like SkyRecipes.
