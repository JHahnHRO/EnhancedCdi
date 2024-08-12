package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public record ParameterizedTypeImpl(Class<?> rawType, Type ownerType, List<Type> actualTypeArguments)
        implements ParameterizedType {

    public ParameterizedTypeImpl {
        // implicit NPE if rawType == null
        if ((rawType.getDeclaringClass() == null) ^ (ownerType == null)) {
            throw new IllegalArgumentException(
                    "ownerType must be present iff the rawType is declared by another class");
        }
        // implicit NPE if actualTypeArguments == null
        if (rawType.getTypeParameters().length != actualTypeArguments.size()) {
            throw new IllegalArgumentException("wrong number of type parameters");
        }
        actualTypeArguments = List.copyOf(actualTypeArguments);
    }

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type... actualTypeArguments) {
        this(rawType, ownerType, List.of(actualTypeArguments));
    }

    @Override
    public Type[] getActualTypeArguments() {
        return actualTypeArguments.toArray(Type[]::new);
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

        return actualTypeArguments.hashCode() // happens to be equal to Arrays.hashCode which Weld uses
               ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof ParameterizedType that && Objects.equals(this.ownerType, that.getOwnerType())
               && Objects.equals(this.rawType, that.getRawType()) && Objects.equals(this.actualTypeArguments,
                                                                                    List.of(that.getActualTypeArguments()));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ownerType != null) {
            sb.append(ownerType).append("$");
        }
        sb.append(rawType);
        if (!actualTypeArguments.isEmpty()) {
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
