package io.github.jhahnhro.enhancedcdi.context.extension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import io.github.jhahnhro.enhancedcdi.context.CloseableContext;
import io.github.jhahnhro.enhancedcdi.context.CloseableContextController;
import io.github.jhahnhro.enhancedcdi.context.ProcessContext;
import io.github.jhahnhro.enhancedcdi.context.ProcessContextController;
import io.github.jhahnhro.enhancedcdi.context.SimpleCloseableContextController;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.types.TypeVariableResolver;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.inject.Singleton;

public class ContextExtension implements Extension {

    private final List<AnnotatedType<? extends CloseableContext>> contextTypes = new ArrayList<>();
    private final List<AnnotatedType<? extends CloseableContext>> controllers = new ArrayList<>();


    <C extends CloseableContext> void discoverContexts(@Observes @WithAnnotations(RegisterContext.class) ProcessAnnotatedType<C> pat) {
        final Class<C> contextClass = pat.getAnnotatedType().getJavaClass();
        try {
            contextClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new DefinitionException("Classes annotated with " + RegisterContext.class.getSimpleName()
                                          + " must have an accessible no-args constructor.", e);
        }

        contextTypes.add(pat.getAnnotatedType());
    }


    void addContexts(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        contextTypes.forEach(t -> addContext(abd, beanManager, t));
    }

    private <C extends CloseableContext> void addContext(AfterBeanDiscovery abd, BeanManager beanManager,
                                                         AnnotatedType<C> contextType) {
        final C context = addContext(abd, contextType);
        addContextBean(abd, contextType, context);
        addControllerBean(abd, beanManager, contextType, context);
    }

    private <C extends CloseableContext> void addContextBean(AfterBeanDiscovery abd, AnnotatedType<C> contextType,
                                                             C context) {
        abd.addBean().read(contextType).scope(Singleton.class).produceWith(instance -> context);
    }

    private <C extends CloseableContext> C addContext(AfterBeanDiscovery abd, AnnotatedType<C> contextType) {
        final Constructor<C> noArgsConstructor = contextType.getConstructors()
                .stream()
                .filter(ctor -> ctor.getParameters().isEmpty())
                .map(AnnotatedConstructor::getJavaMember)
                .findAny()
                .orElseThrow();

        final C context;
        try {
            context = noArgsConstructor.newInstance();
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        abd.addContext(context);
        return context;
    }

    private <C extends CloseableContext> void addControllerBean(AfterBeanDiscovery abd, BeanManager beanManager,
                                                                AnnotatedType<C> contextType, C context) {
        final Class<C> contextClass = contextType.getJavaClass();
        final var controllerType = new ParameterizedTypeImpl(CloseableContextController.class, null, contextClass);

        Bean<?> controllerBean = beanManager.resolve(beanManager.getBeans(controllerType));
        if (controllerBean == null) {

            if (context instanceof ProcessContext<?, ?> processContext) {
                final var typeOfProcessContext = (ParameterizedType) TypeVariableResolver.withKnownTypesOf(contextClass)
                        .resolve(ProcessContext.class);
                final var KEY = typeOfProcessContext.getActualTypeArguments()[0];
                final var PROCESS = typeOfProcessContext.getActualTypeArguments()[1];

                abd.addBean()
                        .addTransitiveTypeClosure(
                                new ParameterizedTypeImpl(ProcessContextController.class, null, KEY, PROCESS,
                                                          contextClass))
                        .scope(Singleton.class)
                        .produceWith(instance -> new ProcessContextController<>(processContext));
            } else {

                abd.addBean()
                        .read(beanManager.createAnnotatedType(SimpleCloseableContextController.class))
                        .types(controllerType)
                        .scope(Singleton.class);
            }
        }
    }
}
