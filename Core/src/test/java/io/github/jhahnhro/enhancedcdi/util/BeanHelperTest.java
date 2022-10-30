package io.github.jhahnhro.enhancedcdi.util;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(WeldJunit5Extension.class)
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
    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(BeanHelper.class, BeanHelperTest.class).build();
    @Inject
    BeanHelper beanHelper;

    public static Stream<Arguments> getArguments() {
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
                          new Annotation[]{new AnnotationLiteral<MyQualifier>() {}}, new Map[]{qualifiedMap})
                //@formatter:on
        );
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    <T> void testSelect(final Type beanType, Annotation[] qualifiers, T[] expectedInstances) {
        final Collection<BeanInstance<T>> beanInstances = beanHelper.select(beanType, qualifiers);

        final Collection<T> actual = beanInstances.stream().map(BeanInstance::instance).toList();
        assertThat(actual).containsExactlyInAnyOrder(expectedInstances);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    private @interface MyQualifier {}

    private static class A {}

    private static class B extends A {}

    private static class C extends B {}
}