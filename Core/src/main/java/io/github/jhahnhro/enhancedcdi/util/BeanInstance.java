package io.github.jhahnhro.enhancedcdi.util;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Convenience object encapsulating a bean, one of its instances and the {@link CreationalContext} in which that
 * instance was created.
 * <p>
 * According to the CDI Spec a bean has:
 * <ol>
 *     <li><i>contextual instances</i>, i.e. the "real" instance(s) of the bean. There can be one or more,
 *     depending on the bean's scope, i.e. one for {@code @ApplicationScoped} and {@code @Singleton}
 *     beans, as many as there are requests for {@code @RequestScoped}, an unlimited amount for {@code @Dependent}
 *     etc. Calling methods on the contextual instance(s) directly calls the "real" method in the bean class.</li>
 *     <li><i>contextual references</i>, i.e. client proxies around the contextual instances for all normal scoped
 *     beans. The client proxies also include the interceptor chain, i.e. calling methods on a contextual reference
 *     goes through the complete interceptor and decorator chain of the bean before arriving at the "real", i.e. the
 *     contextual instance.</li>
 *     <li><i>injectable references</i> for each injection point that resolves to the bean.</li>
 *     <li>{@link javax.enterprise.inject.spi.Unmanaged unmanaged instances} that support interceptors, decorators,
 *     lifecycle callbacks etc., but are not managed by the CDI container and live independently along-side the
 *     managed instance(s).</li>
 * </ol>
 *
 * @param instance
 * @param contextual
 * @param context
 * @param <T>
 */
public record BeanInstance<T>(T instance, Contextual<T> contextual, CreationalContext<T> context) {

    /**
     * Creates a contextual instance by calling {@link Contextual#create(CreationalContext)}.
     *
     * @param contextual
     * @param context
     * @param <T>
     * @return
     */
    public static <T> BeanInstance<T> createContextualInstance(Contextual<T> contextual, CreationalContext<T> context) {
        return new BeanInstance<>(contextual.create(context), contextual, context);
    }

    public static <T> BeanInstance<T> createContextualReference(BeanManager beanManager, Bean<T> bean, Type beanType) {
        final CreationalContext<T> ctx = beanManager.createCreationalContext(bean);
        return new BeanInstance<>((T) beanManager.getReference(bean, beanType, ctx), bean, ctx);
    }

    public static <T> BeanInstance<T> createInjectableReference(BeanManager beanManager,
                                                                InjectionPoint injectionPoint) {
        final Annotation[] qualifiers = injectionPoint.getQualifiers().toArray(Annotation[]::new);
        final Set<Bean<?>> beans = beanManager.getBeans(injectionPoint.getType(), qualifiers);
        final Bean<T> resolvedBean = (Bean<T>) beanManager.resolve(beans);
        final CreationalContext<T> ctx = beanManager.createCreationalContext(resolvedBean);
        return new BeanInstance<>((T) beanManager.getInjectableReference(injectionPoint, ctx), resolvedBean, ctx);
    }

    public void destroy() {
        contextual.destroy(instance, context);
    }
}
