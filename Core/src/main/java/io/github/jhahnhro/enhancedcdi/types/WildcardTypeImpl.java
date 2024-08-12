package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public record WildcardTypeImpl(List<Type> upperBounds, List<Type> lowerBounds) implements WildcardType {

    public WildcardTypeImpl {
        upperBounds = List.copyOf(upperBounds);
        lowerBounds = List.copyOf(lowerBounds);
        if (!lowerBounds.isEmpty() && (upperBounds.size() != 1 || !Object.class.equals(upperBounds.getFirst()))) {
            throw new IllegalArgumentException(
                    "If there is a lower bound on a type variable, the upper bound must be {Object.class}");
        }
    }

    public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
        this(Arrays.asList(upperBounds), Arrays.asList(lowerBounds));
    }

    public static WildcardTypeImpl ofUpperBounds(Type... upperBounds) {
        return new WildcardTypeImpl(Arrays.asList(upperBounds), List.of());
    }

    public static WildcardTypeImpl ofLowerBound(Type lowerBound) {
        return new WildcardTypeImpl(List.of(Object.class), List.of(lowerBound));
    }

    @Override
    public Type[] getUpperBounds() {
        return upperBounds.toArray(new Type[0]);
    }

    @Override
    public Type[] getLowerBounds() {
        return lowerBounds.toArray(new Type[0]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WildcardType that && this.lowerBounds.equals(List.of(that.getLowerBounds()))
               && this.upperBounds.equals(List.of(that.getUpperBounds()));
    }

    @Override
    public int hashCode() {
        // not Objects.hash(...) in order to be consistent with the JDK's and Weld's implementation
        // List.hashCode happens to give the same result as Arrays.hashCode that the JDK uses.
        return lowerBounds.hashCode() ^ upperBounds.hashCode();
    }
}
