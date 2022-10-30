package io.github.jhahnhro.enhancedcdi.types;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.util.TypeLiteral;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterizedTypeImplTest {

    @Nested
    class ConsistencyWithJDK {
        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImplTest#parametrizedTypes")
        void testEquals(ParameterizedType jdkType) {
            final var myType = new ParameterizedTypeImpl((Class<?>) jdkType.getRawType(), jdkType.getOwnerType(),
                                                         jdkType.getActualTypeArguments());

            assertThat(myType).isEqualTo(jdkType);
            assertThat(jdkType).isEqualTo(myType);
        }

        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImplTest#parametrizedTypes")
        void testHashCode(ParameterizedType jdkType) {
            final var myType = new ParameterizedTypeImpl((Class<?>) jdkType.getRawType(), jdkType.getOwnerType(),
                                                         jdkType.getActualTypeArguments());

            assertThat(myType.hashCode()).isEqualTo(jdkType.hashCode());
        }
    }

    static <T> Stream<Arguments> parametrizedTypes() {
        return Stream.of(Arguments.of(new TypeLiteral<List<String>>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<List<Integer>>>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<?>>() {}.getType()),
                         Arguments.of(new TypeLiteral<List<T>>() {}.getType()),
                         Arguments.of(new TypeLiteral<Map<String, Integer>>() {}.getType()),
                         Arguments.of(new TypeLiteral<Map.Entry<String, Integer>>() {}.getType()));
    }
}