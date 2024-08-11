package io.github.jhahnhro.enhancedcdi.types;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jhahnhro.enhancedcdi.util.Iteration;

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

    private static Map<TypeVariable<?>, Type> getKnownTypes(Type type) {
        if (type == null) {
            // do nothing
            return Collections.emptyMap();
        }

        final Map<TypeVariable<?>, Type> result = new HashMap<>();

        Iteration.depthFirstSearch(type, TypeVariableResolver::getRelatedTypes)
                .stream()
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .forEach(relatedType -> {
                    final Class<?> rawType = (Class<?>) relatedType.getRawType();
                    final TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
                    final Type[] actualTypeArguments = relatedType.getActualTypeArguments();
                    final Map<TypeVariable<?>, Type> copy = Map.copyOf(result);
                    for (int i = 0; i < typeParameters.length; i++) {
                        result.put(typeParameters[i], resolveInternal(actualTypeArguments[i], copy));
                    }
                });
        result.entrySet().removeIf(entry -> Objects.equals(entry.getKey(), entry.getValue()));

        return Collections.unmodifiableMap(result);
    }

    private static Stream<Type> getRelatedTypes(Type type) {
        final Stream<Type> relatedTypes = switch (type) {
            case Class<?> clazz -> clazz.isArray() ?
                    // unpack array class
                    Stream.of(clazz.getComponentType()) :
                    // otherwise use enclosing class and generic super types
                    Stream.concat(Stream.of(clazz.getEnclosingClass(), clazz.getGenericSuperclass()),
                                  Stream.of(clazz.getGenericInterfaces()));
            case ParameterizedType parameterizedType -> {
                final Class<?> rawType = (Class<?>) parameterizedType.getRawType();
                yield Stream.concat(Stream.of(parameterizedType.getOwnerType(), rawType.getGenericSuperclass()),
                                    Stream.of(rawType.getGenericInterfaces()));
            }
            case GenericArrayType genericArrayType -> Stream.of(genericArrayType.getGenericComponentType());
            default -> Stream.empty();
        };
        return relatedTypes.filter(Objects::nonNull);
    }

    private static Type normalize(Class<?> clazz) {
        if (clazz.isArray()) {
            final Type normalizedComponentType = normalize(clazz.getComponentType());
            if (clazz.getComponentType() != normalizedComponentType) {
                return new GenericArrayTypeImpl(normalizedComponentType);
            }
        } else if (clazz.getTypeParameters().length > 0) {
            return new ParameterizedTypeImpl(clazz, clazz.getDeclaringClass(), clazz.getTypeParameters());
        }
        return clazz;
    }

    private static Type resolveInternal(Type type, Map<TypeVariable<?>, Type> resolvedVariables) {
        return switch (type) {
            case null -> null;
            case TypeVariable<?> typeVariable -> resolvedVariables.getOrDefault(typeVariable, type);
            case Class<?> clazz -> {
                Type normalizedType = normalize(clazz);
                yield normalizedType == clazz ? clazz : resolveInternal(normalizedType, resolvedVariables);
            }
            case GenericArrayType arrayType ->
                    new GenericArrayTypeImpl(resolveInternal(arrayType.getGenericComponentType(), resolvedVariables));
            case ParameterizedType parameterizedType ->
                    new ParameterizedTypeImpl((Class<?>) parameterizedType.getRawType(),
                                              resolveInternal(parameterizedType.getOwnerType(), resolvedVariables),
                                              resolveInternal(parameterizedType.getActualTypeArguments(),
                                                              resolvedVariables));
            case WildcardType wildcardType ->
                    new WildcardTypeImpl(resolveInternal(wildcardType.getUpperBounds(), resolvedVariables),
                                         resolveInternal(wildcardType.getLowerBounds(), resolvedVariables));
            default -> throw new UnsupportedOperationException("Cannot replace type variables in " + type);
        };
    }

    private static Type[] resolveInternal(Type[] types, Map<TypeVariable<?>, Type> resolvedVariables) {
        Type[] result = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = resolveInternal(types[i], resolvedVariables);
        }
        return result;
    }

    public TypeVariableResolver where(TypeVariable<?> variable, Type type) {
        final Map<TypeVariable<?>, Type> newResolvedVariables = new HashMap<>(this.resolvedVariables);
        newResolvedVariables.put(variable, type);
        return new TypeVariableResolver(newResolvedVariables);
    }

    public TypeVariableResolver where(Map<TypeVariable<?>, Type> knownVariables) {
        final Map<TypeVariable<?>, Type> newResolvedVariables = new HashMap<>(this.resolvedVariables);
        newResolvedVariables.putAll(knownVariables);
        return new TypeVariableResolver(newResolvedVariables);
    }

    public Type resolve(Type type) {
        Objects.requireNonNull(type);
        return resolvedTypesCache.computeIfAbsent(type, t -> resolveInternal(t, this.resolvedVariables));
    }

    public boolean hasUnresolvedVariables() {
        return resolvedVariables.values()
                .stream()
                .anyMatch(type -> type instanceof WildcardType || type instanceof TypeVariable<?>);
    }

    public Set<Type> resolvedTypeClosure(Type type) {
        if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
            throw new IllegalArgumentException();
        }
        final Class<?> erasedType = Types.erasure(type);

        final Set<Class<?>> superClasses = Types.superClasses(erasedType);
        final Set<Class<?>> superInterfaces = Types.superInterfaces(erasedType);
        return Stream.concat(superClasses.stream(), superInterfaces.stream())
                .map(this::resolve)
                .collect(Collectors.toUnmodifiableSet());
    }
}
