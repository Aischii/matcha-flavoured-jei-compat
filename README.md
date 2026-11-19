# Matcha Flavoured JEI Compat

A small **client-side** Fabric mod that makes [Just Enough Items (JEI)](https://modrinth.com/mod/jei) display the items and recipes added by the [Matcha Flavoured](https://modrinth.com/datapack/matcha-flavoured) datapack.

> **Fork note:** this is a fork of [KixPanganiban/matcha-flavoured-jei-compat](https://github.com/KixPanganiban/matcha-flavoured-jei-compat), updated so one jar runs on **every Minecraft 26.1–26.3 release**.

## The problem

Matcha Flavoured is a datapack, so it can't register brand-new items. Instead it reskins vanilla items using the `minecraft:item_model` and `minecraft:item_name` data components — a "Bronze Pickaxe" is really a `minecraft:stone_pickaxe` wearing a custom model and name.

JEI groups items by their base type and has no built-in way to tell these "costumed" variants apart. The result: the pack's alloy tools, armour, foods and blessing books never appear in JEI's item list, and pressing **R**/**U** on them shows nothing.

## What this does

- Registers a JEI **subtype interpreter** keyed on the `item_model` component (or the item's name when there's no custom model), so JEI treats each costumed variant as its own distinct ingredient.
- Adds every costumed variant (236 variants across 69 base items, from Matcha Flavoured 1.12.9) to JEI at runtime, so they appear in the item list and their smithing/crafting recipes are browsable. Each one carries the recipe output's full set of components (stored enchantments, lore, stats…), so it matches the recipe output exactly.

Some vanilla items (enchanted books, potions, shields, fireworks…) already have their own subtype handling in JEI, which this mod can't replace. For those, JEI tells Matcha's variants apart by their own components instead. Each **Blessing** book is its own entry because of its stored enchantments, and the **Warding Shield** because of its banner pattern.

**Known limitation:** JEI tells splash potions apart only by their potion type. Matcha's four throwable foods (Bubbling Mead and the pickled fungi/mushrooms) have custom effects and no potion type, so JEI shows them as **one** entry. Pressing **R** on it lists all four recipes.

The variant list is data-driven — see `src/main/resources/reskins.json` — and generated from the datapack (see [Regenerating reskins.json](#regenerating-reskinsjson)).

## Requirements

- Minecraft **26.1, 26.1.1, 26.1.2, 26.2 or 26.3** (one jar for all of them)
- Fabric Loader **≥ 0.18.4**
- Fabric API
- Just Enough Items (JEI) for your Minecraft version (29.2 or newer)
- The **Matcha Flavoured** datapack **and** resource pack (this mod does not include them — grab them from [Modrinth](https://modrinth.com/datapack/matcha-flavoured))

Client-side only. This mod isn't needed on a server, but **JEI is** (see below).

## ⚠️ Recipes only show when they're actually loaded

This mod only teaches JEI how to *display* the pack; it can't show recipes your game doesn't know about.

**Single-player:** Matcha Flavoured must be active in the world. Use either the mod jar in `mods/`, or the datapack zip (`MFDP_…zip`) in `saves/<world>/datapacks/` plus the resource pack zip (`MFRP_…zip`) in `resourcepacks/`.

> **Heads up:** Prism Launcher's "global data packs" feature can silently fail to inject the pack into a world, leaving you with vanilla mechanics under Matcha textures — items look right but no recipes exist. If that happens, drop the datapack zip directly into `saves/<world>/datapacks/`; Minecraft auto-enables it on the next load.

**Multiplayer:** since Minecraft 1.21.2, servers no longer send their recipes to players. JEI only sees a server's recipes, including Matcha's, if **JEI is installed on the server** as well. Without it, JEI shows your client's vanilla recipes, and pressing **R** on a Matcha item shows nothing. Players without JEI can still join a server that has it. The Matcha items still appear in JEI's list either way, because this mod adds them.

## Install

Download `matcha-flavoured-jei-compat-1.1.2.jar` from [Releases](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/latest) and drop it into your `mods/` folder alongside Fabric API and JEI.

| Mod version | Minecraft | JEI | Where |
|---|---|---|---|
| 1.1.2 | 26.1 – 26.3 | any build for that version (≥ 29.2) | [this fork](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/tag/v1.1.2) |
| 1.1.1 | 26.1 – 26.3 | any build for that version (≥ 29.2); item list for Matcha < 1.12.9 | [this fork](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/tag/v1.1.1) |
| 1.1.0 | 26.3 | 31.18 or newer only (too strict, use 1.1.1) | [this fork](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/tag/v1.1.0) |
| 1.0.0 | 26.2 | 30.14.x | [upstream](https://github.com/KixPanganiban/matcha-flavoured-jei-compat/releases/tag/v1.0.0) |

## Build

Requires **JDK 25** (all Minecraft 26.x versions target Java 25).

```sh
./gradlew build
```

The jar is written to `build/libs/`.

> Note: Minecraft 26.x ships an already-deobfuscated client jar, so no Yarn/Mojang mappings are configured. The build uses Fabric Loom `1.19.x` (Gradle 9.9) with plain `implementation`/`compileOnly` dependencies. JEI plugins are discovered via the `jei_mod_plugin` entrypoint in `fabric.mod.json`.

The build compiles against 26.3, but the compiled classes make exactly the same Minecraft and JEI calls when built against 26.1, 26.1.2, 26.2 or 26.3. That's why one jar covers the whole range. Before widening `minecraft` in `fabric.mod.json` for a new release, build against that version and compare the method and field references (`javap -v`).

## Regenerating reskins.json

`reskins.json` lists every recipe output that carries an `item_model`, `item_name` or `custom_name` component, plus outputs of the vanilla items JEI handles itself (enchanted books, potions, shields…), each with its full component set. To rebuild it against a newer version of the datapack:

```sh
python3 tools/extract_reskins.py path/to/MFDP_x_y_z.zip [path/to/MFRP_x_y_z.zip] > src/main/resources/reskins.json
```

The resource pack zip is optional; it only fills in the human-readable `name` field, which the mod ignores. Data-driven entries in the components (Matcha's own enchantments, trim materials, music discs) are decoded in game against the connected world's registries.

## License

[MIT](LICENSE.md) — free for all. Matcha Flavoured itself is a separate work by its own author under its own license; this mod is an independent compatibility layer and bundles none of the datapack's textures, models or recipes. It only includes the recipe outputs' component data (item/model identifiers, translation keys, stats) needed to rebuild those items.
