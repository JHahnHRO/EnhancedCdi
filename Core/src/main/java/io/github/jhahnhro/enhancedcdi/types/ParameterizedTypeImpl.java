package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

public class ParameterizedTypeImpl implements ParameterizedType {

    private final Class<?> rawType;
    private final Type ownerType;
    private final Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type... actualTypeArguments) {
        if ((rawType.getDeclaringClass() == null) ^ (ownerType == null)) {
            throw new IllegalArgumentException("ownerType must be present iff the rawType is declared by another class");
        }
        if (rawType.getTypeParameters().length != actualTypeArguments.length) {
            throw new IllegalArgumentException("wrong number of type parameters");
        }
        this.rawType = rawType;
        this.ownerType = ownerType;
        this.actualTypeArguments = Arrays.copyOf(actualTypeArguments, actualTypeArguments.length);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return Arrays.copyOf(actualTypeArguments, actualTypeArguments.length);
    }

    @Override
    public Class<?> getRawType() {
        return rawType;
    }

    @Override
    public Type getOwnerType() {
        return ownerType;
    }


    @Override
    public int hashCode() {
        // not Objects.hash(...) in order to be consistent with Weld's implementation
        return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof ParameterizedType that && Objects.equals(this.ownerType, that.getOwnerType())
               && Objects.equals(this.rawType, that.getRawType()) && Arrays.equals(this.actualTypeArguments,
                                                                                   that.getActualTypeArguments());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ownerType != null) {
            sb.append(ownerType).append("$");
        }
        sb.append(rawType);
        if (actualTypeArguments.length > 0) {
            sb.append("<");
            for (Type actualType : actualTypeArguments) {
                sb.append(actualType);
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append(">");
        }
        return sb.toString();
    }
}
