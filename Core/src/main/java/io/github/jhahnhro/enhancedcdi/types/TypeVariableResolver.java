package io.github.jhahnhro.enhancedcdi.types;

import io.github.jhahnhro.enhancedcdi.types.Visit.GenericType.Hierarchy.RecursiveVisitor;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TypeVariableResolver {
    private final Map<TypeVariable<?>, Type> resolvedVariables;
    private final Map<Type, Type> resolvedTypesCache = new ConcurrentHashMap<>();

    public TypeVariableResolver() {
        this(Map.of());
    }

    public TypeVariableResolver(Map<TypeVariable<?>, Type> knownVariables) {
        this.resolvedVariables = Map.copyOf(knownVariables);
        this.resolvedTypesCache.putAll(resolvedVariables);
    }

    public static TypeVariableResolver withKnownTypesOf(Type type) {
        return new TypeVariableResolver(getKnownTypes(type));
    }

    public TypeVariableResolver where(TypeVariable<?> variable, Type type) {
        final Map<TypeVariable<?>, Type> resolvedVariables = new HashMap<>(this.resolvedVariables);
        resolvedVariables.put(variable, type);
        return new TypeVariableResolver(resolvedVariables);
    }

    public TypeVariableResolver where(Map<TypeVariable<?>, Type> knownVariables) {
        final Map<TypeVariable<?>, Type> resolvedVariables = new HashMap<>(this.resolvedVariables);
        resolvedVariables.putAll(knownVariables);
        return new TypeVariableResolver(resolvedVariables);
    }

    private static Map<TypeVariable<?>, Type> getKnownTypes(Type type) {
        if (type == null) {
            // do nothing
            return Collections.emptyMap();
        }

        final Map<TypeVariable<?>, Type> result = new HashMap<>();

        Visit.GenericType.Hierarchy.of(type, new RecursiveVisitor() {

            @Override
            public <T> void visitRawClass(Class<T> clazz) {
                final Type normalizedType = normalize(clazz);
                // clazz has no type variables of its own and is not an array type
                if (normalizedType == clazz) {
                    visit(clazz.getEnclosingClass());
                    visit(clazz.getGenericSuperclass());

                    for (Type genericInterface : clazz.getGenericInterfaces()) {
                        visit(genericInterface);
                    }
                } else {
                    // clazz is either parametrized or an array type => call other visit*-methods
                    visit(normalizedType);
                }
            }

            @Override
            public void visitParametrizedType(ParameterizedType parameterizedType) {
                visit(parameterizedType.getOwnerType());

                final Class<?> rawType = (Class<?>) parameterizedType.getRawType();
                final TypeVariable<? extends Class<?>>[] typeParameters = rawType.getTypeParameters();
                final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                final Map<TypeVariable<?>, Type> copy = Map.copyOf(result);
                for (int i = 0; i < typeParameters.length; i++) {
                    result.put(typeParameters[i], _resolve(actualTypeArguments[i], copy));
                }

                visit(rawType.getGenericSuperclass());
                for (Type genericInterface : rawType.getGenericInterfaces()) {
                    visit(genericInterface);
                }
            }

        });

        return Collections.unmodifiableMap(result);
    }

    private static Type normalize(Class<?> clazz) {
        if (clazz.isArray()) {
            return new GenericArrayTypeImpl(normalize(clazz.getComponentType()));
        } else if (clazz.getTypeParameters().length > 0) {
            return new ParameterizedTypeImpl(clazz, clazz.getDeclaringClass(), clazz.getTypeParameters());
        } else {
            return clazz;
        }
    }

    public Type resolve(Type type) {
        Objects.requireNonNull(type);
        return resolvedTypesCache.computeIfAbsent(type, t -> _resolve(t, this.resolvedVariables));
    }

    private static Type _resolve(Type type, Map<TypeVariable<?>, Type> resolvedVariables) {
        if (type instanceof TypeVariable) {
            return resolvedVariables.getOrDefault(type, type);
        } else if (type instanceof Class<?> clazz) {
            Type normalizedType = normalize(clazz);
            if (normalizedType == clazz) {
                return clazz;
            } else {
                return _resolve(normalizedType, resolvedVariables);
            }
        } else if (type instanceof GenericArrayType arrayType) {
            // TODO: Should (T[] with {T=class C}) resolve to the class C[].class or the GenericArrayType C[] ?
            return new GenericArrayTypeImpl(_resolve(arrayType.getGenericComponentType(), resolvedVariables));
        } else if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();

            final Type resolvedOwnerType;
            if (parameterizedType.getOwnerType() != null) {
                resolvedOwnerType = _resolve(parameterizedType.getOwnerType(), resolvedVariables);
            } else {
                resolvedOwnerType = null;
            }
            return new ParameterizedTypeImpl(rawType, resolvedOwnerType,
                                             _resolve(parameterizedType.getActualTypeArguments(), resolvedVariables));
        } else if (type instanceof WildcardType wildcardType) {
            return new WildcardTypeImpl(_resolve(wildcardType.getUpperBounds(), resolvedVariables),
                                        _resolve(wildcardType.getLowerBounds(), resolvedVariables));
        } else {
            throw new UnsupportedOperationException("Cannot replace type variables in " + type);
        }
    }

    private static Type[] _resolve(Type[] types, Map<TypeVariable<?>, Type> resolvedVariables) {
        Type[] result = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = _resolve(types[i], resolvedVariables);
        }
        return result;
    }

    public boolean hasUnresolvedVariables() {
        return resolvedVariables.values()
                .stream()
                .anyMatch(type -> type instanceof WildcardType || type instanceof TypeVariable<?>);
    }

    public Set<Type> resolvedTypeClosure(Type type) {
        Set<Type> superTypes = new HashSet<>();
        superTypes.add(resolve(type));

        Type superclass = null;
        if (type instanceof final Class<?> clazz) {
            superclass = clazz.getGenericSuperclass();
        } else if (type instanceof final ParameterizedType parameterizedType) {
            superclass = ((Class<?>) parameterizedType.getRawType()).getGenericSuperclass();
        }
        if (superclass != null) {
            superTypes.addAll(resolvedTypeClosure(superclass));
        }
        superTypes.addAll(getResolvedInterfaces(type));

        return superTypes;
    }

    private Set<Type> getResolvedInterfaces(Type type) {
        Set<Type> allInterfaces = new HashSet<>();

        Type[] directInterfaces = new Class<?>[0];
        if (type instanceof Class<?> clazz) {
            directInterfaces = clazz.getGenericInterfaces();
        } else if (type instanceof final ParameterizedType parameterizedType) {
            directInterfaces = ((Class<?>) parameterizedType.getRawType()).getGenericInterfaces();
        }
        for (Type superInterface : directInterfaces) {
            allInterfaces.add(resolve(superInterface));
            allInterfaces.addAll(getResolvedInterfaces(superInterface));
        }

        return allInterfaces;
    }
}
