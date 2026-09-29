package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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

        // Standard version-dependent class redirects
        if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) {
            classRedirects.putIfAbsent("net/minecraft/world/World", "net/minecraft/world/level/Level");
            classRedirects.putIfAbsent("net/minecraft/world/server/ServerWorld", "net/minecraft/server/level/ServerLevel");
            classRedirects.putIfAbsent("net/minecraft/client/world/ClientWorld", "net/minecraft/client/multiplayer/ClientLevel");
            classRedirects.putIfAbsent("net/minecraft/world/IBlockReader", "net/minecraft/world/level/BlockGetter");
            classRedirects.putIfAbsent("net/minecraft/world/IWorld", "net/minecraft/world/level/LevelAccessor");
            classRedirects.putIfAbsent("net/minecraft/world/IWorldReader", "net/minecraft/world/level/LevelReader");
            classRedirects.putIfAbsent("net/minecraft/tileentity/TileEntity", "net/minecraft/world/level/block/entity/BlockEntity");
            classRedirects.putIfAbsent("net/minecraft/client/settings/KeyBinding", "net/minecraft/client/KeyMapping");
            classRedirects.putIfAbsent("net/minecraft/particles/IParticleData", "net/minecraft/core/particles/ParticleOptions");
            classRedirects.putIfAbsent("net/minecraft/particles/ParticleType", "net/minecraft/core/particles/ParticleType");
            classRedirects.putIfAbsent("net/minecraft/util/math/AxisAlignedBB", "net/minecraft/world/phys/AABB");
            classRedirects.putIfAbsent("net/minecraft/util/math/shapes/VoxelShape", "net/minecraft/world/phys/shapes/VoxelShape");
            classRedirects.putIfAbsent("net/minecraft/util/math/shapes/VoxelShapes", "net/minecraft/world/phys/shapes/Shapes");
            classRedirects.putIfAbsent("net/minecraft/util/math/BlockPos", "net/minecraft/core/BlockPos");
            classRedirects.putIfAbsent("net/minecraft/util/SoundCategory", "net/minecraft/sounds/SoundSource");
        } else {
            classRedirects.putIfAbsent("net/minecraft/world/level/Level", "net/minecraft/world/World");
            classRedirects.putIfAbsent("net/minecraft/server/level/ServerLevel", "net/minecraft/world/server/ServerWorld");
            classRedirects.putIfAbsent("net/minecraft/client/multiplayer/ClientLevel", "net/minecraft/client/world/ClientWorld");
            classRedirects.putIfAbsent("net/minecraft/world/level/BlockGetter", "net/minecraft/world/IBlockReader");
            classRedirects.putIfAbsent("net/minecraft/world/level/LevelAccessor", "net/minecraft/world/IWorld");
            classRedirects.putIfAbsent("net/minecraft/world/level/LevelReader", "net/minecraft/world/IWorldReader");
            classRedirects.putIfAbsent("net/minecraft/world/level/block/entity/BlockEntity", "net/minecraft/tileentity/TileEntity");
            classRedirects.putIfAbsent("net/minecraft/client/KeyMapping", "net/minecraft/client/settings/KeyBinding");
            classRedirects.putIfAbsent("net/minecraft/core/particles/ParticleOptions", "net/minecraft/particles/IParticleData");
            classRedirects.putIfAbsent("net/minecraft/core/particles/ParticleType", "net/minecraft/particles/ParticleType");
            classRedirects.putIfAbsent("net/minecraft/world/phys/AABB", "net/minecraft/util/math/AxisAlignedBB");
            classRedirects.putIfAbsent("net/minecraft/world/phys/shapes/VoxelShape", "net/minecraft/util/math/shapes/VoxelShape");
            classRedirects.putIfAbsent("net/minecraft/world/phys/shapes/Shapes", "net/minecraft/util/math/shapes/VoxelShapes");
            classRedirects.putIfAbsent("net/minecraft/core/BlockPos", "net/minecraft/util/math/BlockPos");
            classRedirects.putIfAbsent("net/minecraft/sounds/SoundSource", "net/minecraft/util/SoundCategory");
        }

        // Explosion interaction redirects
        if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"))) {
            classRedirects.putIfAbsent("net/minecraft/world/level/Explosion$BlockInteraction", "net/minecraft/world/level/Level$ExplosionInteraction");
            classRedirects.putIfAbsent("net/minecraft/world/Explosion$Mode", "net/minecraft/world/level/Level$ExplosionInteraction");
        } else {
            classRedirects.putIfAbsent("net/minecraft/world/level/Level$ExplosionInteraction", "net/minecraft/world/level/Explosion$BlockInteraction");
        }

        // CriteriaTriggers package relocation (26.3+)
        if (targetSpec != null && (targetSpec.getVersion().isAtLeast(MCVersion.of("26.3")) || targetSpec.getVersion().getMajor() >= 26)) {
            classRedirects.putIfAbsent("net/minecraft/advancements/CriteriaTriggers", "net/minecraft/advancements/triggers/CriteriaTriggers");
        } else {
            classRedirects.putIfAbsent("net/minecraft/advancements/triggers/CriteriaTriggers", "net/minecraft/advancements/CriteriaTriggers");
        }

        // AdvancementHolder -> Advancement (<= 1.20.1)
        if (targetSpec != null && !targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.2"))) {
            classRedirects.putIfAbsent("net/minecraft/advancements/AdvancementHolder", "net/minecraft/advancements/Advancement");
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

        // 11. Modern VoxelShape bridges (>= 1.13)
        if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.13"))) {
            if (injectModernVoxelShapeBridges(classNode)) {
                modified = true;
            }
        }

        // 12. Evolutionary shifts synthetic bridges for BlockBehaviour and Item (>= 26.3)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("26.3"))) {
            if (injectModern26_3Bridges(classNode)) {
                modified = true;
            }
        }

        // 13. Modern SynchedEntityData bridge (>= 1.20.5)
        if (targetSpec != null && targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.5"))) {
            if (injectModernEntitySyncedDataBridges(classNode)) {
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
                    if (matchesPolyfillRule(pr, minsn, classNode)) {
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
                            Type originalReturnType = Type.getReturnType(minsn.desc);
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = pr.getShimOwner();
                            minsn.name = pr.getShimName();
                            minsn.desc = pr.getShimDesc();
                            minsn.itf = false;
                            Type shimReturnType = Type.getReturnType(pr.getShimDesc());
                            if (originalReturnType.getSort() == Type.OBJECT && !"java/lang/Object".equals(originalReturnType.getInternalName())) {
                                if (shimReturnType.getSort() == Type.OBJECT && "java/lang/Object".equals(shimReturnType.getInternalName())) {
                                    String targetType = classRedirects.getOrDefault(originalReturnType.getInternalName(), originalReturnType.getInternalName());
                                    instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, targetType));
                                }
                            }
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

                // D. Subsystem Method Redirections
                // 1. Particle dispatch
                if (isLevelOrWorld(minsn.owner)) {
                    if (("addParticle".equals(minsn.name) || "spawnParticle".equals(minsn.name))
                            && minsn.desc != null && minsn.desc.endsWith("DDDDDD)V")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/ParticleShim";
                        minsn.name = "addParticle";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;DDDDDD)V";
                        minsn.itf = false;
                        modified = true;
                    } else if (("addParticle".equals(minsn.name) || "spawnParticle".equals(minsn.name))
                            && minsn.desc != null && minsn.desc.endsWith("ZDDDDDD)V")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/ParticleShim";
                        minsn.name = "addParticle";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;ZDDDDDD)V";
                        minsn.itf = false;
                        modified = true;
                    } else if (("sendParticles".equals(minsn.name) || "spawnParticle".equals(minsn.name))
                            && minsn.desc != null && minsn.desc.endsWith("DDDIDDDD)I")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/ParticleShim";
                        minsn.name = "sendParticles";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;DDDIDDDD)I";
                        minsn.itf = false;
                        modified = true;
                    }
                }

                // 2. World/Level isClientSide / isRemote method call
                if (isLevelOrWorld(minsn.owner) && ("isClientSide".equals(minsn.name) || "isRemote".equals(minsn.name)) && "()Z".equals(minsn.desc)) {
                    if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) {
                        minsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                        minsn.name = "isClientSide";
                        minsn.desc = "()Z";
                        modified = true;
                    } else {
                        FieldInsnNode getField = new FieldInsnNode(
                                Opcodes.GETFIELD,
                                classRedirects.getOrDefault(minsn.owner, "net/minecraft/world/World"),
                                "isRemote",
                                "Z"
                        );
                        instructions.set(minsn, getField);
                        modified = true;
                    }
                }

                // 3. getBlockEntity <-> getTileEntity
                if (isBlockGetterOrReader(minsn.owner) && ("getBlockEntity".equals(minsn.name) || "getTileEntity".equals(minsn.name))) {
                    if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) {
                        minsn.name = "getBlockEntity";
                    } else {
                        minsn.name = "getTileEntity";
                    }
                    minsn.desc = remapDescriptor(minsn.desc);
                    modified = true;
                }

                // 4. KeyMapping <-> KeyBinding methods
                if (isKeyMappingOrBinding(minsn.owner)) {
                    if (("isKeyDown".equals(minsn.name) || "isDown".equals(minsn.name)) && "()Z".equals(minsn.desc)) {
                        minsn.name = (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) ? "isDown" : "isKeyDown";
                        minsn.desc = "()Z";
                        modified = true;
                    } else if (("consumeClick".equals(minsn.name) || "isPressed".equals(minsn.name)) && "()Z".equals(minsn.desc)) {
                        minsn.name = (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) ? "consumeClick" : "isPressed";
                        minsn.desc = "()Z";
                        modified = true;
                    }
                }

                // 5. VoxelShape / Shapes static helper redirects for legacy targets (< 1.13)
                if (targetSpec != null && !targetSpec.getVersion().isAtLeast(MCVersion.of("1.13"))) {
                    if ("box".equals(minsn.name) && (minsn.owner.contains("Shapes") || minsn.owner.contains("Block"))) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/VoxelShapeShim";
                        minsn.name = "box";
                        minsn.desc = "(DDDDDD)Ljava/lang/Object;";
                        minsn.itf = false;
                        modified = true;
                    } else if ("or".equals(minsn.name) && minsn.owner.contains("Shapes")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/VoxelShapeShim";
                        minsn.name = "or";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                        minsn.itf = false;
                        modified = true;
                    } else if (isVoxelShape(minsn.owner) && "bounds".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/VoxelShapeShim";
                        minsn.name = "toAABB";
                        minsn.desc = "(Ljava/lang/Object;)Lcom/kyroxova/continuumlib/shims/VoxelShapeShim$VirtualAABB;";
                        minsn.itf = false;
                        modified = true;
                    }
                }

                // 6. EnchantmentHelper static polyfill redirects
                if (isEnchantmentHelper(minsn.owner)) {
                    if ("getItemEnchantmentLevel".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/EnchantmentShim";
                        minsn.name = "getItemEnchantmentLevel";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)I";
                        minsn.itf = false;
                        modified = true;
                    } else if ("getEnchantmentLevel".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/EnchantmentShim";
                        minsn.name = "getEnchantmentLevel";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)I";
                        minsn.itf = false;
                        modified = true;
                    }
                }

                // 7. LivingEntity virtual polyfill redirects
                if (isLivingEntity(minsn.owner, classNode) && (minsn.getOpcode() == Opcodes.INVOKEVIRTUAL || minsn.getOpcode() == Opcodes.INVOKEINTERFACE)) {
                    if ("getAttributeValue".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/LivingEntityShim";
                        minsn.name = "getAttributeValue";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)D";
                        minsn.itf = false;
                        modified = true;
                    } else if ("getAttribute".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/LivingEntityShim";
                        minsn.name = "getAttribute";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                        minsn.itf = false;
                        instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, classRedirects.getOrDefault("net/minecraft/world/entity/ai/attributes/AttributeInstance", "net/minecraft/world/entity/ai/attributes/AttributeInstance")));
                        modified = true;
                    } else if ("getItemBySlot".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/LivingEntityShim";
                        minsn.name = "getItemBySlot";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                        minsn.itf = false;
                        instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, classRedirects.getOrDefault("net/minecraft/world/item/ItemStack", "net/minecraft/world/item/ItemStack")));
                        modified = true;
                    } else if ("setItemSlot".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/LivingEntityShim";
                        minsn.name = "setItemSlot";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
                        minsn.itf = false;
                        modified = true;
                    } else if ("hasEffect".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/MobEffectShim";
                        minsn.name = "hasEffect";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
                        minsn.itf = false;
                        modified = true;
                    } else if ("getEffect".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/MobEffectShim";
                        minsn.name = "getEffect";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                        minsn.itf = false;
                        instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, classRedirects.getOrDefault("net/minecraft/world/effect/MobEffectInstance", "net/minecraft/world/effect/MobEffectInstance")));
                        modified = true;
                    } else if ("removeEffect".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/MobEffectShim";
                        minsn.name = "removeEffect";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
                        minsn.itf = false;
                        modified = true;
                    } else if ("addEffect".equals(minsn.name)) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = "com/kyroxova/continuumlib/shims/MobEffectShim";
                        minsn.name = "addEffect";
                        minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
                        minsn.itf = false;
                        modified = true;
                    }
                }

                // 8. Level.explode return type shift & stack neutrality (void on >= 1.20 vs Explosion on <= 1.19.4)
                if (isLevelOrWorld(minsn.owner) && "explode".equals(minsn.name)) {
                    boolean targetIs1_20Plus = targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"));
                    if (targetIs1_20Plus) {
                        if (minsn.desc != null && (minsn.desc.endsWith("Lnet/minecraft/world/level/Explosion;") || minsn.desc.endsWith("Lnet/minecraft/world/Explosion;"))) {
                            minsn.desc = minsn.desc.substring(0, minsn.desc.lastIndexOf(')') + 1) + "V";
                            AbstractInsnNode next = minsn.getNext();
                            while (next != null && next.getOpcode() < 0) {
                                next = next.getNext();
                            }
                            if (next != null && next.getOpcode() == Opcodes.POP) {
                                instructions.remove(next);
                            } else {
                                instructions.insert(minsn, new InsnNode(Opcodes.ACONST_NULL));
                            }
                            modified = true;
                        }
                    } else {
                        if (minsn.desc != null && minsn.desc.endsWith(")V")) {
                            String explosionOwner = classRedirects.getOrDefault("net/minecraft/world/level/Explosion", "net/minecraft/world/level/Explosion");
                            minsn.desc = minsn.desc.substring(0, minsn.desc.lastIndexOf(')') + 1) + "L" + explosionOwner + ";";
                            instructions.insert(minsn, new InsnNode(Opcodes.POP));
                            modified = true;
                        }
                    }
                }

                // 9. Advancement$Builder and AdvancementHolder rewrites
                if (isAdvancementBuilder(minsn.owner) && "build".equals(minsn.name)) {
                    boolean targetIs1_20_2Plus = targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.2"));
                    if (targetIs1_20_2Plus) {
                        if (minsn.desc != null && minsn.desc.endsWith("Lnet/minecraft/advancements/Advancement;")) {
                            minsn.desc = minsn.desc.substring(0, minsn.desc.lastIndexOf(')') + 1) + "Lnet/minecraft/advancements/AdvancementHolder;";
                            MethodInsnNode valueCall = new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/advancements/AdvancementHolder",
                                    "value",
                                    "()Lnet/minecraft/advancements/Advancement;",
                                    false
                            );
                            instructions.insert(minsn, valueCall);
                            modified = true;
                        }
                    } else {
                        if (minsn.desc != null && minsn.desc.endsWith("Lnet/minecraft/advancements/AdvancementHolder;")) {
                            minsn.desc = minsn.desc.substring(0, minsn.desc.lastIndexOf(')') + 1) + "Lnet/minecraft/advancements/Advancement;";
                            modified = true;
                        }
                    }
                } else if (isAdvancementHolder(minsn.owner)) {
                    boolean targetIs1_20_2Plus = targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.20.2"));
                    if (!targetIs1_20_2Plus) {
                        if ("value".equals(minsn.name) && (minsn.desc == null || minsn.desc.endsWith("Lnet/minecraft/advancements/Advancement;"))) {
                            instructions.remove(minsn);
                            modified = true;
                        } else if ("id".equals(minsn.name) && (minsn.desc == null || minsn.desc.endsWith("Lnet/minecraft/resources/ResourceLocation;"))) {
                            minsn.owner = "net/minecraft/advancements/Advancement";
                            minsn.name = "getId";
                            minsn.desc = "()Lnet/minecraft/resources/ResourceLocation;";
                            modified = true;
                        }
                    }
                }
            }

            // Check Field Instructions
            else if (insn instanceof FieldInsnNode finsn) {
                boolean fieldPolyfilled = false;
                if (finsn.getOpcode() == Opcodes.GETFIELD && isLevelOrWorld(finsn.owner)
                        && ("isRemote".equals(finsn.name) || "isClientSide".equals(finsn.name))
                        && "Z".equals(finsn.desc)) {
                    if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.17"))) {
                        MethodInsnNode getterCall = new MethodInsnNode(
                                Opcodes.INVOKEVIRTUAL,
                                classRedirects.getOrDefault(finsn.owner, "net/minecraft/world/level/Level"),
                                "isClientSide",
                                "()Z",
                                false
                        );
                        instructions.set(finsn, getterCall);
                        fieldPolyfilled = true;
                        modified = true;
                    } else {
                        finsn.name = "isRemote";
                        finsn.desc = "Z";
                        if (classRedirects.containsKey(finsn.owner)) {
                            finsn.owner = classRedirects.get(finsn.owner);
                        }
                        modified = true;
                    }
                }
                if (finsn.getOpcode() == Opcodes.GETSTATIC) {
                    // Explosion interaction enum constant remapping
                    if (targetSpec == null || targetSpec.getVersion().isAtLeast(MCVersion.of("1.20"))) {
                        if ("net/minecraft/world/level/Explosion$BlockInteraction".equals(finsn.owner)
                                || "net/minecraft/world/Explosion$Mode".equals(finsn.owner)
                                || "net/minecraft/world/level/Level$ExplosionInteraction".equals(finsn.owner)) {
                            finsn.owner = "net/minecraft/world/level/Level$ExplosionInteraction";
                            finsn.desc = "Lnet/minecraft/world/level/Level$ExplosionInteraction;";
                            if ("BREAK".equals(finsn.name) || "DESTROY".equals(finsn.name)) {
                                finsn.name = "BLOCK";
                            } else if ("KEEP".equals(finsn.name)) {
                                finsn.name = "NONE";
                            }
                            modified = true;
                        }
                    } else {
                        if ("net/minecraft/world/level/Level$ExplosionInteraction".equals(finsn.owner)
                                || "net/minecraft/world/level/Explosion$BlockInteraction".equals(finsn.owner)
                                || "net/minecraft/world/Explosion$Mode".equals(finsn.owner)) {
                            finsn.owner = "net/minecraft/world/level/Explosion$BlockInteraction";
                            finsn.desc = "Lnet/minecraft/world/level/Explosion$BlockInteraction;";
                            if ("BLOCK".equals(finsn.name) || "TNT".equals(finsn.name)) {
                                finsn.name = "BREAK";
                            } else if ("MOB".equals(finsn.name)) {
                                finsn.name = "DESTROY";
                            } else if ("TRIGGER".equals(finsn.name)) {
                                finsn.name = "NONE";
                            }
                            modified = true;
                        }
                    }

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
        MethodNode modernAssemble2Arg = null;
        boolean is26_3Plus = targetSpec != null && (targetSpec.getVersion().isAtLeast(MCVersion.of("26.3")) || targetSpec.getVersion().getMajor() >= 26);

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
                    if (!is26_3Plus) {
                        hasModernAssembleCraftingInput = true;
                    } else {
                        modernAssemble2Arg = method;
                    }
                } else if ("(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    if (!is26_3Plus) {
                        hasModernAssembleRecipeInput = true;
                    } else {
                        modernAssemble2Arg = method;
                    }
                } else if ("(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    if (is26_3Plus) {
                        hasModernAssembleCraftingInput = true;
                    }
                } else if ("(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;".equals(method.desc)) {
                    if (is26_3Plus) {
                        hasModernAssembleRecipeInput = true;
                    }
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

        // 1. Inject assemble bridge
        if (is26_3Plus) {
            // For 26.3+: 1-arg assemble(CraftingInput) and assemble(RecipeInput)
            if (!hasModernAssembleCraftingInput && (legacyAssemble != null || modernAssemble2Arg != null)) {
                MethodNode assembleNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "assemble",
                        "(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
                        null,
                        null
                );
                InsnList il = assembleNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // CraftingInput
                if (legacyAssemble != null) {
                    String targetContainer = legacyContainerType != null ? legacyContainerType : "net/minecraft/world/Container";
                    boolean hasRegistryAccess = legacyAssemble.desc.contains("RegistryAccess;");
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                    if (hasRegistryAccess) {
                        il.add(new InsnNode(Opcodes.ACONST_NULL));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapRegistryAccess", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", legacyAssemble.desc, false));
                    assembleNode.maxStack = hasRegistryAccess ? 4 : 2;
                } else {
                    il.add(new InsnNode(Opcodes.ACONST_NULL));
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", modernAssemble2Arg.desc, false));
                    assembleNode.maxStack = 3;
                }
                il.add(new InsnNode(Opcodes.ARETURN));
                assembleNode.maxLocals = 2;
                classNode.methods.add(assembleNode);
                hasModernAssembleCraftingInput = true;
                modified = true;
            }

            if (!hasModernAssembleRecipeInput && (legacyAssemble != null || modernAssemble2Arg != null)) {
                MethodNode assembleNode = new MethodNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        "assemble",
                        "(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;",
                        null,
                        null
                );
                InsnList il = assembleNode.instructions;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // RecipeInput
                if (legacyAssemble != null) {
                    String targetContainer = legacyContainerType != null ? legacyContainerType : "net/minecraft/world/Container";
                    boolean hasRegistryAccess = legacyAssemble.desc.contains("RegistryAccess;");
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapInput", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    il.add(new TypeInsnNode(Opcodes.CHECKCAST, targetContainer));
                    if (hasRegistryAccess) {
                        il.add(new InsnNode(Opcodes.ACONST_NULL));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/RecipeShim", "wrapRegistryAccess", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", legacyAssemble.desc, false));
                    assembleNode.maxStack = hasRegistryAccess ? 4 : 2;
                } else {
                    il.add(new InsnNode(Opcodes.ACONST_NULL));
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "assemble", modernAssemble2Arg.desc, false));
                    assembleNode.maxStack = 3;
                }
                il.add(new InsnNode(Opcodes.ARETURN));
                assembleNode.maxLocals = 2;
                classNode.methods.add(assembleNode);
                hasModernAssembleRecipeInput = true;
                modified = true;
            }
        } else {
            // For 1.20.5 - 1.21.1: 2-arg assemble(CraftingInput, HolderLookup.Provider) and assemble(RecipeInput, HolderLookup.Provider)
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

    private boolean injectModernVoxelShapeBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        MethodNode legacyBoundingBox = null;
        MethodNode legacyCollisionBox = null;
        boolean hasModernGetShape = false;
        boolean hasModernGetCollisionShape = false;

        for (MethodNode m : classNode.methods) {
            if ("getBoundingBox".equals(m.name)) {
                legacyBoundingBox = m;
            } else if ("getCollisionBoundingBox".equals(m.name)) {
                legacyCollisionBox = m;
            } else if ("getShape".equals(m.name)) {
                hasModernGetShape = true;
            } else if ("getCollisionShape".equals(m.name)) {
                hasModernGetCollisionShape = true;
            }
        }

        String blockStateClass = classRedirects.getOrDefault("net/minecraft/world/level/block/state/BlockState", "net/minecraft/world/level/block/state/BlockState");
        String blockGetterClass = classRedirects.getOrDefault("net/minecraft/world/level/BlockGetter", "net/minecraft/world/level/BlockGetter");
        String blockPosClass = classRedirects.getOrDefault("net/minecraft/core/BlockPos", "net/minecraft/core/BlockPos");
        String collisionContextClass = classRedirects.getOrDefault("net/minecraft/world/phys/shapes/CollisionContext", "net/minecraft/world/phys/shapes/CollisionContext");
        String voxelShapeClass = classRedirects.getOrDefault("net/minecraft/world/phys/shapes/VoxelShape", "net/minecraft/world/phys/shapes/VoxelShape");
        String modernShapeDesc = "(L" + blockStateClass + ";L" + blockGetterClass + ";L" + blockPosClass + ";L" + collisionContextClass + ";)L" + voxelShapeClass + ";";

        if (legacyBoundingBox != null && !hasModernGetShape) {
            MethodNode shapeBridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getShape",
                    modernShapeDesc,
                    null,
                    null
            );
            InsnList il = shapeBridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyBoundingBox.name, legacyBoundingBox.desc, false));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/VoxelShapeShim", "fromAABB", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, voxelShapeClass));
            il.add(new InsnNode(Opcodes.ARETURN));
            shapeBridge.maxStack = 4;
            shapeBridge.maxLocals = 5;
            classNode.methods.add(shapeBridge);
            modified = true;
        }

        if (legacyCollisionBox != null && !hasModernGetCollisionShape) {
            MethodNode colShapeBridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getCollisionShape",
                    modernShapeDesc,
                    null,
                    null
            );
            InsnList il = colShapeBridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyCollisionBox.name, legacyCollisionBox.desc, false));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/kyroxova/continuumlib/shims/VoxelShapeShim", "fromAABB", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            il.add(new TypeInsnNode(Opcodes.CHECKCAST, voxelShapeClass));
            il.add(new InsnNode(Opcodes.ARETURN));
            colShapeBridge.maxStack = 4;
            colShapeBridge.maxLocals = 5;
            classNode.methods.add(colShapeBridge);
            modified = true;
        }

        return modified;
    }

    private boolean isLevelOrWorld(String owner) {
        if (owner == null) return false;
        return owner.equals("net/minecraft/world/level/Level")
                || owner.equals("net/minecraft/world/World")
                || owner.equals("net/minecraft/server/level/ServerLevel")
                || owner.equals("net/minecraft/world/server/ServerWorld")
                || owner.equals("net/minecraft/client/multiplayer/ClientLevel")
                || owner.equals("net/minecraft/client/world/ClientWorld")
                || owner.equals("net/minecraft/world/level/LevelAccessor")
                || owner.equals("net/minecraft/world/level/LevelReader")
                || owner.equals("net/minecraft/world/level/CommonLevelAccessor")
                || owner.equals("net/minecraft/world/level/ServerLevelAccessor")
                || owner.equals("net/minecraft/world/IWorld")
                || owner.equals("net/minecraft/world/IWorldReader");
    }

    private boolean isBlockGetterOrReader(String owner) {
        if (owner == null) return false;
        return isLevelOrWorld(owner)
                || owner.equals("net/minecraft/world/level/BlockGetter")
                || owner.equals("net/minecraft/world/IBlockReader")
                || owner.equals("net/minecraft/world/IBlockAccess");
    }

    private boolean isKeyMappingOrBinding(String owner) {
        if (owner == null) return false;
        return owner.equals("net/minecraft/client/KeyMapping")
                || owner.equals("net/minecraft/client/settings/KeyBinding");
    }

    private boolean isVoxelShape(String owner) {
        if (owner == null) return false;
        return owner.equals("net/minecraft/world/phys/shapes/VoxelShape")
                || owner.equals("net/minecraft/util/math/shapes/VoxelShape");
    }

    private boolean isEnchantmentHelper(String owner) {
        if (owner == null) return false;
        String normalized = owner.replace('.', '/');
        return normalized.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")
                || normalized.equals("net/minecraft/enchantment/EnchantmentHelper");
    }

    private boolean isLivingEntity(String owner) {
        return isLivingEntity(owner, null);
    }

    private boolean isLivingEntity(String owner, ClassNode classNode) {
        if (owner == null) return false;
        String normalized = owner.replace('.', '/');
        if (normalized.equals("net/minecraft/world/entity/LivingEntity")
                || normalized.equals("net/minecraft/world/entity/player/Player")
                || normalized.equals("net/minecraft/server/level/ServerPlayer")
                || normalized.equals("net/minecraft/client/player/LocalPlayer")
                || normalized.equals("net/minecraft/client/player/RemotePlayer")
                || normalized.equals("net/minecraft/world/entity/Mob")
                || normalized.equals("net/minecraft/world/entity/PathfinderMob")
                || normalized.equals("net/minecraft/world/entity/AgeableMob")
                || normalized.equals("net/minecraft/world/entity/TamableAnimal")
                || normalized.equals("net/minecraft/world/entity/monster/Monster")
                || normalized.equals("net/minecraft/world/entity/animal/Animal")
                || normalized.equals("net/minecraft/world/entity/ambient/AmbientCreature")
                || normalized.equals("net/minecraft/world/entity/FlyingMob")
                || normalized.equals("net/minecraft/entity/LivingEntity")
                || normalized.equals("net/minecraft/entity/EntityLivingBase")
                || normalized.equals("net/minecraft/entity/player/PlayerEntity")
                || normalized.equals("net/minecraft/entity/player/EntityPlayer")
                || normalized.equals("net/minecraft/entity/player/ServerPlayerEntity")
                || normalized.equals("net/minecraft/entity/player/EntityPlayerMP")
                || normalized.equals("net/minecraft/client/entity/player/ClientPlayerEntity")
                || normalized.equals("net/minecraft/entity/MobEntity")
                || normalized.equals("net/minecraft/entity/EntityLiving")) {
            return true;
        }
        if (normalized.startsWith("net/minecraft/world/entity/") && (
                normalized.contains("Player") || normalized.contains("Mob") || normalized.contains("Boss")
                || normalized.contains("Monster") || normalized.contains("Animal")
                || normalized.contains("monster") || normalized.contains("animal")
        )) {
            return true;
        }
        if (classNode != null && (normalized.equals(classNode.name) || normalized.equals(classNode.superName))) {
            if (classNode.superName != null && isLivingEntity(classNode.superName, null)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPolyfillRule(PolyfillRule pr, MethodInsnNode minsn, ClassNode classNode) {
        if (isLevelOrWorld(minsn.owner) && ("explode".equals(minsn.name) || "createExplosion".equals(minsn.name))) {
            return false;
        }
        if (pr.matches(minsn.owner, minsn.name, minsn.desc)) {
            return true;
        }
        // Direct owner match with remapped descriptor
        if (pr.getSourceOwner().equals(minsn.owner.replace('.', '/')) && pr.getSourceName().equals(minsn.name)) {
            String srcDesc = pr.getSourceDesc();
            if (srcDesc == null || srcDesc.equals(minsn.desc) || remapDescriptor(srcDesc).equals(minsn.desc)) {
                return true;
            }
        }
        // Subclass matching for LivingEntity
        if (isLivingEntity(pr.getSourceOwner(), null) && isLivingEntity(minsn.owner, classNode)) {
            if (pr.getSourceName().equals(minsn.name)) {
                String srcDesc = pr.getSourceDesc();
                if (srcDesc == null || srcDesc.equals(minsn.desc) || remapDescriptor(srcDesc).equals(minsn.desc)) {
                    return true;
                }
                Type[] ruleArgs = Type.getArgumentTypes(srcDesc);
                Type[] callArgs = Type.getArgumentTypes(minsn.desc);
                if (ruleArgs.length == callArgs.length) {
                    Type ruleRet = Type.getReturnType(srcDesc);
                    Type callRet = Type.getReturnType(minsn.desc);
                    if (ruleRet.getSort() == callRet.getSort()) {
                        return true;
                    }
                }
            }
        }
        // EnchantmentHelper static matching
        if (isEnchantmentHelper(pr.getSourceOwner()) && isEnchantmentHelper(minsn.owner)) {
            if (pr.getSourceName().equals(minsn.name)) {
                String srcDesc = pr.getSourceDesc();
                if (srcDesc == null || srcDesc.equals(minsn.desc) || remapDescriptor(srcDesc).equals(minsn.desc)) {
                    return true;
                }
                Type[] ruleArgs = Type.getArgumentTypes(srcDesc);
                Type[] callArgs = Type.getArgumentTypes(minsn.desc);
                if (ruleArgs.length == callArgs.length) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean injectModern26_3Bridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        boolean modified = false;

        // 1. BlockBehaviour bridges
        MethodNode legacyClone = null;
        boolean hasModernClone = false;

        MethodNode legacyNeighbor = null;
        boolean hasModernNeighbor = false;

        MethodNode legacyEntityInside = null;
        boolean hasModernEntityInside = false;

        // 2. Item bridges
        MethodNode legacyInventoryTick = null;
        boolean hasModernInventoryTick = false;

        MethodNode legacyUseDuration = null;
        boolean hasModernUseDuration = false;

        MethodNode legacyItemUse = null;
        boolean hasModernItemUse = false;

        for (MethodNode m : classNode.methods) {
            // BlockBehaviour: getCloneItemStack
            if ("getCloneItemStack".equals(m.name)) {
                if ("(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                    hasModernClone = true;
                } else if (m.desc != null && m.desc.endsWith(";Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;")) {
                    legacyClone = m;
                }
            }

            // BlockBehaviour: neighborChanged
            if ("neighborChanged".equals(m.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V".equals(m.desc)) {
                    hasModernNeighbor = true;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V".equals(m.desc)) {
                    legacyNeighbor = m;
                }
            }

            // BlockBehaviour: entityInside
            if ("entityInside".equals(m.name)) {
                if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V".equals(m.desc)) {
                    hasModernEntityInside = true;
                } else if ("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V".equals(m.desc)) {
                    legacyEntityInside = m;
                }
            }

            // Item: inventoryTick
            if ("inventoryTick".equals(m.name)) {
                if ("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V".equals(m.desc)) {
                    hasModernInventoryTick = true;
                } else if ("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V".equals(m.desc)) {
                    legacyInventoryTick = m;
                }
            }

            // Item: getUseDuration
            if ("getUseDuration".equals(m.name)) {
                if ("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I".equals(m.desc)) {
                    hasModernUseDuration = true;
                } else if ("(Lnet/minecraft/world/item/ItemStack;)I".equals(m.desc)) {
                    legacyUseDuration = m;
                }
            }

            // Item: use
            if ("use".equals(m.name)) {
                if ("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;".equals(m.desc)) {
                    hasModernItemUse = true;
                } else if ("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;".equals(m.desc)) {
                    legacyItemUse = m;
                }
            }
        }

        // 1. Inject BlockBehaviour getCloneItemStack
        if (legacyClone != null && !hasModernClone) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getCloneItemStack",
                    "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // LevelReader
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // BlockPos
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // BlockState
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyClone.name, legacyClone.desc, false));
            il.add(new InsnNode(Opcodes.ARETURN));
            bridge.maxStack = 4;
            bridge.maxLocals = 5;
            classNode.methods.add(bridge);
            modified = true;
        }

        // 2. Inject BlockBehaviour neighborChanged
        if (legacyNeighbor != null && !hasModernNeighbor) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "neighborChanged",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // neighborBlock

            LabelNode orientationNull = new LabelNode();
            LabelNode afterNeighborPos = new LabelNode();
            il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // orientation
            il.add(new JumpInsnNode(Opcodes.IFNULL, orientationNull));
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // orientation
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/redstone/Orientation", "getFront", "()Lnet/minecraft/core/Direction;", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "relative", "(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;", false));
            il.add(new JumpInsnNode(Opcodes.GOTO, afterNeighborPos));
            il.add(orientationNull);
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // fallback to pos
            il.add(afterNeighborPos);

            il.add(new VarInsnNode(Opcodes.ILOAD, 6)); // movedByPiston
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyNeighbor.name, legacyNeighbor.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            bridge.maxStack = 7;
            bridge.maxLocals = 7;
            classNode.methods.add(bridge);
            modified = true;
        }

        // 3. Inject BlockBehaviour entityInside
        if (legacyEntityInside != null && !hasModernEntityInside) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "entityInside",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // state
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // pos
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // entity
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyEntityInside.name, legacyEntityInside.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            bridge.maxStack = 5;
            bridge.maxLocals = 7;
            classNode.methods.add(bridge);
            modified = true;
        }

        // 4. Inject Item inventoryTick
        if (legacyInventoryTick != null && !hasModernInventoryTick) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "inventoryTick",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // stack
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // entity

            // slotId:
            LabelNode slotNull = new LabelNode();
            LabelNode afterSlot = new LabelNode();
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // slot
            il.add(new JumpInsnNode(Opcodes.IFNULL, slotNull));
            il.add(new VarInsnNode(Opcodes.ALOAD, 4));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/EquipmentSlot", "getIndex", "()I", false));
            il.add(new JumpInsnNode(Opcodes.GOTO, afterSlot));
            il.add(slotNull);
            il.add(new InsnNode(Opcodes.ICONST_0));
            il.add(afterSlot);

            // isSelected:
            LabelNode notMainHand = new LabelNode();
            LabelNode afterSelected = new LabelNode();
            il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // slot
            il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "MAINHAND", "Lnet/minecraft/world/entity/EquipmentSlot;"));
            il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, notMainHand));
            il.add(new InsnNode(Opcodes.ICONST_1));
            il.add(new JumpInsnNode(Opcodes.GOTO, afterSelected));
            il.add(notMainHand);
            il.add(new InsnNode(Opcodes.ICONST_0));
            il.add(afterSelected);

            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyInventoryTick.name, legacyInventoryTick.desc, false));
            il.add(new InsnNode(Opcodes.RETURN));
            bridge.maxStack = 6;
            bridge.maxLocals = 5;
            classNode.methods.add(bridge);
            modified = true;
        }

        // 5. Inject Item getUseDuration
        if (legacyUseDuration != null && !hasModernUseDuration) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "getUseDuration",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // stack
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyUseDuration.name, legacyUseDuration.desc, false));
            il.add(new InsnNode(Opcodes.IRETURN));
            bridge.maxStack = 2;
            bridge.maxLocals = 3;
            classNode.methods.add(bridge);
            modified = true;
        }

        // 6. Inject Item use
        if (legacyItemUse != null && !hasModernItemUse) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "use",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
                    null,
                    null
            );
            InsnList il = bridge.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // level
            il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // player
            il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // hand
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, legacyItemUse.name, legacyItemUse.desc, false));
            LabelNode nullHolder = new LabelNode();
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new JumpInsnNode(Opcodes.IFNULL, nullHolder));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/InteractionResultHolder", "getResult", "()Lnet/minecraft/world/InteractionResult;", false));
            il.add(new InsnNode(Opcodes.ARETURN));
            il.add(nullHolder);
            il.add(new InsnNode(Opcodes.POP));
            il.add(new InsnNode(Opcodes.ACONST_NULL));
            il.add(new InsnNode(Opcodes.ARETURN));
            bridge.maxStack = 4;
            bridge.maxLocals = 4;
            classNode.methods.add(bridge);
            modified = true;
        }

        return modified;
    }

    private boolean isAdvancementBuilder(String owner) {
        if (owner == null) return false;
        String normalized = owner.replace('.', '/');
        return normalized.equals("net/minecraft/advancements/Advancement$Builder")
                || normalized.equals("net/minecraft/advancements/Advancement$Task");
    }

    private boolean isAdvancementHolder(String owner) {
        if (owner == null) return false;
        String normalized = owner.replace('.', '/');
        return normalized.equals("net/minecraft/advancements/AdvancementHolder")
                || normalized.equals("net/minecraft/advancements/Advancement");
    }

    private boolean isEntitySubclass(ClassNode classNode) {
        if (classNode == null) return false;
        if (classNode.superName != null) {
            String superName = classNode.superName.replace('.', '/');
            if (superName.equals("net/minecraft/world/entity/Entity")
                    || superName.equals("net/minecraft/entity/Entity")
                    || isLivingEntity(superName, null)
                    || superName.startsWith("net/minecraft/world/entity/")) {
                return true;
            }
        }
        if (classNode.methods != null) {
            for (MethodNode m : classNode.methods) {
                if ("defineSynchedData".equals(m.name) && "()V".equals(m.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean injectModernEntitySyncedDataBridges(ClassNode classNode) {
        if (classNode.methods == null) return false;
        if (!isEntitySubclass(classNode)) return false;

        MethodNode legacyDefine = null;
        boolean hasModernDefine = false;

        for (MethodNode method : classNode.methods) {
            if ("defineSynchedData".equals(method.name)) {
                if ("()V".equals(method.desc)) {
                    legacyDefine = method;
                } else if ("(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V".equals(method.desc)) {
                    hasModernDefine = true;
                }
            }
        }

        if (legacyDefine != null && !hasModernDefine) {
            MethodNode bridge = new MethodNode(
                    Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "defineSynchedData",
                    "(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
                    null,
                    null
            );
            InsnList il = bridge.instructions;

            // 1. Call super.defineSynchedData(builder) if super != Object
            if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // builder
                il.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        classNode.superName,
                        "defineSynchedData",
                        "(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
                        false
                ));
            }

            // 2. Call EntityDataShim.pushBuilder(builder)
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/EntityDataShim",
                    "pushBuilder",
                    "(Ljava/lang/Object;)V",
                    false
            ));

            // 3. Call this.defineSynchedData()
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    classNode.name,
                    legacyDefine.name,
                    "()V",
                    false
            ));

            // 4. Call EntityDataShim.popBuilder()
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/EntityDataShim",
                    "popBuilder",
                    "()Ljava/lang/Object;",
                    false
            ));
            il.add(new InsnNode(Opcodes.POP));

            il.add(new InsnNode(Opcodes.RETURN));
            bridge.maxStack = 2;
            bridge.maxLocals = 2;
            classNode.methods.add(bridge);
            return true;
        }

        return false;
    }
}
