package io.github.jhahnhro.enhancedcdi.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GenericArrayTypeImplTest {

    static <T> Stream<Arguments> parametrizedTypes() {
        return Stream.of(Arguments.of(new TypeLiteral<List<String>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<List<Integer>>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<?>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<T>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<Map<String, Integer>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<Map.Entry<String, Integer>[]>() {}.getType()),
                         Arguments.of(new TypeLiteral<T[]>() {}.getType()));
    }

    @Nested
    class ConsistencyWithJDK {
        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.GenericArrayTypeImplTest#parametrizedTypes")
        void testEquals(GenericArrayType jdkType) {
            final var myType = new GenericArrayTypeImpl(jdkType.getGenericComponentType());

            assertThat(myType).isEqualTo(jdkType);
            assertThat(jdkType).isEqualTo(myType);
        }

        @Test
        void genericTypeNotEqualToClass() {
            final Type myType = new GenericArrayTypeImpl(Integer.class);
            final Type jdkType = Integer[].class;

            assertThat(myType).isNotEqualTo(jdkType);
            assertThat(jdkType).isNotEqualTo(myType);
        }

        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.GenericArrayTypeImplTest#parametrizedTypes")
        void testHashCode(GenericArrayType jdkType) {
            final var myType = new GenericArrayTypeImpl(jdkType.getGenericComponentType());

            assertThat(myType).hasSameHashCodeAs(jdkType);
        }
    }
}