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
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Qualifier;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.inject.WeldInstance;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BeanHelperTest {

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
            .addBeanClasses(BeanHelper.class, BeanHelperTest.class, AppScopedBean.class, DependentBean.class);

    @Inject
    BeanHelper beanHelper;

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
        @MethodSource("io.github.jhahnhro.enhancedcdi.util.BeanHelperTest#getArguments")
        <T> void givenTypeAndQualifiers_whenSafeStream_thenAllInstancesAreReturned(final Type beanType,
                                                                                   final Annotation[] qualifiers,
                                                                                   final T[] expectedInstances) {
            final Collection<T> actual = beanHelper.<T>safeStream(beanType, qualifiers)
                    .map(BeanInstance::instance)
                    .toList();
            assertThat(actual).containsExactlyInAnyOrder(expectedInstances);
        }

        @Test
        void givenType_whenSafeStreamIsClosed_thenAllDependentInstancesAreDestroyed() {
            try (Stream<BeanInstance<MyInterface>> beanInstanceStream = beanHelper.safeStream(MyInterface.class)) {
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
            final Type type = new TypeLiteral<List<Integer>>() {}.getType();
            final List<Integer> selectedInstance = beanHelper.select(type);

            assertThat(selectedInstance).isSameAs(integers);
        }

        @Test
        void givenMultipleBeans_whenSelect_thenThrowAmbiguousResolutionException() {
            final Type type = new TypeLiteral<Set<?>>() {}.getType();
            assertThatThrownBy(() -> beanHelper.select(type)).isInstanceOf(AmbiguousResolutionException.class);
        }

        @Test
        void givenNoBeans_whenSelect_thenThrowUnsatisfiedResolutionException() {
            assertThatThrownBy(() -> beanHelper.select(Instant.class)).isInstanceOf(
                    UnsatisfiedResolutionException.class);
        }

        @Test
        void givenAppScopedBean_whenSelect_thenAlwaysReturnSameInstance() {
            final Object instance1 = beanHelper.select(AppScopedBean.class);
            final Object instance2 = beanHelper.select(AppScopedBean.class);

            assertThat(instance1).isSameAs(instance2);
        }

        @Test
        void givenDependentBean_whenSelect_thenAlwaysReturnNewInstance() {
            final Object instance1 = beanHelper.select(DependentBean.class);
            final Object instance2 = beanHelper.select(DependentBean.class);

            assertThat(instance1).isNotEqualTo(instance2);
        }

        @Test
        void whenSelect_thenDestroyingBeanHelperDestroysDependentBeans() {
            final WeldInstance<BeanHelper> instance = weld.select(BeanHelper.class);

            final BeanHelper localBeanHelper = instance.get();

            localBeanHelper.select(AppScopedBean.class);
            localBeanHelper.select(AppScopedBean.class);

            localBeanHelper.select(DependentBean.class);
            localBeanHelper.select(DependentBean.class);

            instance.destroy(localBeanHelper);

            assertThat(AppScopedBean.destroyed).isZero();
            assertThat(DependentBean.destroyed).isEqualTo(2);
        }
    }
}