package io.github.jhahnhro.enhancedcdi.types;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.enterprise.util.TypeLiteral;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WildcardTypeImplTest {

    static <T> Stream<Arguments> parametrizedTypes() {
        // List is just for the compiler
        return Stream.of(
                //@formatter:off
                new TypeLiteral<List<?>>() {}.getType(),
                new TypeLiteral<List<? super Integer>>() {}.getType(),
                new TypeLiteral<List<? extends List<Integer>>>() {}.getType(),
                new TypeLiteral<List<? extends T>>() {}.getType(),
                new TypeLiteral<List<? super List<Integer>>>() {}.getType(),
                new TypeLiteral<List<? super T>>() {}.getType()
                //@formatter:on
        ).map(ParameterizedType.class::cast).map(pt -> pt.getActualTypeArguments()[0]).map(Arguments::of);
    }

    @Nested
    class ConsistencyWithJDK {
        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.WildcardTypeImplTest#parametrizedTypes")
        void testEquals(WildcardType jdkType) {
            final var myType = new WildcardTypeImpl(jdkType.getUpperBounds(), jdkType.getLowerBounds());

            assertThat(myType).isEqualTo(jdkType);
            assertThat(jdkType).isEqualTo(myType);
        }

        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.WildcardTypeImplTest#parametrizedTypes")
        void testHashCode(WildcardType jdkType) {
            final var myType = new WildcardTypeImpl(jdkType.getUpperBounds(), jdkType.getLowerBounds());

            assertThat(myType.hashCode()).isEqualTo(jdkType.hashCode());
        }
    }
}