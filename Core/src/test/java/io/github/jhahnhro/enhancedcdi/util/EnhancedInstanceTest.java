package io.github.jhahnhro.enhancedcdi.util;

import static org.assertj.core.api.Assertions.*;
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
import jakarta.enterprise.inject.Instance.Handle;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.ExplicitParamInjection;
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
                            DependentBean.class, InjectionPointAwareBean.class);

    @Inject
    EnhancedInstance<Object> enhancedInstance;

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

    @Dependent
    static class InjectionPointAwareBean {
        @Inject
        InjectionPoint injectionPoint;
    }

    @Nested
    @EnableWeld
    @ExplicitParamInjection
    class TestStream {

        @WeldSetup
        WeldInitiator weld = WeldInitiator.of(w);

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
                    arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(),
                              new Annotation[]{new AnnotationLiteral<MyOtherQualifier>() {}}, new Map[0])
                    //@formatter:on
            );
        }

        @ParameterizedTest
        @MethodSource("getArguments")
        <T> void givenTypeAndQualifiers_whenStream_thenAllInstancesAreReturned(Type beanType, Annotation[] qualifiers
                , T[] expectedInstances) {
            final Collection<T> actual = enhancedInstance.<T>selectUnchecked(beanType, qualifiers).stream().toList();
            assertThat(actual).containsExactlyInAnyOrder(expectedInstances);
        }

        @Test
        void givenType_whenHandlesStreamIsClosed_thenAllDependentInstancesAreDestroyed() {
            try (var handlesStream = enhancedInstance.select(MyInterface.class).handlesStream()) {
                final List<Handle<MyInterface>> handles = handlesStream.toList();

                // verify that terminal operation toList() does not close the stream
                assertThat(AppScopedBean.destroyed).isZero();
                assertThat(DependentBean.destroyed).isZero();

                assertThat(handles).hasSize(2);
                handles.forEach(Handle::get); // trigger lazy initialization

                // verify that initialization has no side effects that trigger destruction
                assertThat(AppScopedBean.destroyed).isZero();
                assertThat(DependentBean.destroyed).isZero();
            }

            // verify that only the dependent beans were destroyed
            assertThat(AppScopedBean.destroyed).isZero();
            assertThat(DependentBean.destroyed).isEqualTo(1);
        }


    }

    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
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
            final Instance<Set<?>> selected = enhancedInstance.select(new TypeLiteral<>() {});
            assertThatExceptionOfType(AmbiguousResolutionException.class).isThrownBy(selected::get);
        }

        @Test
        void givenNoBeans_whenSelect_thenThrowUnsatisfiedResolutionException() {
            final Instance<Instant> selected = enhancedInstance.select(Instant.class);
            assertThatExceptionOfType(UnsatisfiedResolutionException.class).isThrownBy(selected::get);
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
        void whenSelectWithType_thenReturnAllInstances(@Any EnhancedInstance<Object> enhancedInstance) {
            final var instances = enhancedInstance.select(new TypeLiteral<Map<Integer, String>>() {}).stream().toList();

            assertThat(instances).containsExactlyInAnyOrder(defaultMap, qualifiedMap);
        }


        @Test
        void whenSelectWithTypeAndQualifier_thenReturnCorrectInstance(@Any EnhancedInstance<Object> enhancedInstance) {
            var defaultInstance = enhancedInstance.select(new TypeLiteral<Map<Integer, String>>() {},
                                                          Default.Literal.INSTANCE).get();
            assertThat(defaultInstance).isSameAs(defaultMap);

            var qualifiedInstance = enhancedInstance.select(new TypeLiteral<Map<Integer, String>>() {},
                                                            new AnnotationLiteral<MyQualifier>() {}).get();
            assertThat(qualifiedInstance).isSameAs(qualifiedMap);
        }

        static Stream<Arguments> illegalSubtypes() {
            return Stream.of(
                    //@formatter:off
                    arguments(new TypeLiteral<String>() {}, new TypeLiteral<Integer>() {}),
                    arguments(new TypeLiteral<Iterable<String>>() {}, new TypeLiteral<List<Integer>>() {})
                    //@formatter:on
            );
        }

        static Stream<Arguments> legalSubtypes() {
            return Stream.of(
                    //@formatter:off
                    arguments(new TypeLiteral<CharSequence>() {}, new TypeLiteral<String>() {}),
                    arguments(new TypeLiteral<Iterable<String>>() {}, new TypeLiteral<List<String>>() {})
                    //@formatter:on
            );
        }

        @ParameterizedTest
        @MethodSource("illegalSubtypes")
        <T> void givenIllegalSubtype_whenSelectUnchecked_thenThrowIAE(TypeLiteral<T> requiredType,
                                                                      TypeLiteral<?> illegalSubtype) {
            // selectUnchecked from Object to requiredType should always succeed
            final Type typeT = requiredType.getType();
            var childInstance = enhancedInstance.selectUnchecked(typeT);
            assertThat(childInstance.injectionPoint.getType()).isEqualTo(typeT);

            assertThatIllegalArgumentException().isThrownBy(
                    () -> childInstance.selectUnchecked(illegalSubtype.getType()));
        }

        @ParameterizedTest
        @MethodSource("legalSubtypes")
        <T, U extends T> void givenLegalSubtype_whenSelectUnchecked_thenDoNoThrow(TypeLiteral<T> requiredType,
                                                                                  TypeLiteral<U> subtype) {
            // selectUnchecked from Object to requiredType should always succeed
            final Type typeT = requiredType.getType();
            var childInstance = enhancedInstance.selectUnchecked(typeT);
            assertThat(childInstance.injectionPoint.getType()).isEqualTo(typeT);

            final Type typeU = subtype.getType();
            var childEvent2 = childInstance.selectUnchecked(typeU);
            assertThat(childEvent2.injectionPoint.getType()).isEqualTo(typeU);
        }

        @Nested
        class TestInjectionPoint {

            @Test
            void whenSelect_thenOriginalInjectionPointIsUsed() {
                final InjectionPointAwareBean instance = enhancedInstance.select(InjectionPointAwareBean.class).get();

                // verify that it is the injection point in this class, not the auxiliary injection point in
                // EnhancedInstance
                assertThat(instance.injectionPoint.getMember().getDeclaringClass()).isEqualTo(
                        EnhancedInstanceTest.class);
            }

            @Test
            void whenSelectUnchecked_thenOriginalInjectionPointIsUsed() {
                final Type type = InjectionPointAwareBean.class;

                final InjectionPointAwareBean instance = enhancedInstance.<InjectionPointAwareBean>selectUnchecked(type)
                        .get();

                // verify that it is the injection point in this class, not the auxiliary injection point in
                // EnhancedInstance
                assertThat(instance.injectionPoint.getMember().getDeclaringClass()).isEqualTo(
                        EnhancedInstanceTest.class);
            }
        }
    }
}