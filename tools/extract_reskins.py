#!/usr/bin/env python3
"""Extract the set of "costumed" items from the Matcha Flavoured datapack.

A costumed item is a recipe output that carries an item_model, item_name or
custom_name component (a vanilla item reskinned by the datapack), or a vanilla
item that JEI already tells apart by its components (enchanted books, potions,
shields...). The mod rebuilds each ItemStack from the base item id plus the
recipe output's full component set, so it matches the recipe output exactly.

Usage:
    python3 tools/extract_reskins.py MFDP.zip [MFRP.zip] > src/main/resources/reskins.json

The optional resource pack zip is only used for the human-readable "name"
field (the mod itself ignores it).
"""
import json
import sys
import zipfile

# Items JEI's own vanilla plugin registers a subtype interpreter for. Keep in
# sync with JEI_HANDLED in MatchaJeiPlugin.java.
JEI_HANDLED = {
    "minecraft:decorated_pot", "minecraft:enchanted_book", "minecraft:firework_rocket",
    "minecraft:firework_star", "minecraft:goat_horn", "minecraft:light",
    "minecraft:lingering_potion", "minecraft:ominous_bottle", "minecraft:painting",
    "minecraft:potion", "minecraft:shield", "minecraft:splash_potion",
    "minecraft:suspicious_stew", "minecraft:tipped_arrow",
}
NAME_COMPONENTS = ("minecraft:item_name", "minecraft:custom_name")


def norm_id(x):
    if isinstance(x, dict):
        x = x.get("item") or x.get("id") or x.get("tag")
    if isinstance(x, str) and not x.startswith("#"):
        return x if ":" in x else "minecraft:" + x
    return None


def load_lang(paths):
    for path in paths:
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.endswith("assets/minecraft/lang/en_us.json"):
                    return json.loads(z.read(n))
    return {}


def display_name(comps, lang):
    for key in NAME_COMPONENTS:
        comp = comps.get(key)
        if isinstance(comp, dict) and comp.get("translate"):
            return lang.get(comp["translate"], comp["translate"])
        if isinstance(comp, str):
            return comp
    return None


def main(paths):
    lang = load_lang(paths)
    variants = {}
    with zipfile.ZipFile(paths[0]) as z:
        for n in sorted(z.namelist()):
            # data/<namespace>/recipe/**/*.json
            parts = n.split("/")
            if len(parts) < 4 or parts[0] != "data" or parts[2] != "recipe" or not n.endswith(".json"):
                continue
            try:
                rec = json.loads(z.read(n))
            except (ValueError, KeyError):
                continue
            res = rec.get("result")
            if not isinstance(res, dict):
                continue
            oid = norm_id(res.get("id") or res.get("item"))
            comps = res.get("components") or {}
            if not oid or not comps:
                continue
            costumed = "minecraft:item_model" in comps or any(k in comps for k in NAME_COMPONENTS)
            if not costumed and oid not in JEI_HANDLED:
                continue

            key = (oid, json.dumps(comps, sort_keys=True))
            if key in variants:
                continue
            variants[key] = {"base": oid, "name": display_name(comps, lang), "components": comps}

    base_items = sorted({v["base"] for v in variants.values()})
    json.dump({"base_items": base_items, "variants": list(variants.values())}, sys.stdout, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    main(sys.argv[1:])
