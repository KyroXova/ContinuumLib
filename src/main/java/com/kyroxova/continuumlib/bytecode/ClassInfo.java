package com.kyroxova.continuumlib.bytecode;

import java.util.List;

public record ClassInfo(String name, String superName, List<String> interfaces, int access,
                        List<Member> fields, List<Member> methods, String nestHost, List<String> nestMembers) {
    public ClassInfo(String name, String superName, List<String> interfaces, int access,
                     List<Member> fields, List<Member> methods) {
        this(name, superName, interfaces, access, fields, methods, null, List.of());
    }
    public ClassInfo {
        interfaces = List.copyOf(interfaces);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        nestMembers = nestMembers == null ? List.of() : List.copyOf(nestMembers);
    }
    public record Member(String name, String descriptor, int access, String signature) {}
}
