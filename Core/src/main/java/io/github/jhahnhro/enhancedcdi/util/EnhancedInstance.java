package io.github.jhahnhro.enhancedcdi.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.metadata.MutableInjectionPoint;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link Instance}
 */
@Dependent
@Typed(EnhancedInstance.class) // prevent this bean from clashing with the built-in bean.
public final class EnhancedInstance<T> implements Instance<T> {
    private final BeanManager beanManager;
    private final InjectionPoint originalInjectionPoint;
    private final InjectionPoint currentInjectionPoint;
    private final Set<BeanInstance<T>> dependents;
    private final Set<Bean<?>> matchingBeans;

    //region constructors

    /**
     * Bean c'tor.
     *
     * @param beanManager    beanManager
     * @param injectionPoint the injection point
     */
    @Inject
    EnhancedInstance(BeanManager beanManager, InjectionPoint injectionPoint) {
        this(beanManager, injectionPoint, fakeFromOriginal(injectionPoint), ConcurrentHashMap.newKeySet());
    }

    /**
     * Full c'tor.
     *
     * @param beanManager            beanManager
     * @param originalInjectionPoint the original InjectionPoint of type {@code EnhancedInstance<T>}
     * @param currentInjectionPoint  the fake InjectionPoint of type {@code T} and possibly with additional qualifiers
     * @param dependents             all dependent objects that were generated by this {@code EnhancedInstance}
     */
    private EnhancedInstance(BeanManager beanManager, InjectionPoint originalInjectionPoint,
                             InjectionPoint currentInjectionPoint, Set<BeanInstance<T>> dependents) {
        this.beanManager = beanManager;
        this.originalInjectionPoint = originalInjectionPoint;
        this.currentInjectionPoint = currentInjectionPoint;
        this.dependents = dependents;

        this.matchingBeans = beanManager.getBeans(currentInjectionPoint.getType(), qualifiersAsArray());
    }

    private static InjectionPoint fakeFromOriginal(InjectionPoint injectionPoint) {
        final Type typeT = ((ParameterizedType) injectionPoint.getType()).getActualTypeArguments()[0];
        return mutate(injectionPoint, typeT);
    }

    private static InjectionPoint mutate(InjectionPoint injectionPoint, Type newType,
                                         Annotation... additionalQualifiers) {
        return new MutableInjectionPoint(injectionPoint).setType(newType).addQualifiers(additionalQualifiers);
    }
    //endregion

    //region select
    @Override
    public EnhancedInstance<T> select(Annotation... qualifiers) {
        return select(this.currentInjectionPoint.getType(), qualifiers);
    }

    @Override
    public <U extends T> EnhancedInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return select((Type) subtype, qualifiers);
    }

    @Override
    public <U extends T> EnhancedInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return select(subtype.getType(), qualifiers);
    }

    /**
     * Explicitly unchecked version of the other select methods.
     *
     * @param subtype    the required type
     * @param qualifiers the additional required qualifiers
     * @param <U>        the required type
     * @return a child instance.
     */
    @SuppressWarnings("unchecked")
    public <U extends T> EnhancedInstance<U> select(Type subtype, Annotation... qualifiers) {
        final EnhancedInstance<T> result;
        if (subtype.equals(currentInjectionPoint.getType()) && qualifiers.length == 0) {
            result = this;
        } else {
            result = new EnhancedInstance<>(beanManager, originalInjectionPoint,
                                            mutate(currentInjectionPoint, subtype, qualifiers), dependents);
        }
        return (EnhancedInstance<U>) result;
    }
    //endregion

    //region resolution
    @Override
    public boolean isUnsatisfied() {
        return matchingBeans.isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
        try {
            beanManager.resolve(matchingBeans);
            return false;
        } catch (AmbiguousResolutionException e) {
            return true;
        }
    }
    //endregion

    public T get() {
        final BeanInstance<T> beanInstance = BeanInstance.createInjectableReference(beanManager, currentInjectionPoint);
        if (beanInstance.isDependentBean()) {
            this.dependents.add(beanInstance);
        }
        return beanInstance.instance();
    }

    //region destroy
    @Override
    public void destroy(T instance) {
        boolean wasDependentInstance = this.dependents.removeIf(beanInstance -> beanInstance.destroy(instance));

        if (!wasDependentInstance) {
            // if it is a not a @Dependent instance that was created here, then it is probably a client proxy, and
            // we can use the default Instance to destroy it.
            beanManager.createInstance().destroy(instance);
        }
    }
    //endregion

    @PreDestroy
    void destroyAllDependents() {
        this.dependents.forEach(BeanInstance::destroy);
    }

    @Override
    public Iterator<T> iterator() {
        return safeStream().map(BeanInstance::instance).iterator();
    }

    @Override
    public Stream<T> stream() {
        return safeStream().map(BeanInstance::instance);
    }

    /**
     * Returns a stream of
     * {@link BeanInstance#createContextualReference(BeanManager, Bean, Type) contextual BeanInstances} for these beans
     * similar to {@link Instance#stream()}. The returned stream is "safe" in the sense that all instances of all
     * {@link Dependent} bean will be automatically destroyed when the stream is {@link Stream#close() closed} so that
     * no memory leak occurs.
     *
     * @return a safe stream of {@link BeanInstance}s of all beans with the given type and qualifiers.
     * @apiNote you should use the returned stream in a try-with-resources block, e.g.
     *
     * <pre>{@code
     * try(var beanInstanceStream = enhancedInstance.safeStream(Foo.class, barQualifier){
     *     Optional<Foo> foo = beanInstanceStream.map(BeanInstance::instance).sorted(myFooComparator).findFirst();
     *     // do something with the first foo instance
     * }
     * // all dependents are now destroyed, even the ones that were not used after the findFirst() call
     * }</pre>
     */
    public Stream<BeanInstance<T>> safeStream() {
        return makeStreamSafe(getBeanInstanceStream());
    }

    @SuppressWarnings("unchecked")
    private Stream<BeanInstance<T>> getBeanInstanceStream() {
        Type type = currentInjectionPoint.getType();
        return matchingBeans.stream()
                .map(bean -> (Bean<T>) bean)
                .map(bean -> BeanInstance.createContextualReference(beanManager, bean, type));
    }

    private Stream<BeanInstance<T>> makeStreamSafe(final Stream<BeanInstance<T>> beanInstanceStream) {
        final Collection<BeanInstance<T>> mustBeDestroyed = ConcurrentHashMap.newKeySet();

        // IntelliJ suggests replacing the call to Stream#map with a call Stream#peek(), but the latter is only
        // for debugging and not even guaranteed to be executed with every element of the stream. Therefore, we still
        // map here.
        //noinspection SimplifyStreamApiCallChains
        return beanInstanceStream.map(beanInstance -> {
            if (beanInstance.isDependentBean()) {
                mustBeDestroyed.add(beanInstance);
                dependents.add(beanInstance);
            }
            return beanInstance;
        }).onClose(() -> {
            dependents.removeAll(mustBeDestroyed);
            mustBeDestroyed.forEach(BeanInstance::destroy);
        });
    }

    private Annotation[] qualifiersAsArray() {
        return this.currentInjectionPoint.getQualifiers().toArray(Annotation[]::new);
    }
}