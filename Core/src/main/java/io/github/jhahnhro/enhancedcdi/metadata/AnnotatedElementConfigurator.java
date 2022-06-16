package io.github.jhahnhro.enhancedcdi.metadata;


import javax.enterprise.inject.spi.Annotated;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AnnotatedElementConfigurator<A extends Annotated, SELF extends AnnotatedElementConfigurator<A,
        SELF>> {
    protected final A original;
    private final Set<Annotation> annotations;
    private final Normalizer normalizer = new Normalizer();

    AnnotatedElementConfigurator(A original) {
        this.original = Objects.requireNonNull(original);
        this.annotations = original.getAnnotations()
                .stream()
                .flatMap(normalizer::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @return the original Annotated element
     */
    public A getAnnotated() {
        return original;
    }

    /**
     * @return the current set of annotations on annotated element
     */
    protected Set<Annotation> getAnnotations() {
        return Set.copyOf(annotations);
    }

    /**
     * @return a (unmodifiable) snapshot of the current configuration. The configurator can still be used after calling
     * this method, but further changes have no effect on the returned object.
     */
    public abstract A build();

    /**
     * @return this
     */
    protected SELF self() {
        return (SELF) this;
    }

    public SELF add(Annotation annotation) {
        Objects.requireNonNull(annotation);
        normalizer.normalize(annotation).forEach(annotations::add);
        return self();
    }

    public SELF addAll(Collection<Annotation> annotation) {
        Objects.requireNonNull(annotation).stream().flatMap(normalizer::normalize).forEach(annotations::add);
        return self();
    }

    public SELF remove(Predicate<Annotation> predicate) {
        annotations.removeIf(predicate);
        return self();
    }

    public SELF removeAll() {
        annotations.clear();
        return self();
    }

    public SELF replaceIf(Predicate<Annotation> predicate,
                          Function<? super Annotation, ? extends Annotation> transformer) {
        List<Annotation> ann = new ArrayList<>(annotations);
        for (var iterator = ann.listIterator(); iterator.hasNext(); ) {
            Annotation old = iterator.next();
            if (predicate.test(old)) {
                iterator.set(transformOrThrow(transformer, old));
            }
        }
        this.annotations.clear();
        this.annotations.addAll(ann);
        return self();
    }

    private <X extends Annotation> Annotation transformOrThrow(Function<? super X, ? extends Annotation> transformer,
                                                               X input) {
        return Objects.requireNonNull(transformer.apply(input), "The transformer returned null on " + input);
    }

    public SELF reset() {
        return this.removeAll().addAll(original.getAnnotations());
    }

}
