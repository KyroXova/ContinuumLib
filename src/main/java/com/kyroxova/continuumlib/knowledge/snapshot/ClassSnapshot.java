package com.kyroxova.continuumlib.knowledge.snapshot;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import org.objectweb.asm.Opcodes;
import java.util.*;

public record ClassSnapshot(
        String name,
        String superName,
        List<String> interfaces,
        int access,
        String signature,
        Map<String, MemberSnapshot> fields,
        Map<String, MemberSnapshot> methods,
        Map<String, MemberSnapshot> constructors
) implements Comparable<ClassSnapshot> {
    public ClassSnapshot {
        Objects.requireNonNull(name, "name");
        interfaces = List.copyOf(interfaces == null ? List.of() : interfaces);
        fields = Collections.unmodifiableMap(new TreeMap<>(fields == null ? Map.of() : fields));
        methods = Collections.unmodifiableMap(new TreeMap<>(methods == null ? Map.of() : methods));
        constructors = Collections.unmodifiableMap(new TreeMap<>(constructors == null ? Map.of() : constructors));
    }

    public static ClassSnapshot from(ClassInfo info) {
        Map<String, MemberSnapshot> fields = new TreeMap<>();
        for (var f : info.fields()) {
            fields.put(f.name() + ":" + f.descriptor(), MemberSnapshot.from(info.name(), f, MemberSnapshot.Kind.FIELD));
        }
        Map<String, MemberSnapshot> methods = new TreeMap<>();
        Map<String, MemberSnapshot> constructors = new TreeMap<>();
        for (var m : info.methods()) {
            if (m.name().equals("<init>")) {
                constructors.put(m.name() + ":" + m.descriptor(), MemberSnapshot.from(info.name(), m, MemberSnapshot.Kind.CONSTRUCTOR));
            } else if (!m.name().equals("<clinit>")) {
                methods.put(m.name() + ":" + m.descriptor(), MemberSnapshot.from(info.name(), m, MemberSnapshot.Kind.METHOD));
            }
        }
        return new ClassSnapshot(info.name(), info.superName(), info.interfaces(), info.access(), null, fields, methods, constructors);
    }

    public boolean isInterface() { return (access & Opcodes.ACC_INTERFACE) != 0; }
    public boolean isPublic() { return (access & Opcodes.ACC_PUBLIC) != 0; }
    public boolean isAbstract() { return (access & Opcodes.ACC_ABSTRACT) != 0; }

    @Override
    public int compareTo(ClassSnapshot o) {
        return name.compareTo(o.name);
    }
}
