package com.kyroxova.continuumlib.knowledge.rule;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class RulePackReaderTest {
    private String xml(String rules) {
        String digest = "0".repeat(64);
        return "<rules schema='1' id='fixture' evidence='Synthetic parser fixture'>"
                + "<source minecraft='1.18.2' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='game' sha256='" + digest + "'/></source>"
                + "<target minecraft='1.20.1' loader='FORGE' namespace='MOJMAP' java='17'><artifact name='game' sha256='" + digest + "'/></target>"
                + rules + "</rules>";
    }
    private RulePack read(String text) throws IOException {
        return new RulePackReader().read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void readsExactClassMemberAndBridgeRules() throws Exception {
        var pack = read(xml("<class from='old/Block' to='new/Block'/>"
                + "<member from-owner='old/Block' from-name='a' from-descriptor='()V' to-owner='new/Block' to-name='b' to-descriptor='()V'/>"
                + "<bridge opcode='INVOKEVIRTUAL' from-owner='old/Block' from-name='c' from-descriptor='(I)I' to-owner='hooks/Block' to-name='c' to-descriptor='(Lold/Block;I)I'/>"));
        assertEquals("new/Block", pack.classes().get("old/Block"));
        assertEquals("b", pack.members().values().iterator().next().name());
        assertEquals(1, pack.bridges().size());
        assertEquals("1.18.2", pack.source().minecraftVersion());
        new RuleCatalog(java.util.List.of(pack)).select(pack.source(), pack.target());
    }
    @Test void rejectsTyposDuplicateRulesAndUnsupportedSchema() {
        assertThrows(IOException.class, () -> read(xml("<clas from='a' to='b'/>")));
        assertThrows(IOException.class, () -> read(xml("<class from='a' to='b' extra='ignored'/>")));
        assertThrows(IOException.class, () -> read(xml("<class from='a' to='b'/><class from='a' to='c'/>")));
        assertThrows(IOException.class, () -> read(xml("").replace("schema='1'", "schema='2'")));
        assertThrows(IOException.class, () -> read(xml("<class from='a' to='b'><unexpected/></class>")));
    }
    @Test void refusesDocumentTypesAndExternalEntities() {
        assertThrows(IOException.class, () -> read("<!DOCTYPE rules [<!ENTITY external SYSTEM 'file:///not-read'>]>" + xml("&external;")));
    }
    @Test void readsConstructorFactoriesAndRejectsDuplicateAllocationRules() throws Exception {
        String factory = "<constructor-factory from-owner='api/Id' from-name='&lt;init&gt;' from-descriptor='(Ljava/lang/String;)V' "
                + "to-owner='api/Id' to-name='parse' to-descriptor='(Ljava/lang/String;)Lapi/Id;'/ >".replace("/ >", "/>");
        var pack = read(xml(factory));
        assertEquals("parse", pack.constructors().get(0).factory().name());
        assertThrows(IOException.class, () -> read(xml(factory + factory)));
    }
}
