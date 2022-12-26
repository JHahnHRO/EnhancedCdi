package io.github.jhahnhro.enhancedcdi.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.stream.Stream;
import javax.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WildcardTypeImplTest {

    static <T> Stream<Arguments> parametrizedTypes() {
        // List is just for the compiler, because we cannot define TypeLiteral subclasses with wildcards
        return Stream.of(
                //@formatter:off
                new TypeLiteral<List<?>>() {}.getType(), new TypeLiteral<List<? super String>>() {}.getType(),
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

        @Test
        void givenLowerBoundThenUpperBoundMustBeObject() {
            final ParameterizedType listOfStringsType =
                    (ParameterizedType) new TypeLiteral<List<? super String>>() {}.getType();
            final WildcardType jdkType = (WildcardType) listOfStringsType.getActualTypeArguments()[0];

            final var myType = new WildcardTypeImpl(new Type[]{Object.class}, new Type[]{String.class});

            assertThat(myType).isEqualTo(jdkType);
            assertThat(jdkType).isEqualTo(myType);

            assertThatIllegalArgumentException().isThrownBy(
                    () -> new WildcardTypeImpl(new Type[0], new Type[]{String.class}));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new WildcardTypeImpl(new Type[]{Serializable.class, CharSequence.class},
                                               new Type[]{String.class}));
        }
    }
}