package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Objects;

public class WildcardTypeImpl implements WildcardType {
    private final Type[] upperBounds;
    private final Type[] lowerBounds;

    public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
        Objects.requireNonNull(upperBounds);
        Objects.requireNonNull(lowerBounds);
        this.upperBounds = Arrays.copyOf(upperBounds, upperBounds.length);
        this.lowerBounds = Arrays.copyOf(lowerBounds, lowerBounds.length);
    }

    @Override
    public Type[] getUpperBounds() {
        return Arrays.copyOf(upperBounds, upperBounds.length);
    }

    @Override
    public Type[] getLowerBounds() {
        return Arrays.copyOf(lowerBounds, lowerBounds.length);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WildcardType that && Arrays.equals(this.getLowerBounds(), that.getLowerBounds())
               && Arrays.equals(this.getUpperBounds(), that.getUpperBounds());
    }

    @Override
    public int hashCode() {
        Type[] lowerBounds = getLowerBounds();
        Type[] upperBounds = getUpperBounds();

        return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
    }
}
