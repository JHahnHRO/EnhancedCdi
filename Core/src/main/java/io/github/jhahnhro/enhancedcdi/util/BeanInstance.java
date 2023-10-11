package io.github.jhahnhro.enhancedcdi.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.function.Supplier;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Convenience object encapsulating a bean, one of its instances and the {@link CreationalContext} in which that
 * instance was created.
 * <p>
 * According to the CDI Spec a bean has:
 * <ol>
 *     <li><i>contextual instances</i>, i.e. the "real" instance(s) of the bean. There can be any number of them
 *     depending on the bean's scope:
 *     <ul>
 *         <li>none if the scope's context is not active (i.e. a
 *         {@link javax.enterprise.context.ContextNotActiveException} could be thrown)</li>
 *         <li>exactly one for {@code @ApplicationScoped} and {@code @Singleton} beans,</li>
 *         <li>as many as there are requests for {@code @RequestScoped} beans,</li>
 *         <li>a potentially unlimited amount for {@code @Dependent} scoped beans</li>
 *         <li>...</li>
 *     </ul>
 *     Calling methods on the contextual instance(s) directly calls the "real" method in the bean class, i.e.
 *     interceptors and decorators are not applied.</li>
 *     <li><i>contextual references</i> which are usually client proxies wrapped around the contextual instances. For
 *     all normal scoped beans, they are always client proxies, for {@code @Dependent} scoped beans they may be
 *     proxies or not. The client proxies also include the interceptor chain if necessary, i.e. calling methods on a
 *     contextual reference goes through the complete interceptor and decorator chain of the bean before arriving at
 *     the "real", i.e. the contextual instance.</li>
 *     <li><i>injectable references</i> for each injection point that resolves to the bean. These are also usually
 *     client proxies.</li>
 * </ol>
 */
public final class BeanInstance<T> {
    private final Supplier<T> instanceSupplier;
    private final Contextual<T> contextual;
    private final CreationalContext<T> context;
    private T instance;
    private State state;

    private BeanInstance(Supplier<T> instanceSupplier, Contextual<T> contextual, CreationalContext<T> context) {
        this.instanceSupplier = instanceSupplier;
        this.contextual = contextual;
        this.context = context;
        this.instance = null;
        this.state = State.NOT_INITIALIZED;
    }

    public static <T> BeanInstance<T> createContextualInstance(Contextual<T> contextual, CreationalContext<T> context) {
        return new BeanInstance<>(() -> contextual.create(context), contextual, context);
    }

    public static <T> BeanInstance<T> createContextualReference(BeanManager beanManager, Bean<T> bean, Type beanType) {
        final CreationalContext<T> ctx = beanManager.createCreationalContext(bean);
        return new BeanInstance<>(() -> (T) beanManager.getReference(bean, beanType, ctx), bean, ctx);
    }

    public static <T> BeanInstance<T> createInjectableReference(BeanManager beanManager,
                                                                InjectionPoint injectionPoint) {
        final Annotation[] qualifiers = injectionPoint.getQualifiers().toArray(Annotation[]::new);
        final Set<Bean<?>> beans = beanManager.getBeans(injectionPoint.getType(), qualifiers);
        if (beans.isEmpty()) {
            throw new UnsatisfiedResolutionException("No bean is eligible for injection into " + injectionPoint);
        }
        final Bean<T> resolvedBean = (Bean<T>) beanManager.resolve(beans);
        final CreationalContext<T> ctx = beanManager.createCreationalContext(resolvedBean);
        return new BeanInstance<>(() -> (T) beanManager.getInjectableReference(injectionPoint, ctx), resolvedBean, ctx);
    }

    public synchronized void destroy() {
        if (state == State.INITIALIZED) {
            contextual.destroy(instance, context);
            instance = null;
            state = State.DESTROYED;
        }
    }

    /**
     * Destroys this instance iff it contains the given object.
     *
     * @param instance some instance of {@code T} that might be contained in this {@code BeanInstance} and will be
     *                 destroyed if it is.
     * @return {@code true} iff the given object was contained in this {@code BeanInstance} and has been destroyed.
     */
    synchronized boolean destroy(T instance) {
        if (state == State.INITIALIZED && this.instance == instance) {
            this.destroy();
            return true;
        } else {
            return false;
        }
    }

    public boolean isDependentBean() {
        return this.contextual instanceof Bean<?> bean && bean.getScope() == Dependent.class;
    }

    public synchronized T instance() {
        return switch (state) {
            case NOT_INITIALIZED -> {
                state = State.INITIALIZED;
                instance = instanceSupplier.get();
                yield instance;
            }
            case INITIALIZED -> instance;
            case DESTROYED -> throw new IllegalStateException("Already destroyed");
        };
    }

    public Contextual<T> contextual() {return contextual;}

    public CreationalContext<T> context() {return context;}

    /**
     * @return current state of this instance
     */
    public synchronized State state() {
        return state;
    }

    public enum State {
        NOT_INITIALIZED, INITIALIZED, DESTROYED
    }
}
