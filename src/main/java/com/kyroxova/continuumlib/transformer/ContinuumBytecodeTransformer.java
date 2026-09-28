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

                if (transformInstructions(method.instructions)) {
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

        return modified;
    }

    private boolean transformInstructions(InsnList instructions) {
        if (instructions == null) return false;
        boolean modified = false;

        for (AbstractInsnNode insn : instructions.toArray()) {
            // Check Method Invocations
            if (insn instanceof MethodInsnNode minsn) {
                // A. Check Polyfill Rules first
                for (PolyfillRule pr : polyfillRules) {
                    if (pr.matches(minsn.owner, minsn.name, minsn.desc)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = pr.getShimOwner();
                        minsn.name = pr.getShimName();
                        minsn.desc = pr.getShimDesc();
                        minsn.itf = false;
                        modified = true;
                        break;
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
            } else if ("useItemOn".equals(method.name) && "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;".equals(method.desc)) {
                hasUseItemOn = true;
            } else if ("useWithoutItem".equals(method.name) && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;".equals(method.desc)) {
                hasUseWithoutItem = true;
            } else if ("saveAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;)V".equals(method.desc)) {
                legacySaveMethod = method;
            } else if ("saveAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(method.desc)) {
                hasModernSave = true;
            } else if ("load".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;)V".equals(method.desc)) {
                legacyLoadMethod = method;
            } else if ("loadAdditional".equals(method.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(method.desc)) {
                hasModernLoad = true;
            }
        }

        // Bridge Block.use -> useItemOn
        if (legacyUseMethod != null && !hasUseItemOn) {
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
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "saveAdditional", "(Lnet/minecraft/nbt/CompoundTag;)V", false));
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
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "load", "(Lnet/minecraft/nbt/CompoundTag;)V", false));
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
}
