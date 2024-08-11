package io.github.jhahnhro.enhancedcdi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnhancedInstanceTest {

    @Produces
    static List<String> strings = List.of("foo", "bar", "baz");
    @Produces
    static List<Integer> integers = List.of(1, 2, 3, 4);
    @Produces
    static Set<A> aSet = Set.of(new A());
    @Produces
    static Set<B> bSet = Set.of(new B());
    @Produces
    static Set<C> cSet = Set.of(new C());
    @Produces
    static Map<Integer, String> defaultMap = Map.of(1, "1", 2, "2", 3, "3");
    @Produces
    @MyQualifier
    static Map<Integer, String> qualifiedMap = Map.of(1, "-1", 2, "-2", 3, "-3");
    Weld w = new Weld().disableDiscovery()
            .addBeanClasses(EnhancedInstance.class, EnhancedInstanceTest.class, AppScopedBean.class,
                            DependentBean.class);

    @Inject
    EnhancedInstance<Object> enhancedInstance;

    static Stream<Arguments> getArguments() {
        final Annotation[] none = new Annotation[0];
        return Stream.of(
                //@formatter:off
                arguments(new TypeLiteral<List<String>>() {}.getType(), none, new List[]{strings}),
                arguments(new TypeLiteral<List<?>>() {}.getType(), none, new List[]{strings, integers}),
                arguments(new TypeLiteral<Set<? extends B>>() {}.getType(), none, new Set[]{bSet, cSet}),
                arguments(new TypeLiteral<Set<? super B>>() {}.getType(), none, new Set[]{aSet, bSet}),
                arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(), none, new Map[]{defaultMap}),
                arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(),
                          new Annotation[]{Default.Literal.INSTANCE}, new Map[]{defaultMap}),
                arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(), new Annotation[]{Any.Literal.INSTANCE},
                          new Map[]{defaultMap, qualifiedMap}),
                arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(),
                          new Annotation[]{new AnnotationLiteral<MyQualifier>() {}}, new Map[]{qualifiedMap}),
                arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(),
                          new Annotation[]{new AnnotationLiteral<MyOtherQualifier>() {}}, new Map[0])
                //@formatter:on
        );
    }

    @BeforeEach
    void resetCounters() {
        AppScopedBean.destroyed = 0;
        DependentBean.destroyed = 0;
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    private @interface MyOtherQualifier {}

    interface MyInterface {}

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    private @interface MyQualifier {}

    @ApplicationScoped
    static class AppScopedBean implements MyInterface {
        static int destroyed = 0;

        @PreDestroy
        void destroy() {
            destroyed++;
        }
    }

    private static class A {}

    private static class B extends A {}

    private static class C extends B {}

    @Dependent
    static class DependentBean implements MyInterface {
        static int destroyed = 0;

        @PreDestroy
        void destroy() {
            destroyed++;
        }
    }

    @Nested
    @EnableWeld
    class TestStream {

        @WeldSetup
        WeldInitiator weld = WeldInitiator.of(w);

        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.util.EnhancedInstanceTest#getArguments")
        <T> void givenTypeAndQualifiers_whenSafeStream_thenAllInstancesAreReturned(final Type beanType,
                                                                                   final Annotation[] qualifiers,
                                                                                   final T[] expectedInstances) {
            final Collection<T> actual = enhancedInstance.<T>select(beanType, qualifiers)
                    .safeStream()
                    .map(BeanInstance::instance)
                    .toList();
            assertThat(actual).containsExactlyInAnyOrder(expectedInstances);
        }

        @Test
        void givenType_whenSafeStreamIsClosed_thenAllDependentInstancesAreDestroyed() {
            try (var beanInstanceStream = enhancedInstance.select(MyInterface.class).safeStream()) {
                final List<BeanInstance<MyInterface>> beanInstances = beanInstanceStream.toList();

                // verify that terminal operation toList() does not close the stream
                assertThat(AppScopedBean.destroyed).isZero();
                assertThat(DependentBean.destroyed).isZero();

                assertThat(beanInstances).hasSize(2);
                beanInstances.forEach(BeanInstance::instance); // trigger lazy initialization

                // verify that initialization has no side effects that trigger destruction
                assertThat(AppScopedBean.destroyed).isZero();
                assertThat(DependentBean.destroyed).isZero();
            }

            // verify that only the dependent beans were destroyed
            assertThat(AppScopedBean.destroyed).isZero();
            assertThat(DependentBean.destroyed).isEqualTo(1);
        }


    }

    @Nested
    @EnableWeld
    class TestSelect {

        @WeldSetup
        WeldInitiator weld = WeldInitiator.of(w);

        @Test
        void givenUniqueBean_whenSelect_thenSucceed() {
            final List<Integer> selectedInstance = enhancedInstance.select(new TypeLiteral<List<Integer>>() {}).get();

            assertThat(selectedInstance).isSameAs(integers);
        }

        @Test
        void givenMultipleBeans_whenSelect_thenThrowAmbiguousResolutionException() {
            final EnhancedInstance<Set<?>> selected = enhancedInstance.select(new TypeLiteral<>() {});
            assertThatThrownBy(selected::get).isInstanceOf(AmbiguousResolutionException.class);
        }

        @Test
        void givenNoBeans_whenSelect_thenThrowUnsatisfiedResolutionException() {
            final EnhancedInstance<Instant> selected = enhancedInstance.select(Instant.class);
            assertThatThrownBy(selected::get).isInstanceOf(UnsatisfiedResolutionException.class);
        }

        @Test
        void givenAppScopedBean_whenSelect_thenAlwaysReturnSameInstance() {
            final Object instance1 = enhancedInstance.select(AppScopedBean.class).get();
            final Object instance2 = enhancedInstance.select(AppScopedBean.class).get();

            assertThat(instance1).isSameAs(instance2);
        }

        @Test
        void givenDependentBean_whenSelect_thenAlwaysReturnNewInstance() {
            final Object instance1 = enhancedInstance.select(DependentBean.class).get();
            final Object instance2 = enhancedInstance.select(DependentBean.class).get();

            assertThat(instance1).isNotEqualTo(instance2);
        }

        @Test
        void whenSelect_thenDestroyingEnhancedInstanceDestroysDependentBeans() {
            final Instance<EnhancedInstance<Object>> instance = weld.select(new TypeLiteral<>() {});
            final EnhancedInstance<Object> localEnhancedInstance = instance.get();

            localEnhancedInstance.select(AppScopedBean.class).get();
            localEnhancedInstance.select(AppScopedBean.class).get();

            localEnhancedInstance.select(DependentBean.class).get();
            localEnhancedInstance.select(DependentBean.class).get();

            instance.destroy(localEnhancedInstance);

            assertThat(AppScopedBean.destroyed).isZero();
            assertThat(DependentBean.destroyed).isEqualTo(2);
        }
    }
}