package io.github.jhahnhro.enhancedcdi.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TypeVariableResolverTest {

    public static <U, V, W, X, Y, Z> Stream<Arguments> parameters() {
        final Type typeU = new TypeLiteral<U>() {}.getType();
        final Type typeV = new TypeLiteral<V>() {}.getType();
        final Type typeW = new TypeLiteral<W>() {}.getType();
        final Type typeX = new TypeLiteral<X>() {}.getType();
        final Type typeY = new TypeLiteral<Y>() {}.getType();
        final Type typeZ = new TypeLiteral<Z>() {}.getType();

        return Stream.of(
                //@formatter:off
                arguments(new TypeLiteral<String>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          String.class),

                arguments(new TypeLiteral<X>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          Integer.class),

                arguments(new TypeLiteral<X[]>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new GenericArrayTypeImpl(Integer.class)),

                arguments(new TypeLiteral<X[][]>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new GenericArrayTypeImpl(new GenericArrayTypeImpl(Integer.class))),

                arguments(new TypeLiteral<List<X>>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new TypeLiteral<List<Integer>>() {}.getType()),

                arguments(new TypeLiteral<List<Set<X>>>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new TypeLiteral<List<Set<Integer>>>() {}.getType()),

                arguments(new TypeLiteral<List<? extends X>>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new TypeLiteral<List<? extends Integer>>() {}.getType()),

                arguments(new TypeLiteral<List<? super X>>() {}.getType(),
                          Map.of(typeX, Integer.class),
                          new TypeLiteral<List<? super Integer>>() {}.getType()),

                arguments(new TypeLiteral<Map<X, Y>>() {}.getType(),
                          Map.of(typeX, Integer.class,
                                 typeY, String.class),
                          new TypeLiteral<Map<Integer, String>>() {}.getType()),

                arguments(new TypeLiteral<Map<X, Y>>() {}.getType(),
                          Map.of(typeX, typeV,
                                 typeY, typeW),
                          new TypeLiteral<Map<V, W>>() {}.getType()),

                arguments(new TypeLiteral<Map<X, Y>>() {}.getType(),
                          Map.of(typeX, new TypeLiteral<Map<U, V>>(){}.getType(),
                                 typeY, new TypeLiteral<List<W>>(){}.getType()),
                          new TypeLiteral<Map<Map<U, V>, List<W>>>() {}.getType())

                //@formatter:on
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testResolving(Type source, Map<TypeVariable<?>, Type> knownTypes, Type expected) {

        final Type actual = new TypeVariableResolver(knownTypes).resolve(source);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testResolvedTypeClosure() {
        final Type typeOfListOfInteger = new TypeLiteral<List<Integer>>() {}.getType();
        final TypeVariableResolver resolver = TypeVariableResolver.withKnownTypesOf(typeOfListOfInteger);

        assertThat(resolver.resolvedTypeClosure(typeOfListOfInteger)).isEqualTo(
                Set.of(typeOfListOfInteger, new TypeLiteral<SequencedCollection<Integer>>() {}.getType(),
                       new TypeLiteral<Collection<Integer>>() {}.getType(),
                       new TypeLiteral<Iterable<Integer>>() {}.getType(), Object.class));
    }

    @Nested
    class TestKnownTypes {

        @Test
        void shouldResolveVariablesInSuperTypes() {
            final Type typeOfListOfInteger = new TypeLiteral<ArrayList<Integer>>() {}.getType();
            final TypeVariableResolver resolver = TypeVariableResolver.withKnownTypesOf(typeOfListOfInteger);

            assertThat(resolver.hasUnresolvedVariables()).isFalse();

            // super classes
            assertThat(resolver.resolve(ArrayList.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(AbstractList.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(AbstractCollection.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            // interfaces
            assertThat(resolver.resolve(List.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(SequencedCollection.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(Collection.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(Iterable.class.getTypeParameters()[0])).isEqualTo(Integer.class);
        }

        @Test
        <T> void shouldRecognizeUnresolvableVariables() {
            final Type typeOfListOfInteger = new TypeLiteral<Map<Integer, T>>() {}.getType();
            final TypeVariableResolver resolver = TypeVariableResolver.withKnownTypesOf(typeOfListOfInteger);

            assertThat(resolver.hasUnresolvedVariables()).isTrue();

            assertThat(resolver.resolve(Map.class.getTypeParameters()[0])).isEqualTo(Integer.class);
            assertThat(resolver.resolve(Map.class.getTypeParameters()[1])).isEqualTo(new TypeLiteral<T>() {}.getType());
        }

        @Test
        void shouldNotResolveNestedParametrizedTypes() {
            final Type type = new TypeLiteral<Map<Set<Integer>, Set<Long>>>() {}.getType();
            final TypeVariableResolver resolver = TypeVariableResolver.withKnownTypesOf(type);

            assertThat(resolver.hasUnresolvedVariables()).isFalse();

            assertThat(resolver.resolve(Map.class.getTypeParameters()[0])).isEqualTo(
                    new TypeLiteral<Set<Integer>>() {}.getType());
            assertThat(resolver.resolve(Map.class.getTypeParameters()[1])).isEqualTo(
                    new TypeLiteral<Set<Long>>() {}.getType());

            final TypeVariable<?> unresolvableVariable = Set.class.getTypeParameters()[0];
            assertThat(resolver.resolve(unresolvableVariable)).isEqualTo(unresolvableVariable);
        }
    }
}