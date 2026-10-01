package com.kyroxova.continuumlib.source.rule;

import java.util.Objects;

public sealed interface SourceMigrationRule {
    record ClassRename(String sourceClass, String targetClass) implements SourceMigrationRule {
        public ClassRename {
            Objects.requireNonNull(sourceClass, "sourceClass");
            Objects.requireNonNull(targetClass, "targetClass");
        }
    }

    record MethodRename(String ownerClass, String sourceMethod, String targetMethod) implements SourceMigrationRule {
        public MethodRename {
            Objects.requireNonNull(ownerClass, "ownerClass");
            Objects.requireNonNull(sourceMethod, "sourceMethod");
            Objects.requireNonNull(targetMethod, "targetMethod");
        }
    }

    record ConstructorToFactory(String constructorOwner, String factoryOwner, String factoryMethod) implements SourceMigrationRule {
        public ConstructorToFactory {
            Objects.requireNonNull(constructorOwner, "constructorOwner");
            Objects.requireNonNull(factoryOwner, "factoryOwner");
            Objects.requireNonNull(factoryMethod, "factoryMethod");
        }
    }

    record FactoryToConstructor(String factoryOwner, String factoryMethod, String constructorOwner) implements SourceMigrationRule {
        public FactoryToConstructor {
            Objects.requireNonNull(factoryOwner, "factoryOwner");
            Objects.requireNonNull(factoryMethod, "factoryMethod");
            Objects.requireNonNull(constructorOwner, "constructorOwner");
        }
    }

    record FieldToAccessor(String ownerClass, String fieldName, String getterMethod) implements SourceMigrationRule {
        public FieldToAccessor {
            Objects.requireNonNull(ownerClass, "ownerClass");
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(getterMethod, "getterMethod");
        }
    }
}
