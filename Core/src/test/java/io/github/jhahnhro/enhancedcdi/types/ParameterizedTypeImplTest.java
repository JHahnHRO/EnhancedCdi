package io.github.jhahnhro.enhancedcdi.types;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParameterizedTypeImplTest {

    static <T> Stream<Arguments> parametrizedTypes() {
        return Stream.of(arguments(new TypeLiteral<List<String>>() {}.getType()),
                         arguments(new TypeLiteral<List<List<Integer>>>() {}.getType()),
                         arguments(new TypeLiteral<List<?>>() {}.getType()),
                         arguments(new TypeLiteral<List<T>>() {}.getType()),
                         arguments(new TypeLiteral<Map<String, Integer>>() {}.getType()),
                         arguments(new TypeLiteral<Map.Entry<String, Integer>>() {}.getType()),
                         arguments(new TypeLiteral<OuterClass.StaticInnerClass<String>>() {}.getType()),
                         arguments(new TypeLiteral<OuterClass<String>.InnerClass<Integer>>() {}.getType()));
    }

    @Nested
    class TestConstructor {
        @Test
        void givenStaticInnerClass_whenCtorWithoutOwnerType_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ParameterizedTypeImpl(OuterClass.StaticInnerClass.class, null, Integer.class));
        }

        @Test
        void givenNonStaticInnerClass_whenCtorWithoutOwnerType_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ParameterizedTypeImpl(OuterClass.InnerClass.class, null, Integer.class));
        }

        @Test
        void givenTopLevelClass_whenCtorWithOwnerType_throwIAE() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ParameterizedTypeImpl(List.class, OuterClass.class, Integer.class));
        }

        @Test
        void givenWrongNumberOfTypeArguments_whenCtor_thenThrowIAE() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ParameterizedTypeImpl(Map.class, null));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ParameterizedTypeImpl(Map.class, null, Integer.class));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ParameterizedTypeImpl(Map.class, null, Integer.class, Integer.class, Integer.class));
        }

        @Test
        void givenTypeArgumentsNullList_throwNPE() {
            assertThatNullPointerException().isThrownBy(
                    () -> new ParameterizedTypeImpl(List.class, null, (List<Type>) null));
        }

        @Test
        void givenTypeArgumentsNullArray_throwNPE() {
            assertThatNullPointerException().isThrownBy(
                    () -> new ParameterizedTypeImpl(List.class, null, (Type[]) null));
        }

        @Test
        void givenTypeArgumentsVarArgsContainNull_throwNPE() {
            assertThatNullPointerException().isThrownBy(
                    () -> new ParameterizedTypeImpl(Map.class, null, Integer.class, null));
        }

        @Test
        void givenTypeArgumentsContainNull_throwNPE() {
            List<Type> types = new ArrayList<>();
            types.add(null);

            assertThatNullPointerException().isThrownBy(() -> new ParameterizedTypeImpl(List.class, null, types));
        }
    }

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

            assertThat(myType).hasSameHashCodeAs(jdkType);
        }

        @ParameterizedTest
        @MethodSource("io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImplTest#parametrizedTypes")
        void testToString(ParameterizedType jdkType) {
            final var myType = new ParameterizedTypeImpl((Class<?>) jdkType.getRawType(), jdkType.getOwnerType(),
                                                         jdkType.getActualTypeArguments());

            assertThat(myType).hasToString(jdkType.toString());
        }
    }
}