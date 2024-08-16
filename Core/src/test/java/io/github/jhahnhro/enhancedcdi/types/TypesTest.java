package io.github.jhahnhro.enhancedcdi.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TypesTest {

    @Nested
    class TestCaptureTypeVariable {

        @Test
        <T> void testMethodWithOneVariable() {
            TypeVariable<?> t = Types.captureTypeVariable("T");

            assertThat(t).isEqualTo(new TypeLiteral<T>() {}.getType());
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> Types.captureTypeVariable("X"));
        }

        @Test
        <T, U> void testMethodWithTwoVariables() {
            TypeVariable<?> t = Types.captureTypeVariable("T");
            TypeVariable<?> u = Types.captureTypeVariable("U");

            assertThat(t).isEqualTo(new TypeLiteral<T>() {}.getType());
            assertThat(u).isEqualTo(new TypeLiteral<U>() {}.getType());
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> Types.captureTypeVariable("X"));
        }

        @Test
        void testMethodWithoutVariables() {
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> Types.captureTypeVariable("X"));
        }

        @Test
        void testCaptureFromConstructor() {
            class Foobar {

                private final TypeVariable<?> captured;
                private final TypeLiteral<?> typeLiteral;

                public <U> Foobar() {
                    captured = Types.captureTypeVariable("U");
                    typeLiteral = new TypeLiteral<U>() {};
                }
            }

            final Foobar object = new Foobar();
            final TypeVariable<?> t = object.captured;

            assertThat(t).isEqualTo(object.typeLiteral.getType());
        }

        @Test
        void testCaptureFromStaticInitializer() {
            class Foobar<T> {
                private static final TypeVariable<?> captured = Types.captureTypeVariable("T");
            }
            final TypeVariable<?> t = Foobar.captured;

            assertThat(t).isEqualTo(Foobar.class.getTypeParameters()[0]);
        }
    }

    @Nested
    class TestSuperClasses {
        @Test
        void testObject() {
            final var classes = Types.superClasses(Object.class);
            assertThat(classes).containsExactly(Object.class);
        }

        @Test
        void testClassHierarchy() {
            final var classes = Types.superClasses(Bar.class);
            assertThat(classes).containsExactly(Bar.class, Foo.class, Object.class);
        }

        @Test
        void testInterface() {
            final var classes = Types.superClasses(List.class);
            assertThat(classes).containsExactly(Object.class);
        }

        static class Foo {}

        static class Bar extends Foo {}
    }

    @Nested
    class TestGetSuperInterfaces {
        @Test
        void testObject() {
            final var classes = Types.superInterfaces(Object.class);
            assertThat(classes).isEmpty();
        }

        @Test
        void testInterfaceClass() {
            final var classes = Types.superInterfaces(Foo.class);
            assertThat(classes).containsExactly(Foo.class);
        }

        @Test
        void testInterfaceHierarchy() {
            final var classes = Types.superInterfaces(Bar.class);
            assertThat(classes).containsExactly(Bar.class, Foo.class);
        }

        @Test
        void testClassImplementingAnInterface() {
            final var classes = Types.superInterfaces(Baz.class);
            assertThat(classes).containsExactly(Bar.class, Foo.class);
        }

        @Test
        void testClassImplementingAnInterfaceTwice() {
            final var classes = Types.superInterfaces(BazFoo.class);
            assertThat(classes).containsExactly(Foo.class, Bar.class);
        }

        interface Foo {}

        interface Bar extends Foo {}

        static class Baz implements Bar {}

        static class BazFoo extends Baz implements Foo {}
    }

    @Nested
    class TestErasure {

        public static <T extends Foo> Stream<Arguments> getTypes() {
            return Stream.of(arguments(Integer.class, Integer.class),
                             arguments(new TypeLiteral<List<String>>() {}.getType(), List.class),
                             arguments(new TypeLiteral<Map<Integer, String>>() {}.getType(), Map.class),
                             arguments(new TypeLiteral<T>() {}.getType(), Foo.class),
                             arguments(new TypeLiteral<List<Integer>[]>() {}.getType(), List[].class),
                             arguments(new TypeLiteral<T[]>() {}.getType(), Foo[].class));
        }

        @SuppressWarnings("unused")
        private static <T extends Foo, U> void methodWithGenericParameters(String arg0, List<String> arg1, T arg2,
                                                                           U arg3, List<T> arg4, T[] arg5, U[] arg6) {}

        public static Stream<Arguments> getJdkArguments() {
            try {
                final Class<?>[] erasedTypes = {String.class, List.class, Foo.class, Object.class, List.class,
                        Foo[].class, Object[].class};
                final Method method = TestErasure.class.getDeclaredMethod("methodWithGenericParameters", erasedTypes);
                final Type[] genericTypes = method.getGenericParameterTypes();

                return IntStream.range(0, method.getParameterCount())
                        .mapToObj(i -> arguments(genericTypes[i], erasedTypes[i]));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @ParameterizedTest
        @MethodSource("getTypes")
        void testErasure(Type type, Class<?> expectedErasure) {
            final Class<?> actual = Types.erasure(type);
            assertThat(actual).isEqualTo(expectedErasure);
        }

        @ParameterizedTest
        @MethodSource("getJdkArguments")
        void testCompatibilityWithJDK(Type type, Class<?> expectedErasure) {
            final Class<?> actual = Types.erasure(type);
            assertThat(actual).isEqualTo(expectedErasure);
        }

        static class Foo {}
    }
}
