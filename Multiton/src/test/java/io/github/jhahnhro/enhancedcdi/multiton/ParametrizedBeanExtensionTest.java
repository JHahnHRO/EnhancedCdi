package io.github.jhahnhro.enhancedcdi.multiton;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Shape;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.BeanWithParametrizedProducer;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.EventSink;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.ParametrizedBeanClass;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.ParametrizedBeanClassWithParametrizedProducer;
import io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans.TestQualifier;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import org.jboss.weld.environment.se.ContainerLifecycleObserver;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.inject.WeldInstance;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnableAutoWeld
class ParametrizedBeanExtensionTest {

    private static final ContainerLifecycleObserver<BeforeBeanDiscovery> addAnnotatedTypeToDiscover =
            ContainerLifecycleObserver.beforeBeanDiscovery()
            .notify((bbd, bm) -> {
                bbd.addAnnotatedType(bm.createAnnotatedType(ParametrizedBeanClass.class),
                                     ParametrizedBeanClass.class.getCanonicalName());
                bbd.addAnnotatedType(bm.createAnnotatedType(ParametrizedBeanClassWithParametrizedProducer.class),
                                     ParametrizedBeanClassWithParametrizedProducer.class.getCanonicalName());
            });
    Weld w = WeldInitiator.createWeld()
            .addExtension(new ParametrizedBeanExtension())
            .addBeanClasses(EventSink.class, BeanWithParametrizedProducer.class)
            .addContainerLifecycleObserver(addAnnotatedTypeToDiscover);

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(w).build();

    @Inject
    ParametrizedBeanExtension extension;

    @Inject
    BeanManager beanManager;

    @Test
    void givenParametrizedBeanClass_whenInstanceSelect_thenCorrectNumberOfBeans() {
        assertThat(weld.select(ParametrizedBeanClass.class, Any.Literal.INSTANCE).stream()).hasSize(
                Color.values().length);

        var instance = weld.select(new TypeLiteral<Map<Color, ParametrizedBeanClass>>() {});
        assertThat(instance.isResolvable()).isTrue();
        assertThat(instance.get()).hasSize(Color.values().length)
                .allSatisfy((color, parametrizedBeanClass) -> assertThat(parametrizedBeanClass.getMyColor()).isEqualTo(
                        color));
    }

    @Test
    void givenParametrizedProducerMethod_whenInstanceSelect_thenCorrectNumberOfBeans() {
        assertThat(weld.select(String.class, Any.Literal.INSTANCE).stream()).hasSize(Color.values().length);

        var instance = weld.select(new TypeLiteral<Map<Color, String>>() {});
        assertThat(instance.isResolvable()).isTrue();
        assertThat(instance.get()).hasSize(Color.values().length)
                .allSatisfy((color, ctxInstance) -> assertThat(ctxInstance).isEqualTo(color.name()));
    }

    @Test
    void givenParametrizedProducerMethodInsideParametrizedBean_whenInstanceSelect_thenCorrectNumberOfBeans() {
        Set<List<Object>> actualLists = weld.select(new TypeLiteral<List<Object>>() {}, Any.Literal.INSTANCE)
                .stream()
                .collect(Collectors.toSet());

        assertThat(actualLists).isEqualTo(Set.of(List.of(Color.RED, Shape.CIRCLE), List.of(Color.RED, Shape.SQUARE),
                                                 List.of(Color.GREEN, Shape.CIRCLE), List.of(Color.GREEN, Shape.SQUARE),
                                                 List.of(Color.BLUE, Shape.CIRCLE), List.of(Color.BLUE, Shape.SQUARE)));
    }

    @ParameterizedTest
    @EnumSource
    void givenParametrizedBeanClass_whenInstanceSelectWithQualifier_thenResolvable(Color myColor) {
        WeldInstance<ParametrizedBeanClass> completeBeanInstance = weld.select(ParametrizedBeanClass.class,
                                                                               new TestQualifier.Literal(myColor));
        assertThat(completeBeanInstance.isResolvable()).isTrue();

        ParametrizedBeanClass colorBean = completeBeanInstance.get();
        assertThat(colorBean.getMyColor()).isEqualTo(myColor);
    }

    // TODO: Observer methods, producer methods (static & non-static), disposers, parametrized producer methods
    // inside parametrized beans
}