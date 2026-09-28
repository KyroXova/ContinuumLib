package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;

/**
 * Universal Rules for Datapack and Resource Locations path plural/singular transformations.
 */
public final class DataPackPathRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        // Datapack directory transformations are handled via DataPackResourcePathNormalizer
        // during JAR generation and ResourceLocationShim during runtime lookups.
    }
}
