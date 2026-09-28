package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance ASM bytecode transformer.
 * Rewrites class structures, method invocations, field accesses, and descriptor signatures
 * based on active Knowledge Base delta rules between baseSpec and targetSpec.
 */
public final class ContinuumBytecodeTransformer {

    private static final Logger LOGGER = Logger.getLogger(ContinuumBytecodeTransformer.class.getName());

    private final TargetSpec baseSpec;
    private final TargetSpec targetSpec;
    private final Map<String, String> classRedirects = new HashMap<>();
    private final List<MethodRedirectRule> methodRedirects = new ArrayList<>();
    private final List<FieldRedirectRule> fieldRedirects = new ArrayList<>();
    private final List<PolyfillRule> polyfillRules = new ArrayList<>();

    public ContinuumBytecodeTransformer(ApiKnowledgeBase knowledgeBase, TargetSpec baseSpec, TargetSpec targetSpec) {
        this.baseSpec = baseSpec;
        this.targetSpec = targetSpec;
        List<TransformationRule> activeRules = knowledgeBase.getApplicableRules(baseSpec, targetSpec);
        for (TransformationRule rule : activeRules) {
            if (rule instanceof ClassRedirectRule cr) {
                classRedirects.put(cr.getSourceInternalName(), cr.getTargetInternalName());
            } else if (rule instanceof MethodRedirectRule mr) {
                methodRedirects.add(mr);
            } else if (rule instanceof FieldRedirectRule fr) {
                fieldRedirects.add(fr);
            } else if (rule instanceof PolyfillRule pr) {
                polyfillRules.add(pr);
            }
        }
    }

    /**
     * Transforms raw class bytecode.
     */
    public byte[] transform(String className, byte[] basicClass) {
        if (basicClass == null || basicClass.length == 0) {
            return basicClass;
        }

        // Don't transform ContinuumLib internal classes
        String normalized = className.replace('.', '/');
        if (normalized.startsWith("com/kyroxova/continuumlib/") || normalized.startsWith("com/kyroxova/bootstrapper/")) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);

            boolean modified = applyTransformations(classNode);

            if (!modified) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[ContinuumBytecodeTransformer] Error transforming class " + className, t);
            return basicClass;
        }
    }

    private boolean applyTransformations(ClassNode classNode) {
        boolean modified = false;

        // 1. Super class & interfaces
        if (classNode.superName != null && classRedirects.containsKey(classNode.superName)) {
            classNode.superName = classRedirects.get(classNode.superName);
            modified = true;
        }

        if (classNode.interfaces != null) {
            for (int i = 0; i < classNode.interfaces.size(); i++) {
                String iface = classNode.interfaces.get(i);
                if (classRedirects.containsKey(iface)) {
                    classNode.interfaces.set(i, classRedirects.get(iface));
                    modified = true;
                }
            }
        }

        // 2. Fields
        if (classNode.fields != null) {
            for (FieldNode field : classNode.fields) {
                String remappedDesc = remapDescriptor(field.desc);
                if (!remappedDesc.equals(field.desc)) {
                    field.desc = remappedDesc;
                    modified = true;
                }
            }
        }

        // 3. Methods & instructions
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                String remappedDesc = remapDescriptor(method.desc);
                if (!remappedDesc.equals(method.desc)) {
                    method.desc = remappedDesc;
                    modified = true;
                }

                if (transformInstructions(method, method.instructions, classNode)) {
                    modified = true;
                }
            }
        }

        // 4. Modern synthetic bridges for Block and BlockEntity (>= 1.20.5)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.5"))) {
            if (injectModernBlockAndBlockEntityBridges(classNode)) {
                modified = true;
            }
        }

        // 5. Modern Entity.level field -> method getter (>= 1.20)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"))) {
            if (transformModernEntityLevelAccess(classNode)) {
                modified = true;
            }
        }

        // 6. Modern AttributeModifier constructor rewrite (>= 1.20.5)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.5"))) {
            if (transformAttributeModifierConstructors(classNode)) {
                modified = true;
            }
        }

        // 7. Modern synthetic bridges for Recipe (>= 1.20.5)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.5"))) {
            if (injectModernRecipeBridges(classNode)) {
                modified = true;
            }
        }

        // 8. Modern RandomSource tick bridges (>= 1.19)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.19"))) {
            if (injectModernRandomTickBridges(classNode)) {
                modified = true;
            }
        }

        // 9. Modern Screen bridges (>= 1.20)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"))) {
            if (injectModernScreenBridges(classNode)) {
                modified = true;
            }
        }

        // 10. Modern LootParams bridges (>= 1.20)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"))) {
            if (injectModernLootBridges(classNode)) {
                modified = true;
            }
        }

        return modified;
    }

    private boolean transformInstructions(InsnList instructions) {
        return transformInstructions(null, instructions, null);
    }

    private boolean transformInstructions(MethodNode method, InsnList instructions, ClassNode classNode) {
        if (instructions == null) return false;
        boolean modified = false;

        for (AbstractInsnNode insn : instructions.toArray()) {
            // Check Method Invocations
            if (insn instanceof MethodInsnNode minsn) {
                // A. Check Polyfill Rules first
                for (int i = polyfillRules.size() - 1; i >= 0; i--) {
                    PolyfillRule pr = polyfillRules.get(i);
                    if (pr.matches(minsn.owner, minsn.name, minsn.desc)) {
                        String originalOwner = minsn.owner;
                        boolean isConstructor = "<init>".equals(minsn.name) && minsn.getOpcode() == Opcodes.INVOKESPECIAL;
                        if (isConstructor) {
                            // Strip preceding NEW and DUP opcodes for polyfilled constructor
                            AbstractInsnNode curr = minsn.getPrevious();
                            TypeInsnNode targetNew = null;
                            InsnNode targetDup = null;
                            int depth = 0;
                            while (curr != null) {
                                if (curr.getOpcode() == Opcodes.INVOKESPECIAL && curr instanceof MethodInsnNode subMin && "<init>".equals(subMin.name) && subMin.owner.equals(originalOwner)) {
                                    depth++;
                                } else if (curr.getOpcode() == Opcodes.DUP) {
                                    AbstractInsnNode prev = curr.getPrevious();
                                    while (prev != null && prev.getOpcode() < 0) {
                                        prev = prev.getPrevious();
                                    }
                                    if (prev instanceof TypeInsnNode tnode
                                            && tnode.getOpcode() == Opcodes.NEW
                                            && tnode.desc.equals(originalOwner)) {
                                        if (depth == 0) {
                                            targetDup = (InsnNode) curr;
                                            targetNew = tnode;
                                            break;
                                        } else {
                                            depth--;
                                        }
                                    }
                                }
                                curr = curr.getPrevious();
                            }
                            if (targetNew != null && targetDup != null) {
                                instructions.remove(targetNew);
                                instructions.remove(targetDup);
                                minsn.setOpcode(Opcodes.INVOKESTATIC);
                                minsn.owner = pr.getShimOwner();
                                minsn.name = pr.getShimName();
                                minsn.desc = pr.getShimDesc();
                                minsn.itf = false;
                                String castTarget = classRedirects.getOrDefault(originalOwner, originalOwner);
                                if (!pr.getShimDesc().endsWith("L" + originalOwner + ";") && !pr.getShimDesc().endsWith("L" + castTarget + ";")) {
                                    instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, castTarget));
                                }
                                modified = true;
                                break;
                            } else if (method != null && "<init>".equals(method.name)) {
                                // Preceding NEW / DUP not found in a constructor (e.g. subclass super() or this() call);
                                // leave constructor intact to preserve operand stack neutrality and avoid VerifyError.
                                break;
                            } else {
                                // In non-constructor methods (e.g. synthetic test instructions without explicit NEW/DUP),
                                // rewrite to static factory shim.
                                minsn.setOpcode(Opcodes.INVOKESTATIC);
                                minsn.owner = pr.getShimOwner();
                                minsn.name = pr.getShimName();
                                minsn.desc = pr.getShimDesc();
                                minsn.itf = false;
                                String castTarget = classRedirects.getOrDefault(originalOwner, originalOwner);
                                if (!pr.getShimDesc().endsWith("L" + originalOwner + ";") && !pr.getShimDesc().endsWith("L" + castTarget + ";")) {
                                    instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, castTarget));
                                }
                                modified = true;
                                break;
                            }
                        } else {
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = pr.getShimOwner();
                            minsn.name = pr.getShimName();
                            minsn.desc = pr.getShimDesc();
                            minsn.itf = false;
                            modified = true;
                            break;
                        }
                    }
                }

                // B. Check Method Redirect Rules
                for (MethodRedirectRule mr : methodRedirects) {
                    if (mr.matches(minsn.owner, minsn.name, minsn.desc)) {
                        if (mr.getTargetOpcode() != -1) {
                            minsn.setOpcode(mr.getTargetOpcode());
                        }
                        minsn.owner = mr.getTargetOwner();
                        minsn.name = mr.getTargetName();
                        if (mr.getTargetDesc() != null) {
                            minsn.desc = remapDescriptor(mr.getTargetDesc());
                        }
                        modified = true;
                        break;
                    }
                }

                // C. Check Class Redirects on Method Owner & Descriptors
                if (classRedirects.containsKey(minsn.owner)) {
                    minsn.owner = classRedirects.get(minsn.owner);
                    modified = true;
                }
                String remappedDesc = remapDescriptor(minsn.desc);
                if (!remappedDesc.equals(minsn.desc)) {
                    minsn.desc = remappedDesc;
                    modified = true;
                }
            }

            // Check Field Instructions
            else if (insn instanceof FieldInsnNode finsn) {
                boolean fieldPolyfilled = false;
                if (finsn.getOpcode() == Opcodes.GETSTATIC) {
                    for (int i = polyfillRules.size() - 1; i >= 0; i--) {
                        PolyfillRule pr = polyfillRules.get(i);
                        if (pr.matches(finsn.owner, finsn.name, finsn.desc)
                                || pr.matches(finsn.owner, finsn.name, "()" + finsn.desc)) {
                            MethodInsnNode staticCall = new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    pr.getShimOwner(),
                                    pr.getShimName(),
                                    pr.getShimDesc(),
                                    false
                            );
                            instructions.set(finsn, staticCall);
                            fieldPolyfilled = true;
                            modified = true;
                            break;
                        }
                    }
                }

                if (!fieldPolyfilled) {
                    for (FieldRedirectRule fr : fieldRedirects) {
                        if (fr.matches(finsn.owner, finsn.name, finsn.desc)) {
                            finsn.owner = fr.getTargetOwner();
                            finsn.name = fr.getTargetName();
                            if (fr.getTargetDesc() != null) {
                                finsn.desc = remapDescriptor(fr.getTargetDesc());
                            }
                            modified = true;
                            break;
                        }
                    }

                    if (classRedirects.containsKey(finsn.owner)) {
                        finsn.owner = classRedirects.get(finsn.owner);
                        modified = true;
                    }
                    String remappedDesc = remapDescriptor(finsn.desc);
                    if (!remappedDesc.equals(finsn.desc)) {
                        finsn.desc = remappedDesc;
                        modified = true;
                    }
                }
            }

            // Check Type Instructions (NEW, CHECKCAST, INSTANCEOF)
            else if (insn instanceof TypeInsnNode tinsn) {
                if (classRedirects.containsKey(tinsn.desc)) {
                    tinsn.desc = classRedirects.get(tinsn.desc);
                    modified = true;
                }
            }
        }

        return modified;
    }

    private String remapDescriptor(String desc) {
        if (desc == null || classRedirects.isEmpty()) return desc;

        String result = desc;
        for (Map.Entry<String, String> entry : classRedirects.entrySet()) {
            String from = "L" + entry.getKey() + ";";
            String to = "L" + entry.getValue() + ";";
            if (result.contains(from)) {
                result = result.replace(from, to);
            }
        }
        return result;
    }

    private boolean injectModernBlockAndBlockEntityBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyUseMethod = null;
        boolean hasUseItemOn = false;
        boolean hasUseWithoutItem = false;

        MethodNode legacySaveMethod = null;
        boolean hasModernSave = false;
        MethodNode legacyLoadMethod = null;
        boolean hasModernLoad = false;

        for (MethodNode method : classNode.methods) {
            if ("use".equals(method.name) && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;".equals(method.desc)) {
                legacyUseMethod = method;
            } else if ("useItemOn".equals(method.name) && (
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;".equals(method.desc)
                    || "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;".equals(method.desc)
            )) {
                hasUseItemOn = true;
            } else if ("useWithoutItem".equals(method.name) && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;".equals(method.desc)) {
                hasUseWithoutItem = true;
            } else if ("saveAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;)V".equals(method.desc)) {
                legacySaveMethod = method;
            } else if ("saveAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(method.desc)) {
                hasModernSave = true;
            } else if (("load".equals(method.name) || "loadAdditional".equals(method.name)) && "(Lnet/minecraft/nbt/CompoundTag;)V".equals(method.desc)) {
                legacyLoadMethod = method;
            } else if ("loadAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(method.desc)) {
                hasModernLoad = true;
            }
        }

        // Bridge Block.use -> useItemOn
        if (legacyUseMethod != null && !hasUseItemOn) {
            boolean isModernInteractionResult = targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.21.2"));
            if (isModernInteractionResult) {
                MethodNode useItemOnNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "useItemOn",
                        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                        null,
                        null
                );
                InsnList il = useItemOnNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // BlockState state
                il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Level level
                il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // BlockPos pos
                il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // Player player
                il.add(new VarInsnNode(Opcodes.ALOAD, 6)); // InteractionHand hand
                il.add(new VarInsnNode(Opcodes.ALOAD, 7)); // BlockHitResult hit
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "use", legacyUseMethod.desc, false));
                il.add(new InsnNode(Opcodes.ARETURN));
                useItemOnNode.maxStack = 7;
                useItemOnNode.maxLocals = 8;
                classNode.methods.add(useItemOnNode);
                modified = true;
            } else {
                MethodNode useItemOnNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "useItemOn",
                        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;",
                        null,
                        null
                );
                InsnList il = useItemOnNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // BlockState state
                il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Level level
                il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // BlockPos pos
                il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // Player player
                il.add(new VarInsnNode(Opcodes.ALOAD, 6)); // InteractionHand hand
                il.add(new VarInsnNode(Opcodes.ALOAD, 7)); // BlockHitResult hit
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "use", legacyUseMethod.desc, false));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/BlockInteractionShim", "toItemInteractionResult", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/ItemInteractionResult"));
                il.add(new InsnNode(Opcodes.ARETURN));
                useItemOnNode.maxStack = 8;
                useItemOnNode.maxLocals = 8;
                classNode.methods.add(useItemOnNode);
                modified = true;
            }
        }

        // Bridge Block.use -> useWithoutItem
        if (legacyUseMethod != null && !hasUseWithoutItem) {
            MethodNode useWithoutItemNode = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "useWithoutItem",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                    null,
                    null
            );
            InsnList il = useWithoutItemNode.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // BlockState state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Level level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // BlockPos pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // Player player
            il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/InteractionHand", "MAIN_HAND", "Lnet/minecraft/world/InteractionHand;"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // BlockHitResult hit
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "use", legacyUseMethod.desc, false));
            il.add(new InsnNode(Opcodes.ARETURN));
            useWithoutItemNode.maxStack = 7;
            useWithoutItemNode.maxLocals = 6;
            classNode.methods.add(useWithoutItemNode);
            modified = true;
        }

        // Bridge BlockEntity saveAdditional
        if (legacySaveMethod != null && !hasModernSave) {
            MethodNode saveBridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "saveAdditional",
                    "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
                    null,
                    null
            );
            InsnList il = saveBridge.instructions;
            if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName, "saveAdditional", "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", false));
            }
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/BlockEntityShim", "pushSave", "(Ljava/lang/Object;)V", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "saveAdditional", "(Lnet/minecraft/nbt/CompoundTag;)V", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/BlockEntityShim", "popSave", "(Ljava/lang/Object;)V", false));
            il.add(new InsnNode(Opcodes.RETURN));
            saveBridge.maxStack = 3;
            saveBridge.maxLocals = 3;
            classNode.methods.add(saveBridge);
            modified = true;
        }

        // Bridge BlockEntity load -> loadAdditional
        if (legacyLoadMethod != null && !hasModernLoad) {
            MethodNode loadBridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "loadAdditional",
                    "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
                    null,
                    null
            );
            InsnList il = loadBridge.instructions;
            if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName, "loadAdditional", "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", false));
            }
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/BlockEntityShim", "pushLoad", "(Ljava/lang/Object;)V", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyLoadMethod.name, "(Lnet/minecraft/nbt/CompoundTag;)V", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/BlockEntityShim", "popLoad", "(Ljava/lang/Object;)V", false));
            il.add(new InsnNode(Opcodes.RETURN));
            loadBridge.maxStack = 3;
            loadBridge.maxLocals = 3;
            classNode.methods.add(loadBridge);
            modified = true;
        }

        return modified;
    }

    private boolean transformModernEntityLevelAccess(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.GETFIELD && insn instanceof FieldInsnNode finsn) {
                    if ("level".equals(finsn.name) && "Lnet/minecraft/world/level/Level;".equals(finsn.desc)) {
                        MethodInsnNode getterCall = new MethodInsnNode(
                                Opcodes.INVOKEVIRTUAL,
                                finsn.owner,
                                "level",
                                "()Lnet/minecraft/world/level/Level;",
                                false
                        );
                        method.instructions.set(finsn, getterCall);
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    private boolean transformAttributeModifierConstructors(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL && insn instanceof MethodInsnNode minsn) {
                    if ("net/minecraft/world/entity/ai/attributes/AttributeModifier".equals(minsn.owner)
                            && "<init>".equals(minsn.name)
                            && "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V".equals(minsn.desc)) {

                        AbstractInsnNode curr = minsn.getPrevious();
                        TypeInsnNode targetNew = null;
                        InsnNode targetDup = null;
                        while (curr != null) {
                            if (curr.getOpcode() == Opcodes.DUP && curr.getPrevious() instanceof TypeInsnNode tnode
                                    && tnode.getOpcode() == Opcodes.NEW
                                    && "net/minecraft/world/entity/ai/attributes/AttributeModifier".equals(tnode.desc)) {
                                targetDup = (InsnNode) curr;
                                targetNew = tnode;
                                break;
                            }
                            curr = curr.getPrevious();
                        }

                        if (targetNew != null && targetDup != null) {
                            method.instructions.remove(targetNew);
                            method.instructions.remove(targetDup);
                            MethodInsnNode staticCall = new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "com/kyroxova/continuumlib/shims/AttributeModifierShim",
                                    "createModifier",
                                    "(Ljava/util/UUID;Ljava/lang/String;DLjava/lang/Object;)Ljava/lang/Object;",
                                    false
                            );
                            TypeInsnNode checkCast = new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/entity/ai/attributes/AttributeModifier");
                            method.instructions.set(minsn, staticCall);
                            method.instructions.insert(staticCall, checkCast);
                            modified = true;
                        }
                    }
                }
            }
        }
        return modified;
    }

    private boolean injectModernRecipeBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyAssemble = null;
        String legacyContainerType = null;
        boolean hasModernAssembleCraftingInput = false;
        boolean hasModernAssembleRecipeInput = false;

        MethodNode legacyMatches = null;
        boolean hasModernMatchesCraftingInput = false;
        boolean hasModernMatchesRecipeInput = false;

        MethodNode legacyGetResultItem = null;
        boolean hasModernGetResultItem = false;

        MethodNode legacyGetRemainingItems = null;
        boolean hasModernGetRemainingItemsCrafting = false;

        MethodNode legacyBuildCraftingRecipes = null;
        boolean hasModernBuildRecipes = false;

        for (MethodNode method : classNode.methods) {
            // Check assemble
            if ("assemble".equals(method.name)) {
                if ("(Lnet/minecraft/world/Container;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyAssemble = method;
                    legacyContainerType = "net/minecraft/world/Container";
                } else if ("(Lnet/minecraft/world/inventory/CraftingContainer;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyAssemble = method;
                    legacyContainerType = "net/minecraft/world/inventory/CraftingContainer";
                } else if ("(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyAssemble = method;
                    legacyContainerType = "net/minecraft/world/Container";
                } else if ("(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyAssemble = method;
                    legacyContainerType = "net/minecraft/world/inventory/CraftingContainer";
                } else if ("(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    hasModernAssembleCraftingInput = true;
                } else if ("(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    hasModernAssembleRecipeInput = true;
                }
            }

            // Check matches
            if ("matches".equals(method.name)) {
                if ("(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Z".equals(method.desc)
                        || "(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z".equals(method.desc)) {
                    legacyMatches = method;
                } else if ("(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z".equals(method.desc)) {
                    hasModernMatchesCraftingInput = true;
                } else if ("(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Z".equals(method.desc)) {
                    hasModernMatchesRecipeInput = true;
                }
            }

            // Check getResultItem
            if ("getResultItem".equals(method.name)) {
                if ("()Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyGetResultItem = method;
                } else if ("(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    legacyGetResultItem = method;
                } else if ("(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    hasModernGetResultItem = true;
                }
            }

            // Check getRemainingItems
            if ("getRemainingItems".equals(method.name)) {
                if (method.desc != null && (method.desc.startsWith("(Lnet/minecraft/world/Container;)") || method.desc.startsWith("(Lnet/minecraft/world/inventory/CraftingContainer;)"))) {
                    legacyGetRemainingItems = method;
                } else if ("(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/core/NonNullList;".equals(method.desc)) {
                    hasModernGetRemainingItemsCrafting = true;
                }
            }

            // Check buildCraftingRecipes / buildRecipes
            if (("buildCraftingRecipes".equals(method.name) || "buildRecipes".equals(method.name)) && "(Ljava/util/function/Consumer;)V".equals(method.desc)) {
                legacyBuildCraftingRecipes = method;
            } else if ("buildRecipes".equals(method.name) && "(Lnet/minecraft/data/recipes/RecipeOutput;)V".equals(method.desc)) {
                hasModernBuildRecipes = true;
            }
        }

        // 1. Inject assemble(CraftingInput, HolderLookup.Provider)
        if (legacyAssemble != null) {
            String targetContainer = legacyContainerType != null ? legacyContainerType : "net/minecraft/world/Container";
            boolean hasRegistryAccess = legacyAssemble.desc.contains("RegistryAccess;");

            if (!hasModernAssembleCraftingInput) {
                MethodNode assembleNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "assemble",
                        "(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
                        null,
                        null
                );
                InsnList il = assembleNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // CraftingInput
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                if (hasRegistryAccess) {
                    il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // HolderLookup.Provider
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapRegistryAccess", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
                }
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", legacyAssemble.desc, false));
                il.add(new InsnNode(Opcodes.ARETURN));
                assembleNode.maxStack = hasRegistryAccess ? 4 : 3;
                assembleNode.maxLocals = 3;
                classNode.methods.add(assembleNode);
                modified = true;
            }

            if (!hasModernAssembleRecipeInput) {
                MethodNode assembleNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "assemble",
                        "(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
                        null,
                        null
                );
                InsnList il = assembleNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // RecipeInput
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                if (hasRegistryAccess) {
                    il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // HolderLookup.Provider
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapRegistryAccess", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
                }
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", legacyAssemble.desc, false));
                il.add(new InsnNode(Opcodes.ARETURN));
                assembleNode.maxStack = hasRegistryAccess ? 4 : 3;
                assembleNode.maxLocals = 3;
                classNode.methods.add(assembleNode);
                modified = true;
            }
        }

        // 2. Inject matches(CraftingInput, Level)
        if (legacyMatches != null) {
            String targetContainer = legacyContainerType != null ? legacyContainerType : "net/minecraft/world/Container";

            if (!hasModernMatchesCraftingInput) {
                MethodNode matchesNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "matches",
                        "(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
                        null,
                        null
                );
                InsnList il = matchesNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // CraftingInput
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Level
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "matches", legacyMatches.desc, false));
                il.add(new InsnNode(Opcodes.IRETURN));
                matchesNode.maxStack = 3;
                matchesNode.maxLocals = 3;
                classNode.methods.add(matchesNode);
                modified = true;
            }

            if (!hasModernMatchesRecipeInput) {
                MethodNode matchesNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "matches",
                        "(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Z",
                        null,
                        null
                );
                InsnList il = matchesNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // RecipeInput
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Level
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "matches", legacyMatches.desc, false));
                il.add(new InsnNode(Opcodes.IRETURN));
                matchesNode.maxStack = 3;
                matchesNode.maxLocals = 3;
                classNode.methods.add(matchesNode);
                modified = true;
            }
        }

        // 3. Inject getResultItem(HolderLookup.Provider)
        if (legacyGetResultItem != null && !hasModernGetResultItem) {
            MethodNode resultNode = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getResultItem",
                    "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            InsnList il = resultNode.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            boolean hasRegistryAccess = legacyGetResultItem.desc.contains("RegistryAccess;");
            if (hasRegistryAccess) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // HolderLookup.Provider
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapRegistryAccess", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
            }
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "getResultItem", legacyGetResultItem.desc, false));
            il.add(new InsnNode(Opcodes.ARETURN));
            resultNode.maxStack = hasRegistryAccess ? 3 : 1;
            resultNode.maxLocals = 2;
            classNode.methods.add(resultNode);
            modified = true;
        }

        // 4. Inject getRemainingItems(CraftingInput)
        if (legacyGetRemainingItems != null && !hasModernGetRemainingItemsCrafting) {
            String targetContainer = legacyContainerType != null ? legacyContainerType : "net/minecraft/world/Container";
            MethodNode remNode = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getRemainingItems",
                    "(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/core/NonNullList;",
                    null,
                    null
            );
            InsnList il = remNode.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // CraftingInput
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "getRemainingItems", legacyGetRemainingItems.desc, false));
            il.add(new InsnNode(Opcodes.ARETURN));
            remNode.maxStack = 2;
            remNode.maxLocals = 2;
            classNode.methods.add(remNode);
            modified = true;
        }

        // 5. Inject buildRecipes(RecipeOutput) -> buildCraftingRecipes(Consumer)
        if (legacyBuildCraftingRecipes != null && !hasModernBuildRecipes) {
            MethodNode buildRecipesNode = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "buildRecipes",
                    "(Lnet/minecraft/data/recipes/RecipeOutput;)V",
                    null,
                    null
            );
            InsnList il = buildRecipesNode.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // RecipeOutput
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/RecipeShim",
                    "wrapOutput",
                    "(Ljava/lang/Object;)Ljava/util/function/Consumer;",
                    false
            ));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    classNode.name,
                    legacyBuildCraftingRecipes.name,
                    legacyBuildCraftingRecipes.desc,
                    false
            ));
            il.add(new InsnNode(Opcodes.RETURN));
            buildRecipesNode.maxStack = 2;
            buildRecipesNode.maxLocals = 2;
            classNode.methods.add(buildRecipesNode);
            modified = true;
        }

        return modified;
    }

    private boolean injectModernRandomTickBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyAnimateTick = null;
        boolean hasModernAnimateTick = false;

        MethodNode legacyRandomTick = null;
        boolean hasModernRandomTick = false;

        MethodNode legacyTick = null;
        boolean hasModernTick = false;

        for (MethodNode method : classNode.methods) {
            if ("animateTick".equals(method.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V".equals(method.desc)) {
                    legacyAnimateTick = method;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V".equals(method.desc)) {
                    hasModernAnimateTick = true;
                }
            } else if ("randomTick".equals(method.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V".equals(method.desc)) {
                    legacyRandomTick = method;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V".equals(method.desc)) {
                    hasModernRandomTick = true;
                }
            } else if ("tick".equals(method.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V".equals(method.desc)) {
                    legacyTick = method;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V".equals(method.desc)) {
                    hasModernTick = true;
                }
            }
        }

        // Inject animateTick bridge
        if (legacyAnimateTick != null && !hasModernAnimateTick) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "animateTick",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // RandomSource
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RandomShim", "toLegacyRandom", "(Ljava/lang/Object;)Ljava/util/Random;", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "animateTick", legacyAnimateTick.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 5;
            mn.maxLocals = 5;
            classNode.methods.add(mn);
            modified = true;
        }

        // Inject randomTick bridge
        if (legacyRandomTick != null && !hasModernRandomTick) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "randomTick",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // serverLevel
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // RandomSource
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RandomShim", "toLegacyRandom", "(Ljava/lang/Object;)Ljava/util/Random;", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "randomTick", legacyRandomTick.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 5;
            mn.maxLocals = 5;
            classNode.methods.add(mn);
            modified = true;
        }

        // Inject tick bridge
        if (legacyTick != null && !hasModernTick) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "tick",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // serverLevel
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // RandomSource
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RandomShim", "toLegacyRandom", "(Ljava/lang/Object;)Ljava/util/Random;", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "tick", legacyTick.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 5;
            mn.maxLocals = 5;
            classNode.methods.add(mn);
            modified = true;
        }

        return modified;
    }

    private boolean injectModernScreenBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyRender = null;
        boolean hasModernRender = false;

        MethodNode legacyRenderBg = null;
        boolean hasModernRenderBg = false;

        MethodNode legacyRenderLabels = null;
        boolean hasModernRenderLabels = false;

        MethodNode legacyRenderTooltip = null;
        boolean hasModernRenderTooltip = false;

        for (MethodNode method : classNode.methods) {
            if ("render".equals(method.name)) {
                if ("(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V".equals(method.desc)) {
                    legacyRender = method;
                } else if ("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V".equals(method.desc)) {
                    hasModernRender = true;
                }
            } else if ("renderBg".equals(method.name)) {
                if ("(Lcom/mojang/blaze3d/vertex/PoseStack;FII)V".equals(method.desc)) {
                    legacyRenderBg = method;
                } else if ("(Lnet/minecraft/client/gui/GuiGraphics;FII)V".equals(method.desc)) {
                    hasModernRenderBg = true;
                }
            } else if ("renderLabels".equals(method.name)) {
                if ("(Lcom/mojang/blaze3d/vertex/PoseStack;II)V".equals(method.desc)) {
                    legacyRenderLabels = method;
                } else if ("(Lnet/minecraft/client/gui/GuiGraphics;II)V".equals(method.desc)) {
                    hasModernRenderLabels = true;
                }
            } else if ("renderTooltip".equals(method.name)) {
                if ("(Lcom/mojang/blaze3d/vertex/PoseStack;II)V".equals(method.desc)) {
                    legacyRenderTooltip = method;
                } else if ("(Lnet/minecraft/client/gui/GuiGraphics;II)V".equals(method.desc)) {
                    hasModernRenderTooltip = true;
                }
            }
        }

        // Bridge Screen.render(GuiGraphics, int, int, float) -> render(PoseStack, int, int, float)
        if (legacyRender != null && !hasModernRender) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "render",
                    "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // GuiGraphics
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "extractPose", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, "com/mojang/blaze3d/vertex/PoseStack"));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2)); // mouseX
            il.add(new VarInsnNode(Opcodes.ILOAD, 3)); // mouseY
            il.add(new VarInsnNode(Opcodes.FLOAD, 4)); // partialTicks
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "render", legacyRender.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 5;
            mn.maxLocals = 5;
            classNode.methods.add(mn);
            modified = true;
        }

        // Bridge Screen.renderBg(GuiGraphics, float, int, int) -> renderBg(PoseStack, float, int, int)
        if (legacyRenderBg != null && !hasModernRenderBg) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "renderBg",
                    "(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // GuiGraphics
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "extractPose", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, "com/mojang/blaze3d/vertex/PoseStack"));
            il.add(new VarInsnNode(Opcodes.FLOAD, 2)); // partialTicks
            il.add(new VarInsnNode(Opcodes.ILOAD, 3)); // mouseX
            il.add(new VarInsnNode(Opcodes.ILOAD, 4)); // mouseY
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "renderBg", legacyRenderBg.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 5;
            mn.maxLocals = 5;
            classNode.methods.add(mn);
            modified = true;
        }

        // Bridge Screen.renderLabels(GuiGraphics, int, int) -> renderLabels(PoseStack, int, int)
        if (legacyRenderLabels != null && !hasModernRenderLabels) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "renderLabels",
                    "(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // GuiGraphics
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "extractPose", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, "com/mojang/blaze3d/vertex/PoseStack"));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2)); // mouseX
            il.add(new VarInsnNode(Opcodes.ILOAD, 3)); // mouseY
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "renderLabels", legacyRenderLabels.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 4;
            mn.maxLocals = 4;
            classNode.methods.add(mn);
            modified = true;
        }

        // Bridge Screen.renderTooltip(GuiGraphics, int, int) -> renderTooltip(PoseStack, int, int)
        if (legacyRenderTooltip != null && !hasModernRenderTooltip) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "renderTooltip",
                    "(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // GuiGraphics
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "extractPose", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, "com/mojang/blaze3d/vertex/PoseStack"));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2)); // mouseX
            il.add(new VarInsnNode(Opcodes.ILOAD, 3)); // mouseY
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "renderTooltip", legacyRenderTooltip.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            mn.maxStack = 4;
            mn.maxLocals = 4;
            classNode.methods.add(mn);
            modified = true;
        }

        return modified;
    }

    private boolean injectModernLootBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyGetDrops = null;
        boolean hasModernGetDrops = false;

        for (MethodNode method : classNode.methods) {
            if ("getDrops".equals(method.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootContext$Builder;)Ljava/util/List;".equals(method.desc)) {
                    legacyGetDrops = method;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;".equals(method.desc)) {
                    hasModernGetDrops = true;
                }
            }
        }

        if (legacyGetDrops != null && !hasModernGetDrops) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getDrops",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
                    null,
                    null
            );
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // BlockState
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // LootParams.Builder
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/level/storage/loot/LootContext$Builder"));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "getDrops", legacyGetDrops.desc, false));
            il.add(new InsnNode(Opcodes.ARETURN));
            mn.maxStack = 3;
            mn.maxLocals = 3;
            classNode.methods.add(mn);
            modified = true;
        }

        return modified;
    }
}
