package io.github.jhahnhro.enhancedcdi.multiton.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

class Factory<X, Y> {
    final ReflectiveCall<X, Y> call;
    final String desc;

    Factory (ReflectiveCall<X, Y> constructorOrFactoryMethod, String desc) {
        this.call = Objects.requireNonNull(constructorOrFactoryMethod);
        this.desc = Objects.requireNonNull(desc);
    }

    @FunctionalInterface
    interface ReflectiveCall<X, Y> {
        Y apply(X x) throws InvocationTargetException;
    }
}
