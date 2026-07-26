package com.matcha.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class MatchaJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("matchajei");
    private static final Identifier PLUGIN_UID = Identifier.fromNamespaceAndPath("matchajei", "plugin");
    private static final String SPEC_PATH = "/reskins.json";

    // Keys costumed variants by their item_model (a stable Identifier, identical on the recipe output and the
    // ingredient we add), so JEI matches "show recipes" (R) to the datapack recipes. Falls back to the item_name's
    // translation key (also stable) for the rare variant that has a custom name but no custom model. Never uses
    // Component#getString(), whose result depends on language-load timing and would desync the two stacks' keys.
    private static final ISubtypeInterpreter<ItemStack> COSTUME_INTERPRETER = (stack, context) -> {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (model != null) {
            return model.toString();
        }
        Component name = stack.get(DataComponents.ITEM_NAME);
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
        for (String id : spec.baseItems) {
            Identifier itemId = Identifier.tryParse(id);
            if (itemId == null) {
                LOGGER.warn("[matchajei] skipping malformed base item id: {}", id);
                continue;
            }
            var holder = BuiltInRegistries.ITEM.get(itemId);
            if (holder.isEmpty()) {
                LOGGER.warn("[matchajei] base item not found in registry: {}", id);
                continue;
            }
            registration.registerSubtypeInterpreter(holder.get().value(), COSTUME_INTERPRETER);
            registered++;
        }
        LOGGER.info("[matchajei] registered subtype interpreters for {}/{} base items", registered, spec.baseItems.size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> variants = new ArrayList<>(spec.variants.size());
        for (Variant variant : spec.variants) {
            Identifier baseId = Identifier.tryParse(variant.base);
            if (baseId == null) {
                LOGGER.warn("[matchajei] skipping variant with malformed base: {}", variant.base);
                continue;
            }
            var holder = BuiltInRegistries.ITEM.get(baseId);
            if (holder.isEmpty()) {
                LOGGER.warn("[matchajei] variant base not found in registry: {}", variant.base);
                continue;
            }
            ItemStack stack = new ItemStack(holder.get().value());
            if (variant.model != null) {
                Identifier modelId = Identifier.tryParse(variant.model);
                if (modelId == null) {
                    LOGGER.warn("[matchajei] skipping variant with malformed model: {}", variant.model);
                    continue;
                }
                stack.set(DataComponents.ITEM_MODEL, modelId);
            }
            if (variant.nameKey != null) {
                stack.set(DataComponents.ITEM_NAME, Component.translatable(variant.nameKey));
            }
            variants.add(stack);
        }
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, variants);
        LOGGER.info("[matchajei] added {} costumed variants as JEI ingredients", variants.size());
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
                JsonArray variantArray = root.getAsJsonArray("variants");
                for (JsonElement el : variantArray) {
                    JsonObject obj = el.getAsJsonObject();
                    variants.add(new Variant(
                            stringOrNull(obj, "base"),
                            stringOrNull(obj, "model"),
                            stringOrNull(obj, "name_key")
                    ));
                }
                return new Spec(baseItems, variants);
            }
        } catch (Exception e) {
            throw new IllegalStateException("[matchajei] failed to load reskins spec from " + SPEC_PATH, e);
        }
    }

    private static String stringOrNull(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }

    private record Spec(List<String> baseItems, List<Variant> variants) {}

    private record Variant(String base, String model, String nameKey) {}
}
