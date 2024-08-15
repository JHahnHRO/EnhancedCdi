package io.github.jhahnhro.enhancedcdi.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.metadata.InjectionPointImpl;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link Instance}
 */
@Dependent
@Typed(EnhancedInstance.class) // prevent this bean from clashing with the built-in bean of type Instance<T>
public final class EnhancedInstance<T> extends AbstractInstanceDecorator<T> {

    private final BeanManager beanContainer;
    private final Predicate<Handle<T>> handleFilter;


    //region constructors

    /**
     * Bean c'tor.
     *
     * @param bm                             beanContainer
     * @param enhancedInstanceInjectionPoint the injection point
     */
    @Inject
    EnhancedInstance(BeanManager bm, InjectionPoint enhancedInstanceInjectionPoint) {
        this(bm, unpackOriginal(enhancedInstanceInjectionPoint), true);
    }

    private EnhancedInstance(BeanManager bm, InjectionPoint injectionPoint, boolean ignored) {
        this(bm, createDelegate(bm, injectionPoint), injectionPoint);
    }

    private EnhancedInstance(BeanManager bm, Instance<T> delegate, InjectionPoint injectionPoint) {
        super(delegate, injectionPoint);
        this.beanContainer = bm;
        this.handleFilter = h -> bm.isMatchingBean(h.getBean().getTypes(), h.getBean().getQualifiers(),
                                                   injectionPoint.getType(), injectionPoint.getQualifiers());
    }

    /**
     * Unpacks the original {@code EnhancedInstance<T>} injection point to a {@code T} injection point.
     */
    private static InjectionPoint unpackOriginal(InjectionPoint injectionPoint) {
        final Type requiredType = ((ParameterizedType) injectionPoint.getType()).getActualTypeArguments()[0];
        return InjectionPointImpl.mutate(injectionPoint, requiredType, injectionPoint.getQualifiers());
    }

    private static <T> Instance<T> createDelegate(BeanManager bm, InjectionPoint ip) {
        Type instanceOfType = new ParameterizedTypeImpl(Instance.class, null, ip.getType());
        InjectionPoint syntheticInjectionPoint = InjectionPointImpl.mutate(ip, instanceOfType, ip.getQualifiers());
        //noinspection unchecked
        return (Instance<T>) bm.getInjectableReference(syntheticInjectionPoint, bm.createCreationalContext(null));
    }
    //endregion

    //region select
    @Override
    public EnhancedInstance<T> select(Annotation... qualifiers) {
        return (EnhancedInstance<T>) super.select(qualifiers);
    }

    @Override
    public <U extends T> EnhancedInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return (EnhancedInstance<U>) super.select(subtype, qualifiers);
    }

    @Override
    public <U extends T> EnhancedInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return (EnhancedInstance<U>) super.select(subtype, qualifiers);
    }

    /**
     * Obtains a child <code>Instance</code> for the given required type and additional required qualifiers.
     *
     * @param <U>        the required type
     * @param subtype    a {@link Type} representing the required type
     * @param qualifiers the additional required qualifiers
     * @return the child {@code Instance}
     * @throws IllegalArgumentException if the type is not a subtype of the current event type or if the qualifiers
     *                                  contain two instances of the same non-repeating qualifier type, or an instance
     *                                  of an annotation that is not a qualifier type
     * @throws IllegalStateException    if the container is already shutdown
     */
    @SuppressWarnings("unchecked")
    public <U extends T> EnhancedInstance<U> selectUnchecked(Type subtype, Annotation... qualifiers) {
        Set<Type> types = TypeVariableResolver.withKnownTypesOf(subtype).resolvedTypeClosure(subtype);
        if (!types.contains(this.injectionPoint.getType())) {
            throw new IllegalArgumentException(subtype + " is not a subtype of " + this.injectionPoint.getType());
        }

        return (EnhancedInstance<U>) decorate(delegate.select(qualifiers),
                                              createNewInjectionPoint(subtype, qualifiers));
    }

    @Override
    protected <U extends T> EnhancedInstance<U> decorate(Instance<U> delegate, InjectionPoint newInjectionPoint) {
        return new EnhancedInstance<>(beanContainer, delegate, newInjectionPoint);
    }
    //endregion

    //region resolution
    @Override
    public boolean isUnsatisfied() {
        return super.isUnsatisfied() // short-circuit if the delegate already knows it's empty
               || this.handlesStream().findAny().isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
        return super.isAmbiguous() // short-circuit if the delegate already knows it's non-ambiguous
               && this.handlesStream().limit(2).count() > 1;
    }
    //endregion

    //region get, iterator, stream for instance(s)
    @Override
    public T get() {
        return this.getHandle().get();
    }

    @Override
    public Iterator<T> iterator() {
        return this.stream().iterator();
    }

    /**
     * Returns a stream of the bean instances. The returned stream is "safe" in the sense that all instances of all
     * {@link Dependent} bean will be automatically destroyed when the stream is {@link Stream#close() closed} so that
     * no memory leak occurs.
     *
     * @return a safe stream of instances of all beans with the given type and qualifiers.
     * @apiNote you should use the returned stream in a try-with-resources block, e.g.
     *
     * <pre>{@code
     * try(var stream = enhancedInstance.stream()){
     *     Optional<Foo> foo = stream.map(BeanInstance::instance).sorted(myFooComparator).findFirst();
     *     // do something with the first foo instance
     * }
     * // all dependents are now destroyed, even the ones that were not used after the findFirst() call
     * }</pre>
     */
    @Override
    public Stream<T> stream() {
        return this.handlesStream().map(Handle::get);
    }
    //endregion

    //region get, iterator, stream for handle(s)
    @Override
    public Handle<T> getHandle() {
        return this.handlesStream().limit(2).reduce((o1, o2) -> {
            throw new AmbiguousResolutionException();
        }).orElseThrow(UnsatisfiedResolutionException::new);
    }

    @Override
    public Iterable<Handle<T>> handles() {
        return () -> this.handlesStream().iterator();
    }

    /**
     * Returns a stream of {@link Instance.Handle}s. The returned stream is "safe" in the sense that all instances of
     * all {@link Dependent} bean will be automatically destroyed when the stream is {@link Stream#close() closed} so
     * that no memory leak occurs.
     *
     * @return a safe stream of {@link Instance.Handle}s of all beans with the given type and qualifiers.
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
    @Override
    public Stream<Handle<T>> handlesStream() {
        final Stream<Handle<T>> handlesStream = (Stream<Handle<T>>) delegate.handlesStream().filter(handleFilter);
        return makeSafe(handlesStream);
    }

    private Stream<Handle<T>> makeSafe(final Stream<Handle<T>> stream) {
        final Set<Handle<T>> dependents = new HashSet<>();

        // IntelliJ suggests replacing the call to Stream#map with a call Stream#peek(), but the latter is only
        // for debugging and not even guaranteed to be executed with every element of the stream. Therefore, we still
        // map here.
        //noinspection SimplifyStreamApiCallChains
        return stream.map(h -> {
            if (h.getBean().getScope() == Dependent.class) {
                dependents.add(h);
            }
            return h;
        }).onClose(() -> dependents.forEach(Handle::destroy));
    }
    //endregion
}
