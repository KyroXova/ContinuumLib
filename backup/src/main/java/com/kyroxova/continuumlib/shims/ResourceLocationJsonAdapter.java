package com.kyroxova.continuumlib.shims;

import com.google.gson.*;
import java.lang.reflect.Type;

/**
 * Universal JSON serializer and deserializer for ResourceLocation across all versions.
 * Handles singular and plural datapack path variations automatically.
 */
public final class ResourceLocationJsonAdapter implements JsonSerializer<Object>, JsonDeserializer<Object> {

    @Override
    public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) return JsonNull.INSTANCE;
        return new JsonPrimitive(src.toString());
    }

    @Override
    public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) return null;
        String raw = json.getAsString();
        return ResourceLocationShim.parse(raw);
    }
}
