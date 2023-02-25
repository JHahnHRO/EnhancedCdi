package io.github.jhahnhro.enhancedcdi.types;

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof GenericArrayType that && Objects.equals(this.genericComponentType,
                                                                    that.getGenericComponentType());
    }

    @Override
    public int hashCode() {
        return genericComponentType.hashCode();
    }

    @Override
    public String toString() {
        return genericComponentType.getTypeName() + "[]";
    }
}
