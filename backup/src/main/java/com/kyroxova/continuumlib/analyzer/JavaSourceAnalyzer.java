package com.kyroxova.continuumlib.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * AST-driven Java source analyzer using JavaParser.
 * Analyzes native base mod source code and transforms it into ContinuumLib Semantic IR (CIR).
 * Strictly avoids regex heuristics for semantic resolution.
 */
public final class JavaSourceAnalyzer {

    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    private final TargetSpec baseSpec;

    public JavaSourceAnalyzer(TargetSpec baseSpec) {
        this.baseSpec = baseSpec;
    }

    public CirCompilationUnit analyze(File sourceFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        return analyzeCompilationUnit(cu);
    }

    public CirCompilationUnit analyze(String sourceCode) {
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
        return analyzeCompilationUnit(cu);
    }

    public CirCompilationUnit analyzeCompilationUnit(CompilationUnit cu) {
        CirCompilationUnit cirCu = new CirCompilationUnit();

        // 0. Extract string constants (e.g. MOD_ID = "infinityxyzmod")
        Map<String, String> stringConstants = new HashMap<>();
        for (TypeDeclaration<?> type : cu.getTypes()) {
            for (FieldDeclaration field : type.getFields()) {
                for (VariableDeclarator var : field.getVariables()) {
                    if (var.getInitializer().isPresent() && var.getInitializer().get().isStringLiteralExpr()) {
                        stringConstants.put(var.getNameAsString(), var.getInitializer().get().asStringLiteralExpr().getValue());
                    }
                }
            }
        }

        // 1. Package declaration
        cu.getPackageDeclaration().ifPresent(pd -> cirCu.setPackageName(pd.getNameAsString()));

        // 2. Imports
        cu.getImports().forEach(im -> cirCu.addImport(im.getNameAsString()));

        // 3. Types
        for (TypeDeclaration<?> type : cu.getTypes()) {
            if (type.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration classDecl = type.asClassOrInterfaceDeclaration();
                if (cirCu.getPrimaryClassName() == null) {
                    cirCu.setPrimaryClassName(classDecl.getNameAsString());
                    if (!classDecl.getExtendedTypes().isEmpty()) {
                        cirCu.setSuperClass(classDecl.getExtendedTypes(0).asString());
                    }
                    for (var iface : classDecl.getImplementedTypes()) {
                        cirCu.addImplementedInterface(iface.asString());
                    }
                }

                // Analyze fields
                for (BodyDeclaration<?> member : classDecl.getMembers()) {
                    if (member.isFieldDeclaration()) {
                        FieldDeclaration field = member.asFieldDeclaration();
                        if (!tryAnalyzeRegistryField(field, classDecl.getNameAsString(), cirCu, stringConstants)) {
                            if (!tryAnalyzeRegisteredEntryField(field, cirCu, stringConstants)) {
                                if (!tryAnalyzeDirectCreativeTabField(field, cirCu, stringConstants)) {
                                    cirCu.addUnmanagedField(field.toString());
                                }
                            }
                        }
                    } else if (member.isMethodDeclaration()) {
                        MethodDeclaration method = member.asMethodDeclaration();
                        if (method.isAnnotationPresent("SubscribeEvent")) {
                            String methodName = method.getNameAsString();
                            boolean isStatic = method.isStatic();
                            String rawEventType = method.getParameters().isEmpty() ? "Event" : method.getParameter(0).getTypeAsString();
                            String cat = rawEventType.contains("Block") ? "BLOCK_EVENT" : (rawEventType.contains("Tick") ? "TICK_EVENT" : "GENERIC_EVENT");
                            cirCu.addEventListener(new CirEventListenerDefinition(methodName, rawEventType, cat, isStatic));
                        }
                        analyzeMethodForNetworkPackets(method, cirCu, stringConstants);
                        cirCu.addUnmanagedMethod(member.toString());
                    } else {
                        cirCu.addUnmanagedMethod(member.toString());
                    }
                }
            }
        }

        return cirCu;
    }

    private boolean tryAnalyzeRegistryField(FieldDeclaration field, String enclosingClassName, CirCompilationUnit cirCu, Map<String, String> stringConstants) {
        VariableDeclarator var = field.getVariable(0);
        String typeName = field.getElementType().asString();

        if (typeName.startsWith("DeferredRegister") || (var.getInitializer().isPresent() && var.getInitializer().get().toString().contains("DeferredRegister."))) {
            if (var.getInitializer().isPresent() && var.getInitializer().get().isMethodCallExpr()) {
                MethodCallExpr call = var.getInitializer().get().asMethodCallExpr();
                String callName = call.getNameAsString();
                if ("createBlocks".equals(callName) && !call.getArguments().isEmpty()) {
                    Expression arg0 = call.getArgument(0);
                    String rawExpr = arg0.isNameExpr() ? arg0.asNameExpr().getNameAsString() : null;
                    String modId = extractStringLiteralOrConstant(arg0, stringConstants);
                    CirRegistryDeclaration regDecl = new CirRegistryDeclaration(
                            CirRegistryType.BLOCK, modId, rawExpr, var.getNameAsString(), enclosingClassName,
                            field.hasModifier(Modifier.Keyword.STATIC), field.hasModifier(Modifier.Keyword.FINAL)
                    );
                    cirCu.addRegistry(regDecl);
                    return true;
                } else if ("createItems".equals(callName) && !call.getArguments().isEmpty()) {
                    Expression arg0 = call.getArgument(0);
                    String rawExpr = arg0.isNameExpr() ? arg0.asNameExpr().getNameAsString() : null;
                    String modId = extractStringLiteralOrConstant(arg0, stringConstants);
                    CirRegistryDeclaration regDecl = new CirRegistryDeclaration(
                            CirRegistryType.ITEM, modId, rawExpr, var.getNameAsString(), enclosingClassName,
                            field.hasModifier(Modifier.Keyword.STATIC), field.hasModifier(Modifier.Keyword.FINAL)
                    );
                    cirCu.addRegistry(regDecl);
                    return true;
                } else if ("create".equals(callName) && call.getArguments().size() >= 2) {
                    Expression arg0 = call.getArgument(0);
                    Expression arg1 = call.getArgument(1);

                    CirRegistryType regType = mapForgeRegistryType(arg0.toString());
                    String rawExpr = arg1.isNameExpr() ? arg1.asNameExpr().getNameAsString() : null;
                    String modId = extractStringLiteralOrConstant(arg1, stringConstants);

                    boolean isStatic = field.hasModifier(Modifier.Keyword.STATIC);
                    boolean isFinal = field.hasModifier(Modifier.Keyword.FINAL);

                    CirRegistryDeclaration regDecl = new CirRegistryDeclaration(
                            regType, modId, rawExpr, var.getNameAsString(), enclosingClassName, isStatic, isFinal
                    );
                    cirCu.addRegistry(regDecl);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryAnalyzeRegisteredEntryField(FieldDeclaration field, CirCompilationUnit cirCu, Map<String, String> stringConstants) {
        VariableDeclarator var = field.getVariable(0);
        String fieldName = var.getNameAsString();
        boolean isStatic = field.hasModifier(Modifier.Keyword.STATIC);
        boolean isFinal = field.hasModifier(Modifier.Keyword.FINAL);

        if (var.getInitializer().isEmpty()) {
            return false;
        }

        if (!var.getInitializer().get().isMethodCallExpr()) {
            String elemType = field.getElementType().asString();
            if (elemType.contains("RegistryObject") || elemType.contains("DeferredHolder")
                    || elemType.contains("DeferredBlock") || elemType.contains("DeferredItem")) {
                String target = var.getInitializer().get().toString();
                String innerType = "Object";
                if (field.getElementType().isClassOrInterfaceType()) {
                    var typeArgs = field.getElementType().asClassOrInterfaceType().getTypeArguments();
                    if (typeArgs.isPresent() && !typeArgs.get().isEmpty()) {
                        innerType = typeArgs.get().get(typeArgs.get().size() - 1).asString();
                    }
                }
                cirCu.addRegisteredEntry(new CirAliasDefinition(fieldName, target, innerType, "", isStatic, isFinal));
                return true;
            }
            return false;
        }

        MethodCallExpr call = var.getInitializer().get().asMethodCallExpr();
        String callName = call.getNameAsString();
        String scopeName = call.getScope().map(Object::toString).orElse("");

        // 1. NeoForge registerBlock(id, factory, props)
        if ("registerBlock".equals(callName) && call.getArguments().size() >= 3) {
            String regId = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
            Expression factoryExpr = call.getArgument(1);
            Expression propsExpr = call.getArgument(2);
            String implType = "Block";
            if (factoryExpr.isMethodReferenceExpr()) {
                implType = factoryExpr.asMethodReferenceExpr().getScope().toString();
            } else if (field.getElementType().isClassOrInterfaceType()) {
                var typeArgs = field.getElementType().asClassOrInterfaceType().getTypeArguments();
                if (typeArgs.isPresent() && !typeArgs.get().isEmpty()) {
                    implType = typeArgs.get().get(0).asString();
                }
            }
            CirBlockProperties props = new CirBlockProperties();
            extractBlockProperties(propsExpr, props);
            CirBlockDefinition blockDef = new CirBlockDefinition(regId, fieldName, scopeName, implType, props, isStatic, isFinal);
            cirCu.addRegisteredEntry(blockDef);
            return true;
        }

        // 2. NeoForge registerSimpleBlockItem(id, blockRef)
        if ("registerSimpleBlockItem".equals(callName) && call.getArguments().size() >= 2) {
            String regId = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
            String blockRef = call.getArgument(1).toString();
            CirBlockItemDefinition biDef = new CirBlockItemDefinition(regId, fieldName, scopeName, blockRef, new CirItemProperties(), isStatic, isFinal);
            cirCu.addRegisteredEntry(biDef);
            return true;
        }

        // 3. NeoForge registerSimpleItem(id, props)
        if ("registerSimpleItem".equals(callName) && call.getArguments().size() >= 2) {
            String regId = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
            Expression propsExpr = extractSuppliedExpression(call.getArgument(1));
            CirItemProperties props = new CirItemProperties();
            extractItemProperties(propsExpr, props);
            CirItemDefinition itemDef = new CirItemDefinition(regId, fieldName, scopeName, "Item", props, isStatic, isFinal);
            cirCu.addRegisteredEntry(itemDef);
            return true;
        }

        // 4. NeoForge registerItem(id, factory, props)
        if ("registerItem".equals(callName) && call.getArguments().size() >= 3) {
            String regId = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
            Expression factoryExpr = call.getArgument(1);
            Expression propsExpr = call.getArgument(2);
            String implType = "Item";
            if (factoryExpr.isMethodReferenceExpr()) {
                implType = factoryExpr.asMethodReferenceExpr().getScope().toString();
            } else if (field.getElementType().isClassOrInterfaceType()) {
                var typeArgs = field.getElementType().asClassOrInterfaceType().getTypeArguments();
                if (typeArgs.isPresent() && !typeArgs.get().isEmpty()) {
                    implType = typeArgs.get().get(0).asString();
                }
            }
            CirItemProperties props = new CirItemProperties();
            extractItemProperties(propsExpr, props);
            CirItemDefinition itemDef = new CirItemDefinition(regId, fieldName, scopeName, implType, props, isStatic, isFinal);
            cirCu.addRegisteredEntry(itemDef);
            return true;
        }

        // 5. Fabric Registry.register(Registry, id, entry)
        if ("register".equals(callName) && call.getArguments().size() >= 3 && ("Registry".equals(scopeName) || "BuiltInRegistries".equals(scopeName))) {
            Expression regArg = call.getArgument(0);
            Expression idArg = call.getArgument(1);
            Expression entryArg = call.getArgument(2);

            CirRegistryType regType = mapFabricRegistryType(regArg.toString());
            String regId = extractIdFromResourceLocation(idArg, stringConstants);
            String defaultScope = regType == CirRegistryType.BLOCK ? "BLOCKS" : (regType == CirRegistryType.ITEM ? "ITEMS" : "REGISTRIES");

            if (cirCu.findRegistry(regType).isEmpty()) {
                String modId = extractNamespaceFromResourceLocation(idArg, stringConstants);
                cirCu.addRegistry(new CirRegistryDeclaration(regType, modId != null ? modId : "modid", null, defaultScope, "ModRegistries", true, true));
            }

            if (regType == CirRegistryType.BLOCK) {
                return analyzeBlockEntry(fieldName, defaultScope, regId, entryArg, isStatic, isFinal, cirCu);
            } else if (regType == CirRegistryType.ITEM) {
                return analyzeItemEntry(fieldName, defaultScope, regId, entryArg, isStatic, isFinal, cirCu);
            }
        }

        // 6. Standard Forge / NeoForge register(id, supplier)
        if ((!"register".equals(callName) && !"registerBlock".equals(callName)) || call.getArguments().size() < 2) {
            if ((field.getElementType().asString().contains("RegistryObject") || field.getElementType().asString().contains("DeferredHolder")) && !call.getArguments().isEmpty()) {
                String innerType = "Object";
                if (field.getElementType().isClassOrInterfaceType()) {
                    var typeArgs = field.getElementType().asClassOrInterfaceType().getTypeArguments();
                    if (typeArgs.isPresent() && !typeArgs.get().isEmpty()) {
                        innerType = typeArgs.get().get(0).asString();
                    }
                }
                if (innerType.contains("SoundEvent") || call.getNameAsString().toLowerCase().contains("sound")) {
                    String soundName = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
                    String soundId = soundName.replace('.', '_');
                    String regField = cirCu.findRegistry(CirRegistryType.SOUND_EVENT)
                            .map(CirRegistryDeclaration::getFieldName)
                            .orElse("SOUND_EVENTS");
                    cirCu.addRegisteredEntry(new CirSoundDefinition(soundId, fieldName, regField, soundId, isStatic, isFinal));
                    return true;
                }
            }
            return false;
        }

        if (scopeName.isEmpty()) {
            return false;
        }
        Optional<CirRegistryDeclaration> regOpt = cirCu.findRegistryByField(scopeName);
        if (regOpt.isEmpty()) {
            return false;
        }
        CirRegistryDeclaration reg = regOpt.get();

        String regId = extractStringLiteralOrConstant(call.getArgument(0), stringConstants);
        Expression supplierExpr = call.getArgument(1);

        // Resolve lambda / supplier expression: () -> new Block(...)
        Expression creationExpr = extractSuppliedExpression(supplierExpr);
        if (creationExpr == null) {
            return false;
        }

        if (reg.getRegistryType() == CirRegistryType.BLOCK) {
            return analyzeBlockEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.ITEM) {
            return analyzeItemEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.BLOCK_ENTITY_TYPE) {
            return analyzeBlockEntityEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.ENTITY_TYPE) {
            return analyzeEntityEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.SOUND_EVENT) {
            return analyzeSoundEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.PARTICLE_TYPE) {
            return analyzeParticleEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.MENU) {
            return analyzeMenuEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.FLUID) {
            return analyzeFluidEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.RECIPE_SERIALIZER) {
            return analyzeRecipeSerializerEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        } else if (reg.getRegistryType() == CirRegistryType.CREATIVE_MODE_TAB) {
            return analyzeCreativeTabEntry(fieldName, scopeName, regId, creationExpr, isStatic, isFinal, cirCu);
        }

        return false;
    }

    private boolean analyzeBlockEntry(String fieldName, String registryFieldName, String regId,
                                      Expression creationExpr, boolean isStatic, boolean isFinal,
                                      CirCompilationUnit cirCu) {
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr newExpr = creationExpr.asObjectCreationExpr();
            String blockClassName = newExpr.getTypeAsString();

            CirBlockProperties properties = new CirBlockProperties();
            for (Expression arg : newExpr.getArguments()) {
                if (isPropertiesExpression(arg)) {
                    extractBlockProperties(arg, properties);
                }
            }

            CirBlockDefinition blockDef = new CirBlockDefinition(
                    regId, fieldName, registryFieldName, blockClassName, properties, isStatic, isFinal
            );
            cirCu.addRegisteredEntry(blockDef);
            return true;
        }
        return false;
    }

    private boolean analyzeItemEntry(String fieldName, String registryFieldName, String regId,
                                     Expression creationExpr, boolean isStatic, boolean isFinal,
                                     CirCompilationUnit cirCu) {
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr newExpr = creationExpr.asObjectCreationExpr();
            String itemClassName = newExpr.getTypeAsString();

            if ("BlockItem".equals(itemClassName) || itemClassName.endsWith(".BlockItem")) {
                // BlockItem registration: new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().tab(...))
                String blockRef = "UNKNOWN";
                CirItemProperties properties = new CirItemProperties();

                if (newExpr.getArguments().size() >= 1) {
                    Expression arg0 = newExpr.getArgument(0);
                    blockRef = extractBlockReferenceName(arg0);
                }
                if (newExpr.getArguments().size() >= 2) {
                    extractItemProperties(newExpr.getArgument(1), properties);
                }

                CirBlockItemDefinition blockItemDef = new CirBlockItemDefinition(
                        regId, fieldName, registryFieldName, blockRef, properties, isStatic, isFinal
                );
                cirCu.addRegisteredEntry(blockItemDef);
                return true;
            } else {
                // Normal Item or Custom Item
                CirItemProperties properties = new CirItemProperties();
                for (Expression arg : newExpr.getArguments()) {
                    if (isItemPropertiesExpression(arg)) {
                        extractItemProperties(arg, properties);
                    }
                }

                CirItemDefinition itemDef = new CirItemDefinition(
                        regId, fieldName, registryFieldName, itemClassName, properties, isStatic, isFinal
                );
                cirCu.addRegisteredEntry(itemDef);
                return true;
            }
        }
        return false;
    }

    private boolean analyzeBlockEntityEntry(String fieldName, String registryFieldName, String regId,
                                            Expression creationExpr, boolean isStatic, boolean isFinal,
                                            CirCompilationUnit cirCu) {
        // e.g., BlockEntityType.Builder.of(ExampleBlockEntity::new, EXAMPLE_BLOCK.get()).build(null)
        String entityClass = "BlockEntity";
        List<String> validBlocks = new ArrayList<>();

        if (creationExpr.isMethodCallExpr()) {
            MethodCallExpr call = creationExpr.asMethodCallExpr();
            if ("build".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                Expression builderExpr = call.getScope().get();
                if (builderExpr.isMethodCallExpr()) {
                    MethodCallExpr ofCall = builderExpr.asMethodCallExpr();
                    if ("of".equals(ofCall.getNameAsString()) && !ofCall.getArguments().isEmpty()) {
                        Expression factoryArg = ofCall.getArgument(0);
                        if (factoryArg.isMethodReferenceExpr()) {
                            entityClass = factoryArg.asMethodReferenceExpr().getScope().toString();
                        }

                        for (int i = 1; i < ofCall.getArguments().size(); i++) {
                            validBlocks.add(extractBlockReferenceName(ofCall.getArgument(i)));
                        }
                    }
                }
            }
        }

        CirBlockEntityDefinition beDef = new CirBlockEntityDefinition(
                regId, fieldName, registryFieldName, entityClass, validBlocks, isStatic, isFinal
        );
        cirCu.addRegisteredEntry(beDef);
        return true;
    }

    private Expression extractSuppliedExpression(Expression supplierExpr) {
        if (supplierExpr.isLambdaExpr()) {
            LambdaExpr lambda = supplierExpr.asLambdaExpr();
            if (lambda.getBody().isExpressionStmt()) {
                return lambda.getBody().asExpressionStmt().getExpression();
            } else if (lambda.getBody().isBlockStmt()) {
                // Search for return statement
                var returnStmts = lambda.getBody().asBlockStmt().findAll(com.github.javaparser.ast.stmt.ReturnStmt.class);
                if (!returnStmts.isEmpty() && returnStmts.get(0).getExpression().isPresent()) {
                    return returnStmts.get(0).getExpression().get();
                }
            }
        }
        return supplierExpr;
    }

    private boolean isPropertiesExpression(Expression expr) {
        String s = expr.toString();
        return s.contains("Properties.of") || s.contains("BlockBehaviour.Properties") || s.contains("Block.Properties");
    }

    private boolean isItemPropertiesExpression(Expression expr) {
        String s = expr.toString();
        return s.contains("Item.Properties") || s.contains("new Item.Properties");
    }

    private void extractBlockProperties(Expression expr, CirBlockProperties props) {
        if (expr.isMethodCallExpr()) {
            MethodCallExpr call = expr.asMethodCallExpr();
            String name = call.getNameAsString();

            if ("of".equals(name)) {
                if (!call.getArguments().isEmpty()) {
                    String mat = call.getArgument(0).toString();
                    if (mat.contains("Material.")) {
                        mat = mat.substring(mat.indexOf("Material.") + 9);
                    }
                    props.setMaterial(mat);
                }
            } else if ("strength".equals(name)) {
                if (call.getArguments().size() == 1) {
                    try {
                        float val = Float.parseFloat(stripFloatSuffix(call.getArgument(0).toString()));
                        props.setStrength(val);
                    } catch (Exception ignored) {
                        props.addChainedCall(call.toString());
                    }
                } else if (call.getArguments().size() >= 2) {
                    try {
                        float d = Float.parseFloat(stripFloatSuffix(call.getArgument(0).toString()));
                        float r = Float.parseFloat(stripFloatSuffix(call.getArgument(1).toString()));
                        props.setStrength(d, r);
                    } catch (Exception ignored) {
                        props.addChainedCall(call.toString());
                    }
                }
            } else if ("requiresCorrectToolForDrops".equals(name)) {
                props.setRequiresCorrectToolForDrops(true);
            } else if ("noOcclusion".equals(name)) {
                props.setNoOcclusion(true);
            } else if ("sound".equals(name) && !call.getArguments().isEmpty()) {
                String snd = call.getArgument(0).toString();
                if (snd.contains("SoundType.")) {
                    snd = snd.substring(snd.indexOf("SoundType.") + 10);
                }
                props.setSoundType(snd);
            } else {
                props.addChainedCall(call.getNameAsString() + "(" + formatArgs(call.getArguments()) + ")");
            }

            // Recurse down the call chain
            if (call.getScope().isPresent()) {
                extractBlockProperties(call.getScope().get(), props);
            }
        }
    }

    private void extractItemProperties(Expression expr, CirItemProperties props) {
        if (expr.isMethodCallExpr()) {
            MethodCallExpr call = expr.asMethodCallExpr();
            String name = call.getNameAsString();

            if ("tab".equals(name) && !call.getArguments().isEmpty()) {
                props.setCreativeTab(call.getArgument(0).toString());
            } else if ("stacksTo".equals(name) && !call.getArguments().isEmpty()) {
                try {
                    props.setMaxStackSize(Integer.parseInt(call.getArgument(0).toString()));
                } catch (Exception ignored) {
                }
            } else if ("durability".equals(name) && !call.getArguments().isEmpty()) {
                try {
                    props.setMaxDamage(Integer.parseInt(call.getArgument(0).toString()));
                } catch (Exception ignored) {
                }
            } else {
                props.addChainedCall(call.getNameAsString() + "(" + formatArgs(call.getArguments()) + ")");
            }

            if (call.getScope().isPresent()) {
                extractItemProperties(call.getScope().get(), props);
            }
        }
    }

    private String extractBlockReferenceName(Expression expr) {
        String s = expr.toString();
        if (s.endsWith(".get()")) {
            return s.substring(0, s.length() - 6);
        }
        return s;
    }

    private String extractStringLiteralOrConstant(Expression expr, Map<String, String> constants) {
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().getValue();
        }
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            if (constants != null && constants.containsKey(name)) {
                return constants.get(name);
            }
            return name;
        }
        return expr.toString();
    }

    private String stripFloatSuffix(String s) {
        s = s.trim();
        if (s.endsWith("f") || s.endsWith("F")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String formatArgs(NodeList<Expression> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(args.get(i).toString());
        }
        return sb.toString();
    }

    private boolean analyzeEntityEntry(String fieldName, String registryFieldName, String regId,
                                       Expression creationExpr, boolean isStatic, boolean isFinal,
                                       CirCompilationUnit cirCu) {
        String entityClass = "Entity";
        String category = "MISC";
        Float w = null, h = null;
        Integer tracking = null, interval = null;

        if (creationExpr.isMethodCallExpr()) {
            MethodCallExpr curr = creationExpr.asMethodCallExpr();
            while (curr != null) {
                String mName = curr.getNameAsString();
                if ("of".equals(mName) && !curr.getArguments().isEmpty()) {
                    Expression arg0 = curr.getArgument(0);
                    if (arg0.isMethodReferenceExpr()) {
                        entityClass = arg0.asMethodReferenceExpr().getScope().toString();
                    }
                    if (curr.getArguments().size() > 1) {
                        String catStr = curr.getArgument(1).toString();
                        if (catStr.contains("MobCategory.")) {
                            category = catStr.substring(catStr.indexOf("MobCategory.") + 12);
                        }
                    }
                } else if ("sized".equals(mName) && curr.getArguments().size() >= 2) {
                    try {
                        w = Float.parseFloat(stripFloatSuffix(curr.getArgument(0).toString()));
                        h = Float.parseFloat(stripFloatSuffix(curr.getArgument(1).toString()));
                    } catch (Exception ignored) {}
                } else if ("clientTrackingRange".equals(mName) && !curr.getArguments().isEmpty()) {
                    try { tracking = Integer.parseInt(curr.getArgument(0).toString()); } catch (Exception ignored) {}
                } else if ("updateInterval".equals(mName) && !curr.getArguments().isEmpty()) {
                    try { interval = Integer.parseInt(curr.getArgument(0).toString()); } catch (Exception ignored) {}
                }

                if (curr.getScope().isPresent() && curr.getScope().get().isMethodCallExpr()) {
                    curr = curr.getScope().get().asMethodCallExpr();
                } else {
                    curr = null;
                }
            }
        }

        CirEntityDefinition def = new CirEntityDefinition(
                regId, fieldName, registryFieldName, entityClass, category, w, h, tracking, interval, isStatic, isFinal
        );
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeSoundEntry(String fieldName, String registryFieldName, String regId,
                                      Expression creationExpr, boolean isStatic, boolean isFinal,
                                      CirCompilationUnit cirCu) {
        String soundPath = regId;
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr obj = creationExpr.asObjectCreationExpr();
            if (!obj.getArguments().isEmpty()) {
                soundPath = obj.getArgument(0).toString();
            }
        }
        CirSoundDefinition def = new CirSoundDefinition(regId, fieldName, registryFieldName, soundPath, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeParticleEntry(String fieldName, String registryFieldName, String regId,
                                         Expression creationExpr, boolean isStatic, boolean isFinal,
                                         CirCompilationUnit cirCu) {
        String pClass = "SimpleParticleType";
        boolean alwaysShow = false;
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr obj = creationExpr.asObjectCreationExpr();
            pClass = obj.getTypeAsString();
            if (!obj.getArguments().isEmpty()) {
                alwaysShow = Boolean.parseBoolean(obj.getArgument(0).toString());
            }
        }
        CirParticleDefinition def = new CirParticleDefinition(regId, fieldName, registryFieldName, pClass, alwaysShow, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeMenuEntry(String fieldName, String registryFieldName, String regId,
                                     Expression creationExpr, boolean isStatic, boolean isFinal,
                                     CirCompilationUnit cirCu) {
        String menuClass = "AbstractContainerMenu";
        if (creationExpr.isMethodCallExpr()) {
            MethodCallExpr call = creationExpr.asMethodCallExpr();
            if (!call.getArguments().isEmpty() && call.getArgument(0).isMethodReferenceExpr()) {
                menuClass = call.getArgument(0).asMethodReferenceExpr().getScope().toString();
            }
        }
        CirMenuDefinition def = new CirMenuDefinition(regId, fieldName, registryFieldName, menuClass, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeFluidEntry(String fieldName, String registryFieldName, String regId,
                                      Expression creationExpr, boolean isStatic, boolean isFinal,
                                      CirCompilationUnit cirCu) {
        String fClass = "ForgeFlowingFluid";
        boolean isSource = true;
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr obj = creationExpr.asObjectCreationExpr();
            fClass = obj.getTypeAsString();
            isSource = fClass.contains("Source");
        }
        CirFluidDefinition def = new CirFluidDefinition(regId, fieldName, registryFieldName, fClass, isSource, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeRecipeSerializerEntry(String fieldName, String registryFieldName, String regId,
                                                 Expression creationExpr, boolean isStatic, boolean isFinal,
                                                 CirCompilationUnit cirCu) {
        String rClass = "Recipe";
        boolean isSimple = false;
        String serializerRef = creationExpr.toString();
        if (creationExpr.isObjectCreationExpr()) {
            ObjectCreationExpr obj = creationExpr.asObjectCreationExpr();
            if (obj.getTypeAsString().contains("SimpleRecipeSerializer")) {
                isSimple = true;
                if (!obj.getArguments().isEmpty() && obj.getArgument(0).isMethodReferenceExpr()) {
                    rClass = obj.getArgument(0).asMethodReferenceExpr().getScope().toString();
                }
            }
        } else if (creationExpr.isFieldAccessExpr()) {
            serializerRef = creationExpr.asFieldAccessExpr().toString();
            rClass = creationExpr.asFieldAccessExpr().getScope().toString();
        }
        CirRecipeSerializerDefinition def = new CirRecipeSerializerDefinition(regId, fieldName, registryFieldName, rClass, isSimple, serializerRef, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean analyzeCreativeTabEntry(String fieldName, String registryFieldName, String regId,
                                            Expression creationExpr, boolean isStatic, boolean isFinal,
                                            CirCompilationUnit cirCu) {
        String iconExpr = "net.minecraft.world.item.Items.AIR";
        String titleKey = "itemGroup." + regId;
        if (creationExpr.isMethodCallExpr()) {
            MethodCallExpr call = creationExpr.asMethodCallExpr();
            Expression curr = call;
            while (curr != null && curr.isMethodCallExpr()) {
                MethodCallExpr m = curr.asMethodCallExpr();
                if ("icon".equals(m.getNameAsString()) && !m.getArguments().isEmpty()) {
                    iconExpr = m.getArgument(0).toString();
                } else if ("title".equals(m.getNameAsString()) && !m.getArguments().isEmpty()) {
                    titleKey = m.getArgument(0).toString().replace("\"", "");
                }
                curr = m.getScope().orElse(null);
            }
        }
        CirCreativeTabDefinition def = new CirCreativeTabDefinition(regId, fieldName, registryFieldName, iconExpr, titleKey, isStatic, isFinal);
        cirCu.addRegisteredEntry(def);
        return true;
    }

    private boolean tryAnalyzeDirectCreativeTabField(FieldDeclaration field, CirCompilationUnit cirCu, Map<String, String> stringConstants) {
        if (!field.getElementType().asString().contains("CreativeModeTab")) {
            return false;
        }
        VariableDeclarator var = field.getVariable(0);
        String fieldName = var.getNameAsString();
        boolean isStatic = field.hasModifier(Modifier.Keyword.STATIC);
        boolean isFinal = field.hasModifier(Modifier.Keyword.FINAL);

        if (var.getInitializer().isEmpty()) {
            return false;
        }

        Expression initExpr = var.getInitializer().get();
        if (initExpr.isObjectCreationExpr()) {
            ObjectCreationExpr newExpr = initExpr.asObjectCreationExpr();
            String regId = "main";
            if (!newExpr.getArguments().isEmpty()) {
                regId = extractStringLiteralOrConstant(newExpr.getArgument(0), stringConstants);
            }
            String iconItem = "net.minecraft.world.item.Items.AIR";
            if (newExpr.getAnonymousClassBody().isPresent()) {
                for (BodyDeclaration<?> bodyMember : newExpr.getAnonymousClassBody().get()) {
                    if (bodyMember.isMethodDeclaration()) {
                        MethodDeclaration m = bodyMember.asMethodDeclaration();
                        if ("makeIcon".equals(m.getNameAsString())) {
                            for (ReturnStmt ret : m.findAll(ReturnStmt.class)) {
                                if (ret.getExpression().isPresent()) {
                                    Expression retExpr = ret.getExpression().get();
                                    if (retExpr.isObjectCreationExpr()) {
                                        ObjectCreationExpr itemStackExpr = retExpr.asObjectCreationExpr();
                                        if (!itemStackExpr.getArguments().isEmpty()) {
                                            String argStr = itemStackExpr.getArgument(0).toString();
                                            if (argStr.endsWith(".get()")) {
                                                iconItem = argStr.substring(0, argStr.length() - 6);
                                            } else {
                                                iconItem = argStr;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (cirCu.findRegistry(CirRegistryType.CREATIVE_MODE_TAB).isEmpty()) {
                String modId = cirCu.getRegistries().isEmpty() ? regId : cirCu.getRegistries().get(0).getModId();
                String rawModId = cirCu.getRegistries().isEmpty() ? null : cirCu.getRegistries().get(0).getRawModIdExpression();
                CirRegistryDeclaration autoReg = new CirRegistryDeclaration(
                        CirRegistryType.CREATIVE_MODE_TAB, modId, rawModId, "CREATIVE_MODE_TABS",
                        cirCu.getPrimaryClassName(), true, true
                );
                cirCu.addRegistry(autoReg);
            }
            String regField = cirCu.findRegistry(CirRegistryType.CREATIVE_MODE_TAB)
                    .map(CirRegistryDeclaration::getFieldName)
                    .orElse("CREATIVE_MODE_TABS");
            CirCreativeTabDefinition tabDef = new CirCreativeTabDefinition(regId, fieldName, regField, iconItem, "itemGroup." + regId, isStatic, isFinal);
            cirCu.addRegisteredEntry(tabDef);
            return true;
        }
        return false;
    }

    private void analyzeMethodForNetworkPackets(MethodDeclaration method, CirCompilationUnit cirCu, Map<String, String> stringConstants) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if ("register".equals(call.getNameAsString()) && call.getArguments().size() >= 3) {
                Expression arg0 = call.getArgument(0);
                if (arg0.isClassExpr()) {
                    String packetClass = arg0.asClassExpr().getTypeAsString();
                    int id = 0;
                    if (call.getArgument(1).isIntegerLiteralExpr()) {
                        id = call.getArgument(1).asIntegerLiteralExpr().asInt();
                    }
                    String dir = call.getArgument(2).toString();
                    String encoder = call.getArguments().size() >= 4 ? call.getArgument(3).toString() : null;
                    String decoder = call.getArguments().size() >= 5 ? call.getArgument(4).toString() : null;
                    String handler = call.getArguments().size() >= 6 ? call.getArgument(5).toString() : null;
                    cirCu.addNetworkPacket(new CirNetworkPacketDefinition("main", packetClass, id, dir, encoder, decoder, handler));
                }
            }
        });
    }

    private CirRegistryType mapForgeRegistryType(String exprStr) {
        if (exprStr.contains("BLOCKS")) return CirRegistryType.BLOCK;
        if (exprStr.contains("ITEMS")) return CirRegistryType.ITEM;
        if (exprStr.contains("BLOCK_ENTITIES") || exprStr.contains("BLOCK_ENTITY_TYPES")) return CirRegistryType.BLOCK_ENTITY_TYPE;
        if (exprStr.contains("ENTITIES") || exprStr.contains("ENTITY_TYPES")) return CirRegistryType.ENTITY_TYPE;
        if (exprStr.contains("FLUIDS")) return CirRegistryType.FLUID;
        if (exprStr.contains("CONTAINERS") || exprStr.contains("MENUS") || exprStr.contains("MENU_TYPES")) return CirRegistryType.MENU;
        if (exprStr.contains("SOUND_EVENTS") || exprStr.contains("SOUNDS")) return CirRegistryType.SOUND_EVENT;
        if (exprStr.contains("PARTICLE_TYPES") || exprStr.contains("PARTICLES")) return CirRegistryType.PARTICLE_TYPE;
        if (exprStr.contains("RECIPE_SERIALIZERS")) return CirRegistryType.RECIPE_SERIALIZER;
        if (exprStr.contains("CREATIVE_MODE_TAB") || exprStr.contains("CREATIVE_MODE_TABS") || exprStr.contains("CREATIVE_TABS")) return CirRegistryType.CREATIVE_MODE_TAB;
        return CirRegistryType.BLOCK;
    }

    private String extractIdFromResourceLocation(Expression idArg, Map<String, String> stringConstants) {
        if (idArg.isObjectCreationExpr()) {
            var args = idArg.asObjectCreationExpr().getArguments();
            if (args.size() >= 2) {
                return extractStringLiteralOrConstant(args.get(1), stringConstants);
            }
        } else if (idArg.isMethodCallExpr()) {
            var args = idArg.asMethodCallExpr().getArguments();
            if (args.size() >= 2) {
                return extractStringLiteralOrConstant(args.get(1), stringConstants);
            }
        }
        return "unknown";
    }

    private String extractNamespaceFromResourceLocation(Expression idArg, Map<String, String> stringConstants) {
        if (idArg.isObjectCreationExpr()) {
            var args = idArg.asObjectCreationExpr().getArguments();
            if (args.size() >= 1) {
                return extractStringLiteralOrConstant(args.get(0), stringConstants);
            }
        } else if (idArg.isMethodCallExpr()) {
            var args = idArg.asMethodCallExpr().getArguments();
            if (args.size() >= 1) {
                return extractStringLiteralOrConstant(args.get(0), stringConstants);
            }
        }
        return null;
    }

    private CirRegistryType mapFabricRegistryType(String expr) {
        if (expr.contains("BLOCK_ENTITY")) return CirRegistryType.BLOCK_ENTITY_TYPE;
        if (expr.contains("BLOCK")) return CirRegistryType.BLOCK;
        if (expr.contains("ITEM")) return CirRegistryType.ITEM;
        if (expr.contains("ENTITY")) return CirRegistryType.ENTITY_TYPE;
        if (expr.contains("SOUND")) return CirRegistryType.SOUND_EVENT;
        if (expr.contains("PARTICLE")) return CirRegistryType.PARTICLE_TYPE;
        if (expr.contains("MENU")) return CirRegistryType.MENU;
        if (expr.contains("FLUID")) return CirRegistryType.FLUID;
        if (expr.contains("RECIPE")) return CirRegistryType.RECIPE_SERIALIZER;
        if (expr.contains("TAB")) return CirRegistryType.CREATIVE_MODE_TAB;
        return CirRegistryType.BLOCK;
    }
}
