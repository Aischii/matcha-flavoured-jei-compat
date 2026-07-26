#!/usr/bin/env python3
"""Extract the set of "costumed" items from the Matcha Flavoured datapack.

A costumed item is a recipe output that carries a minecraft:item_model and/or
minecraft:item_name component (i.e. a vanilla item reskinned by the datapack).
The mod needs the base item id + the components so it can rebuild the ItemStack
and register it with JEI.

Usage:
    python3 tools/extract_reskins.py path/to/Matcha_Flavoured.zip > src/main/resources/reskins.json
"""
import json
import sys
import zipfile


def norm_id(x):
    if isinstance(x, dict):
        x = x.get("item") or x.get("id") or x.get("tag")
    if isinstance(x, str) and not x.startswith("#"):
        return x if ":" in x else "minecraft:" + x
    return None


def main(zip_path):
    with zipfile.ZipFile(zip_path) as z:
        names = z.namelist()

        lang = {}
        for n in names:
            if n.endswith("assets/minecraft/lang/en_us.json"):
                lang = json.loads(z.read(n))
                break

        variants = {}
        base_items = set()
        for n in names:
            # data/<namespace>/recipe/**/*.json
            parts = n.split("/")
            if len(parts) < 4 or parts[0] != "data" or parts[2] != "recipe" or not n.endswith(".json"):
                continue
            try:
                rec = json.loads(z.read(n))
            except (ValueError, KeyError):
                continue
            res = rec.get("result")
            if isinstance(res, str):
                res = {"id": res}
            if not isinstance(res, dict):
                continue
            oid = norm_id(res.get("id") or res.get("item"))
            comps = res.get("components", {})
            if not oid or not comps:
                continue
            model = comps.get("minecraft:item_model")
            name_comp = comps.get("minecraft:item_name")
            if not model and not name_comp:
                continue

            name_key = name_comp.get("translate") if isinstance(name_comp, dict) else None
            name = None
            if name_key:
                name = lang.get(name_key, name_key)
            elif isinstance(name_comp, str):
                name = name_comp

            key = (oid, model or "", json.dumps(name_comp, sort_keys=True) if name_comp else "")
            if key in variants:
                continue
            base_items.add(oid)
            variants[key] = {"base": oid, "model": model, "name": name, "name_key": name_key}

    json.dump(
        {"base_items": sorted(base_items), "variants": list(variants.values())},
        sys.stdout,
        indent=1,
    )
    sys.stdout.write("\n")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
