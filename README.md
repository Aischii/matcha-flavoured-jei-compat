# Matcha Flavoured JEI Compat

A small **client-side** Fabric mod that makes [Just Enough Items (JEI)](https://modrinth.com/mod/jei) display the items and recipes added by the [Matcha Flavoured](https://modrinth.com/datapack/matcha-flavoured) datapack.

> **Fork note:** this is a fork of [KixPanganiban/matcha-flavoured-jei-compat](https://github.com/KixPanganiban/matcha-flavoured-jei-compat), updated so one jar runs on **every Minecraft 26.1–26.3 release**.

## The problem

Matcha Flavoured is a datapack, so it can't register brand-new items. Instead it reskins vanilla items using the `minecraft:item_model` and `minecraft:item_name` data components — a "Bronze Pickaxe" is really a `minecraft:stone_pickaxe` wearing a custom model and name.

JEI groups items by their base type and has no built-in way to tell these "costumed" variants apart. The result: the pack's alloy tools, armour, foods and blessing books never appear in JEI's item list, and pressing **R**/**U** on them shows nothing.

## What this does

- Registers a JEI **subtype interpreter** keyed on the `item_model` component, so JEI treats each costumed variant as its own distinct ingredient.
- Adds every costumed variant (211 variants across 80 base items) to JEI at runtime, so they appear in the item list and their smithing/crafting recipes are browsable.

The variant list is data-driven — see `src/main/resources/reskins.json` — and generated from the datapack (see [Regenerating reskins.json](#regenerating-reskinsjson)).

## Requirements

- Minecraft **26.1, 26.1.1, 26.1.2, 26.2 or 26.3** (one jar for all of them)
- Fabric Loader **≥ 0.18.4**
- Fabric API
- Just Enough Items (JEI) for your Minecraft version (29.2 or newer)
- The **Matcha Flavoured** datapack **and** resource pack (this mod does not include them — grab them from [Modrinth](https://modrinth.com/datapack/matcha-flavoured))

Client-side only — it does nothing on a dedicated server and isn't required there.

## ⚠️ The datapack has to actually be loaded in your world

This mod only teaches JEI how to *display* the pack; it can't show recipes that were never loaded. The Matcha Flavoured download is a **combined datapack + resource pack** — the same zip must be placed in **both** locations:

- Resource pack (textures/names): `resourcepacks/`
- Datapack (recipes/mechanics): the world's `saves/<world>/datapacks/` folder

> **Heads up:** Prism Launcher's "global data packs" feature can silently fail to inject the pack into a world, leaving you with vanilla mechanics under Matcha textures — items look right but no recipes exist. If that happens, drop `Matcha_Flavoured.zip` directly into `saves/<world>/datapacks/`; Minecraft auto-enables it on the next load.

## Install

Download `matcha-flavoured-jei-compat-1.1.1.jar` from [Releases](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/latest) and drop it into your `mods/` folder alongside Fabric API and JEI.

| Mod version | Minecraft | JEI | Where |
|---|---|---|---|
| 1.1.1 | 26.1 – 26.3 | any build for that version (≥ 29.2) | [this fork](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/tag/v1.1.1) |
| 1.1.0 | 26.3 | 31.18 or newer only (too strict, use 1.1.1) | [this fork](https://github.com/Aischii/matcha-flavoured-jei-compat/releases/tag/v1.1.0) |
| 1.0.0 | 26.2 | 30.14.x | [upstream](https://github.com/KixPanganiban/matcha-flavoured-jei-compat/releases/tag/v1.0.0) |

## Build

Requires **JDK 25** (all Minecraft 26.x versions target Java 25).

```sh
./gradlew build
```

The jar is written to `build/libs/`.

> Note: Minecraft 26.x ships an already-deobfuscated client jar, so no Yarn/Mojang mappings are configured. The build uses Fabric Loom `1.19.x` (Gradle 9.9) with plain `implementation`/`compileOnly` dependencies. JEI plugins are discovered via the `jei_mod_plugin` entrypoint in `fabric.mod.json`.

The build compiles against 26.3, but the compiled class makes exactly the same Minecraft and JEI calls when built against 26.1, 26.1.2, 26.2 or 26.3. That's why one jar covers the whole range. Before widening `minecraft` in `fabric.mod.json` for a new release, build against that version and compare the method and field references (`javap -v`).

## Regenerating reskins.json

`reskins.json` lists every recipe output that carries an `item_model`/`item_name` component. To rebuild it against a newer version of the datapack:

```sh
python3 tools/extract_reskins.py path/to/Matcha_Flavoured.zip > src/main/resources/reskins.json
```

## License

[MIT](LICENSE.md) — free for all. Matcha Flavoured itself is a separate work by its own author under its own license; this mod is an independent compatibility layer and bundles none of the datapack's content beyond the item/model identifiers needed to reference it.
