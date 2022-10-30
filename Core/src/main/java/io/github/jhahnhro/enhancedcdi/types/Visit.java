package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class Visit {

    public static <T> Set<Class<? super T>> getAllSuperTypes(Class<T> clazz) {
        final ClassHierarchy.RecursiveVisitor visitor = new ClassHierarchy.RecursiveVisitor();
        visitor.visit(clazz);
        return (Set) visitor.getAlreadyVisited();
    }

    public static class ClassHierarchy {

        public static void of(Class<?> clazz, Visitor visitor) {
            visitor.visit(clazz);
        }

        public static class RecursiveVisitor extends Visitor {
            private final Set<Class<?>> alreadyVisited = new LinkedHashSet<>();

            @Override
            public <T> void visitClass(Class<T> clazz) {
                visit(clazz);
            }

            @Override
            public <T> void visitInterface(Class<T> interfaceClazz) {
                visit(interfaceClazz);
            }

            @Override
            public <T> void visit(Class<T> clazz) {
                if (clazz == null || alreadyVisited.contains(clazz)) {
                    return;
                }

                this.visit(clazz.getSuperclass());
                for (Class<?> anInterface : clazz.getInterfaces()) {
                    // the same interface can occur multiple times in the class hierarchy. We make sure it
                    // is only visited once.
                    visit(anInterface);
                }

                this.alreadyVisited.add(clazz);
            }

            public Set<Class<?>> getAlreadyVisited() {
                return Set.copyOf(alreadyVisited);
            }
        }

        public static class Visitor {
            public <T> void visit(Class<T> clazz) {
                if (clazz == null) {
                    return;
                }

                if (clazz.isInterface()) {
                    visitInterface(clazz);
                } else {
                    visitClass(clazz);
                }
            }

            public <T> void visitClass(Class<T> clazz) {
            }

            public <T> void visitInterface(Class<T> interfaceClazz) {
            }
        }
    }

    public static class GenericType {

        public static class Visitor {
            public void visit(Type type) {
                if (type == null) {
                    return;
                }

                if (type instanceof TypeVariable<?> typeVariable) {
                    visitTypeVariable(typeVariable);
                } else if (type instanceof Class<?> clazz) {
                    visitRawClass(clazz);
                } else if (type instanceof ParameterizedType parameterizedType) {
                    visitParametrizedType(parameterizedType);
                } else if (type instanceof GenericArrayType genericArrayType) {
                    visitGenericArrayType(genericArrayType);
                } else if (type instanceof WildcardType wildcardType) {
                    visitWildcardType(wildcardType);
                } else {
                    throw new UnsupportedOperationException("Cannot visit unknown type " + type);
                }
            }

            public <T> void visitRawClass(Class<T> clazz) {
            }

            public void visitParametrizedType(ParameterizedType parameterizedType) {
            }

            public void visitGenericArrayType(GenericArrayType genericArrayType) {
            }

            public void visitWildcardType(WildcardType wildcardType) {
            }

            public <D extends GenericDeclaration> void visitTypeVariable(TypeVariable<D> typeVariable) {
            }
        }

        public static class Hierarchy {

            public static void of(Type type, RecursiveVisitor visitor) {
                visitor.visit(type);
            }

            public static class RecursiveVisitor extends Visitor {
                private final Set<Type> alreadyVisited = new LinkedHashSet<>();

                @Override
                public void visit(Type type) {
                    if (alreadyVisited.add(type)) {
                        super.visit(type);
                    }
                }

                @Override
                public <T> void visitRawClass(Class<T> clazz) {
                    visit(clazz.getGenericSuperclass());

                    for (Type genericInterface : clazz.getGenericInterfaces()) {
                        visit(genericInterface);
                    }
                }

                public Set<Type> getAlreadyVisited() {
                    return alreadyVisited;
                }
            }
        }
    }
}
