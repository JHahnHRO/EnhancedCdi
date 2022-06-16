package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.RoutingKeyPattern;

import javax.enterprise.context.ApplicationScoped;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class RoutingKeyPatternCache {

    private final Map<AnnotatedElement, Optional<Pattern>> cache;

    RoutingKeyPatternCache() {
        cache = new ConcurrentHashMap<>();
    }

    public Optional<Pattern> get(AnnotatedElement method) {
        return cache.computeIfAbsent(method, this::newPattern);
    }

    private Optional<Pattern> newPattern(AnnotatedElement element) {
        final RoutingKeyPattern annotation = element.getAnnotation(RoutingKeyPattern.class);
        if (annotation != null) {
            return Optional.of(Pattern.compile(annotation.value()));
        }
        return Optional.empty();
    }
}
