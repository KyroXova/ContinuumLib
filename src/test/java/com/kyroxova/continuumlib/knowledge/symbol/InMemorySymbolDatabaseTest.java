package com.kyroxova.continuumlib.knowledge.symbol;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;
import com.kyroxova.continuumlib.model.symbol.SymbolKey;
import com.kyroxova.continuumlib.model.symbol.SymbolKind;
import com.kyroxova.continuumlib.model.symbol.SymbolName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySymbolDatabaseTest {
    private static final EnvironmentId FORGE_1182 =
            new EnvironmentId("1.18.2", Loader.FORGE, MappingNamespace.MOJMAP, 17);

    @Test
    void resolvesAliasesWithoutCollapsingOverloads() {
        SymbolKey useHand = key("use", "(LBlockState;LLevel;LBlockPos;LPlayer;LInteractionHand;LHitResult;)LInteractionResult;");
        SymbolKey useLegacyName = new SymbolKey(FORGE_1182, "net/minecraft/world/level/block/Block",
                SymbolKind.METHOD, "m_6227_", useHand.descriptor(), MappingNamespace.SRG);
        SymbolKey useNoHand = key("use", "(LBlockState;LLevel;LBlockPos;LPlayer;LHitResult;)LInteractionResult;");
        var hand = new SymbolName("minecraft.block.use.with_hand", List.of(useHand, useLegacyName));
        var noHand = new SymbolName("minecraft.block.use.without_hand", List.of(useNoHand));
        var database = new InMemorySymbolDatabase(List.of(hand, noHand));

        assertEquals(hand, database.find(useHand).orElseThrow());
        assertEquals(hand, database.find(useLegacyName).orElseThrow());
        assertEquals(noHand, database.find(useNoHand).orElseThrow());
        assertNotEquals(database.find(useHand), database.find(useNoHand));
        assertTrue(database.find(key("missing", "()V")).isEmpty());
    }

    @Test
    void storesConstructorsAndFieldsAsFirstClassSymbols() {
        SymbolKey constructor = new SymbolKey(FORGE_1182, "example/Block", SymbolKind.CONSTRUCTOR,
                "<init>", "(LProperties;)V", MappingNamespace.MOJMAP);
        SymbolKey field = new SymbolKey(FORGE_1182, "example/Blocks", SymbolKind.FIELD,
                "STONE", "LBlock;", MappingNamespace.MOJMAP);
        var database = new InMemorySymbolDatabase(List.of(
                new SymbolName("example.block.constructor", List.of(constructor)),
                new SymbolName("example.blocks.stone", List.of(field))));

        assertTrue(database.find(constructor).isPresent());
        assertTrue(database.find(field).isPresent());
    }

    private SymbolKey key(String name, String descriptor) {
        return new SymbolKey(FORGE_1182, "net/minecraft/world/level/block/Block",
                SymbolKind.METHOD, name, descriptor, MappingNamespace.MOJMAP);
    }
}
