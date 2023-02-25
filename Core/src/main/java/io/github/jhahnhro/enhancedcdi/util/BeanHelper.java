package io.github.jhahnhro.enhancedcdi.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

/**
 * Helps with the lack of {@code select(Type, Annotation...)} in {@link Instance}
 */
@Dependent
public class BeanHelper {
    @Inject
    BeanManager beanManager;

    private final Collection<BeanInstance<?>> dependentInstances = new ConcurrentLinkedQueue<>();

    /**
     * Unchecked variant of {@link Instance#select(javax.enterprise.util.TypeLiteral, Annotation...)}. Just like
     * {@code Instance.select}, instances of {@link Dependent} beans will be automatically destroyed when this
     * {@link BeanHelper} instance gets destroyed.
     *
     * @param type       type to select
     * @param qualifiers qualifiers to select
     * @param <T>        type to select
     * @return the contextual instance of the bean with the given type and qualifiers
     */
    public <T> T select(final Type type, final Annotation... qualifiers) {
        final List<BeanInstance<T>> beanInstances = this.<T>getBeanInstanceStream(type, qualifiers).toList();

        if (beanInstances.isEmpty()) {
            throw new UnsatisfiedResolutionException();
        } else if (beanInstances.size() > 1) {
            final String errMsg = beanInstances.stream()
                    .map(BeanInstance::contextual)
                    .map(Object::toString)
                    .collect(Collectors.joining("\n\t-",
                                                "Multiple beans match the qualifiers %s and type %s:".formatted(
                                                        qualifiers, type), ""));
            throw new AmbiguousResolutionException(errMsg);
        }
        final BeanInstance<T> beanInstance = beanInstances.get(0);
        if (beanInstance.contextual() instanceof Bean<T> bean && bean.getScope() == Dependent.class) {
            dependentInstances.add(beanInstance);
        }

        return beanInstance.instance();
    }

    @PreDestroy
    void destroyAllDependents() {
        dependentInstances.forEach(BeanInstance::destroy);
    }


    /**
     * Selects all beans with the given type and qualifiers. Returns a stream of
     * {@link BeanInstance#createContextualReference(BeanManager, Bean, Type) contextual BeanInstances} for these beans
     * similar to {@link Instance#stream()}. The returned stream is "safe" in the sense that all instances of all
     * {@link Dependent} bean will be automatically destroyed when the stream is closed so that no memory leak occurs.
     *
     * @param type       a type to select
     * @param qualifiers qualifiers to select
     * @param <T>        the type to select
     * @return a safe stream of {@link BeanInstance}s of all beans with the given type and qualifiers.
     * @apiNote you should use the returned stream in a try-with-resources block, e.g.
     *
     * <pre>{@code
     * try(var beanInstanceStream = beanHelper.safeStream(Foo.class, barQualifier){
     *     Optional<Foo> foo = beanInstanceStream.map(BeanInstance::instance).sorted(myFooComparator).findFirst();
     *     // do something with the first foo instance
     * }
     * // all dependents are now destroyed, even the ones that were not used after the findFirst() call
     * }</pre>
     */
    public <T> Stream<BeanInstance<T>> safeStream(Type type, Annotation... qualifiers) {
        return makeStreamSafe(getBeanInstanceStream(type, qualifiers));
    }

    @SuppressWarnings("unchecked")
    private <T> Stream<BeanInstance<T>> getBeanInstanceStream(Type type, Annotation... qualifiers) {
        return beanManager.getBeans(type, qualifiers)
                .stream()
                .map(bean -> (Bean<T>) bean)
                .map(bean -> BeanInstance.createContextualReference(beanManager, bean, type));
    }

    private <T> Stream<BeanInstance<T>> makeStreamSafe(final Stream<BeanInstance<T>> beanInstanceStream) {
        final Collection<BeanInstance<T>> mustBeDestroyed = new ConcurrentLinkedQueue<>();

        // IntelliJ suggests replacing the call to Stream#map with a call Stream#peek(), but the latter is only
        // for debugging and not even guaranteed to be executed with every element of the stream. Therefore, we still
        // map here.
        //noinspection SimplifyStreamApiCallChains
        return beanInstanceStream.map(beanInstance -> {
            if (beanInstance.contextual() instanceof Bean<?> bean && bean.getScope() == Dependent.class) {
                mustBeDestroyed.add(beanInstance);
            }
            return beanInstance;
        }).onClose(() -> mustBeDestroyed.forEach(BeanInstance::destroy));
    }
}