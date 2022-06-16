package io.github.jhahnhro.enhancedcdi.reflection;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Objects;

public record GenericArrayTypeImpl(Type genericComponentType) implements GenericArrayType {
    public GenericArrayTypeImpl(Type genericComponentType) {
        this.genericComponentType = Objects.requireNonNull(genericComponentType);
    }

    @Override
    public Type getGenericComponentType() {
        return genericComponentType;
    }

    @Override
    public String toString() {
        return genericComponentType + "[]";
    }
}
