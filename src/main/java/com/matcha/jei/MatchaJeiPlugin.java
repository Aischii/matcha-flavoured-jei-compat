package com.matcha.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@JeiPlugin
public class MatchaJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("matchajei");
    private static final Identifier PLUGIN_UID = Identifier.fromNamespaceAndPath("matchajei", "plugin");
    private static final String SPEC_PATH = "/reskins.json";
    private static final int MAX_DECODE_WARNINGS = 5;

    // JEI's own vanilla plugin registers subtype interpreters for these items before any other plugin runs, and JEI
    // keeps the first one registered. They're identical in JEI 29 (26.1) through 31 (26.3). Matcha variants of these
    // items are still told apart, because each variant stack carries the recipe output's full components (stored
    // enchantments, potion contents, banner patterns), which is what JEI's interpreters compare.
    // Keep in sync with JEI_HANDLED in tools/extract_reskins.py.
    private static final Set<String> JEI_HANDLED = Set.of(
            "minecraft:decorated_pot", "minecraft:enchanted_book", "minecraft:firework_rocket",
            "minecraft:firework_star", "minecraft:goat_horn", "minecraft:light",
            "minecraft:lingering_potion", "minecraft:ominous_bottle", "minecraft:painting",
            "minecraft:potion", "minecraft:shield", "minecraft:splash_potion",
            "minecraft:suspicious_stew", "minecraft:tipped_arrow"
    );

    // Keys costumed variants by their item_model (a stable Identifier, identical on the recipe output and the
    // ingredient we add), so JEI matches "show recipes" (R) to the datapack recipes. Falls back to the translation key
    // of item_name, then custom_name, for variants with a custom name but no custom model. Never uses
    // Component#getString() on translatable names, whose result depends on language-load timing and would desync the
    // two stacks' keys.
    private static final ISubtypeInterpreter<ItemStack> COSTUME_INTERPRETER = (stack, context) -> {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (model != null) {
            return model.toString();
        }
        Component name = stack.get(DataComponents.ITEM_NAME);
        if (name == null) {
            name = stack.get(DataComponents.CUSTOM_NAME);
        }
        if (name != null) {
            return "name:" + (name.getContents() instanceof TranslatableContents tc ? tc.getKey() : name.getString());
        }
        return "";
    };

    private final Spec spec = loadSpec();

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        int registered = 0;
        int jeiHandled = 0;
        for (String id : spec.baseItems) {
            Optional<Item> item = lookupItem(id);
            if (item.isEmpty()) {
                continue;
            }
            if (JEI_HANDLED.contains(BuiltInRegistries.ITEM.getKey(item.get()).toString())) {
                jeiHandled++;
                continue;
            }
            registration.registerSubtypeInterpreter(item.get(), COSTUME_INTERPRETER);
            registered++;
        }
        LOGGER.info("[matchajei] registered subtype interpreters for {} base items ({} already handled by JEI, {} in spec)",
                registered, jeiHandled, spec.baseItems.size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        DynamicOps<JsonElement> ops = serializationOps();
        List<ItemStack> variants = new ArrayList<>(spec.variants.size());
        int failed = 0;
        for (Variant variant : spec.variants) {
            Optional<Item> item = lookupItem(variant.base);
            if (item.isEmpty()) {
                continue;
            }
            ItemStack stack = new ItemStack(item.get());
            // Decode with the connected world's registries, so data-driven entries (enchantments, banner patterns)
            // resolve to the same holders the server's recipe outputs use. Unknown entries are dropped, not fatal.
            DataResult<DataComponentPatch> result = DataComponentPatch.CODEC.parse(ops, variant.components);
            if (result.error().isPresent() && failed++ < MAX_DECODE_WARNINGS) {
                LOGGER.warn("[matchajei] some components of a {} variant could not be decoded: {}", variant.base,
                        result.error().get().message());
            }
            result.resultOrPartial().ifPresent(stack::applyComponents);
            variants.add(stack);
        }
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, variants);
        LOGGER.info("[matchajei] added {} costumed variants as JEI ingredients ({} with undecodable components)",
                variants.size(), failed);
    }

    private static DynamicOps<JsonElement> serializationOps() {
        Minecraft minecraft = Minecraft.getInstance();
        HolderLookup.Provider registries = null;
        if (minecraft.getConnection() != null) {
            registries = minecraft.getConnection().registryAccess();
        } else if (minecraft.level != null) {
            registries = minecraft.level.registryAccess();
        }
        if (registries == null) {
            LOGGER.warn("[matchajei] no world registries available; data-driven components may not decode");
            return JsonOps.INSTANCE;
        }
        return registries.createSerializationContext(JsonOps.INSTANCE);
    }

    private static Optional<Item> lookupItem(String id) {
        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null) {
            LOGGER.warn("[matchajei] skipping malformed item id: {}", id);
            return Optional.empty();
        }
        var holder = BuiltInRegistries.ITEM.get(itemId);
        if (holder.isEmpty()) {
            LOGGER.warn("[matchajei] item not found in registry: {}", id);
            return Optional.empty();
        }
        return Optional.of(holder.get().value());
    }

    private static Spec loadSpec() {
        try (InputStream in = MatchaJeiPlugin.class.getResourceAsStream(SPEC_PATH)) {
            if (in == null) {
                throw new IllegalStateException("resource not found on classpath: " + SPEC_PATH);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                List<String> baseItems = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("base_items")) {
                    baseItems.add(el.getAsString());
                }

                List<Variant> variants = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("variants")) {
                    JsonObject obj = el.getAsJsonObject();
                    JsonElement components = obj.get("components");
                    variants.add(new Variant(
                            obj.get("base").getAsString(),
                            components != null && components.isJsonObject() ? components : new JsonObject()
                    ));
                }
                return new Spec(baseItems, variants);
            }
        } catch (Exception e) {
            throw new IllegalStateException("[matchajei] failed to load reskins spec from " + SPEC_PATH, e);
        }
    }

    private record Spec(List<String> baseItems, List<Variant> variants) {}

    private record Variant(String base, JsonElement components) {}
}
