package io.github.jhahnhro.enhancedcdi.metadata;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

public record InjectionPointImpl
        (Type type, Set<Annotation> qualifiers, Bean<?> bean, Member member, Annotated annotated, boolean isDelegate,
         boolean isTransient) implements InjectionPoint, Serializable {

    public InjectionPointImpl {
        Objects.requireNonNull(type);
        qualifiers = Set.copyOf(qualifiers);
    }

    public InjectionPointImpl(Type type, Set<Annotation> qualifiers) {
        this(type, qualifiers, null, null, null, false, false);
    }

    public InjectionPointImpl(Type type, Annotation... qualifiers) {
        this(type, Set.of(qualifiers));
    }

    /**
     * Convenience method that changes type and qualifiers of a given {@link InjectionPoint}. If the given injection
     * point is {@code null} then a new {@code InjectionPoint} is returned that has the given type and qualifiers, but
     * all other components {@code null}.
     *
     * @param original   an {@link InjectionPoint}
     * @param type       the new type
     * @param qualifiers the new qualifiers
     * @return
     */
    public static InjectionPointImpl mutate(InjectionPoint original, Type type, Set<Annotation> qualifiers) {
        if (original == null) {
            return new InjectionPointImpl(type, qualifiers);
        }
        return new InjectionPointImpl(type, qualifiers, original.getBean(), original.getMember(),
                                      original.getAnnotated(), original.isDelegate(), original.isTransient());
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    @Override
    public Member getMember() {
        return member;
    }

    @Override
    public Annotated getAnnotated() {
        return annotated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof InjectionPoint that && this.type.equals(that.getType()) && this.qualifiers.equals(
                that.getQualifiers()) && Objects.equals(this.bean, that.getType()) && Objects.equals(this.member,
                                                                                                     that.getMember())
               && Objects.equals(this.annotated, that.getAnnotated()) && this.isDelegate == that.isDelegate()
               && this.isTransient == that.isTransient();

    }

    @Override
    public int hashCode() {
        return Objects.hash(type, qualifiers, bean, member, annotated, isDelegate, isTransient);
    }
}
