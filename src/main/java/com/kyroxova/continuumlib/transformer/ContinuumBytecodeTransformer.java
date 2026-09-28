package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
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

    private final Map<String, String> classRedirects = new HashMap<>();
    private final List<MethodRedirectRule> methodRedirects = new ArrayList<>();
    private final List<FieldRedirectRule> fieldRedirects = new ArrayList<>();
    private final List<PolyfillRule> polyfillRules = new ArrayList<>();

    public ContinuumBytecodeTransformer(ApiKnowledgeBase knowledgeBase, TargetSpec baseSpec, TargetSpec targetSpec) {
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
}
