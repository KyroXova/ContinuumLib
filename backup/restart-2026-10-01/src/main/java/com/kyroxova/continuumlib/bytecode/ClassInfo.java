package com.kyroxova.continuumlib.bytecode;

import java.util.List;

public record ClassInfo(String name, String superName, List<String> interfaces, int access,
                        List<Member> fields, List<Member> methods) {
    public ClassInfo {
        interfaces = List.copyOf(interfaces);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }
    public record Member(String name, String descriptor, int access, String signature) {}
}
