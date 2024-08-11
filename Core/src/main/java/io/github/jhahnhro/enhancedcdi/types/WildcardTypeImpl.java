package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

public record WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) implements WildcardType {

    public WildcardTypeImpl {
        upperBounds = Arrays.copyOf(upperBounds, upperBounds.length); // implicit NPE
        lowerBounds = Arrays.copyOf(lowerBounds, lowerBounds.length); // implicit NPE
        if (lowerBounds.length > 0 && (upperBounds.length != 1 || !Object.class.equals(upperBounds[0]))) {
            throw new IllegalArgumentException(
                    "If there is a lower bound on a type variable, the upper bound must be {Object.class}");
        }
    }

    public static WildcardTypeImpl ofUpperBounds(Type... upperBounds) {
        return new WildcardTypeImpl(upperBounds, new Type[0]);
    }

    public static WildcardTypeImpl ofLowerBound(Type lowerBound) {
        return new WildcardTypeImpl(new Type[]{Object.class}, new Type[]{lowerBound});
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
        return o instanceof WildcardType that && Arrays.equals(this.lowerBounds, that.getLowerBounds())
               && Arrays.equals(this.upperBounds, that.getUpperBounds());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
    }
}
