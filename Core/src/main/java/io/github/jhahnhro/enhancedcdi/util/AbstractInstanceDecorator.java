package io.github.jhahnhro.enhancedcdi.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;

public abstract class AbstractInstanceDecorator<T> implements Instance<T> {

    protected final Instance<T> delegate;
    protected final InjectionPoint injectionPoint;

    protected AbstractInstanceDecorator(Instance<T> delegate, InjectionPoint injectionPoint) {
        this.delegate = delegate;
        this.injectionPoint = injectionPoint;
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        return decorate(delegate.select(qualifiers), createNewInjectionPoint(injectionPoint.getType(), qualifiers));
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return decorate(delegate.select(subtype, qualifiers), createNewInjectionPoint(subtype, qualifiers));
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return decorate(delegate.select(subtype, qualifiers), createNewInjectionPoint(subtype.getType(), qualifiers));
    }

    protected InjectionPoint createNewInjectionPoint(Type type, Annotation... additionalQualifiers) {
        Set<Annotation> newQualifiers = new HashSet<>(injectionPoint.getQualifiers());
        Collections.addAll(newQualifiers, additionalQualifiers);
        return InjectionPointImpl.mutate(injectionPoint, type, newQualifiers);
    }

    protected abstract <U extends T> AbstractInstanceDecorator<U> decorate(Instance<U> delegate,
                                                                           InjectionPoint newInjectionPoint);

    @Override
    public boolean isUnsatisfied() {
        return delegate.isUnsatisfied();
    }

    @Override
    public boolean isAmbiguous() {
        return delegate.isAmbiguous();
    }

    @Override
    public void destroy(T instance) {
        delegate.destroy(instance);
    }


    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public Stream<T> stream() {
        return delegate.stream();
    }

    @Override
    public Handle<T> getHandle() {
        return delegate.getHandle();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        return delegate.handles();
    }

    @Override
    public Stream<? extends Handle<T>> handlesStream() {
        return delegate.handlesStream();
    }
}
