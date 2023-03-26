package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

public class MutableInjectionPoint implements InjectionPoint {

    private Type type;
    private Set<Annotation> qualifiers;
    private Bean<?> bean;
    private Member member;
    private Annotated annotated;
    private boolean isDelegate;
    private boolean isTransient;

    public MutableInjectionPoint() {
    }

    public MutableInjectionPoint(InjectionPoint injectionPoint) {
        this.type = injectionPoint.getType();
        this.qualifiers = new HashSet<>(injectionPoint.getQualifiers());
        this.qualifiers.removeIf(q -> q.annotationType() == Default.class);
        this.bean = injectionPoint.getBean();
        this.member = injectionPoint.getMember();
        this.annotated = injectionPoint.getAnnotated();
        this.isDelegate = injectionPoint.isDelegate();
        this.isTransient = injectionPoint.isTransient();
    }

    @Override
    public Type getType() {
        return type;
    }

    public MutableInjectionPoint setType(Type type) {
        this.type = Objects.requireNonNull(type);
        return this;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers.isEmpty() ? Set.of(Default.Literal.INSTANCE) : qualifiers;
    }

    public MutableInjectionPoint setQualifiers(Set<Annotation> qualifiers) {
        this.qualifiers.clear();
        this.qualifiers.addAll(qualifiers);
        return this;
    }

    public MutableInjectionPoint setQualifiers(Annotation... qualifiers) {
        this.qualifiers.clear();
        Collections.addAll(this.qualifiers, qualifiers);
        return this;
    }

    public MutableInjectionPoint addQualifier(Annotation qualifier) {
        this.qualifiers.add(qualifier);
        return this;
    }

    public MutableInjectionPoint addQualifiers(Annotation... qualifiers) {
        Collections.addAll(this.qualifiers, qualifiers);
        return this;
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    public MutableInjectionPoint setBean(Bean<?> bean) {
        this.bean = bean;
        return this;
    }


    @Override
    public Member getMember() {
        return member;
    }

    public MutableInjectionPoint setMember(Member member) {
        this.member = member;
        return this;
    }

    @Override
    public Annotated getAnnotated() {
        return annotated;
    }

    public MutableInjectionPoint setAnnotated(Annotated annotated) {
        this.annotated = annotated;
        return this;
    }

    @Override
    public boolean isDelegate() {
        return isDelegate;
    }

    public MutableInjectionPoint setDelegate(boolean isDelegate) {
        this.isDelegate = isDelegate;
        return this;
    }

    @Override
    public boolean isTransient() {
        return isTransient;
    }

    public MutableInjectionPoint setTransient(boolean isTransient) {
        this.isTransient = isTransient;
        return this;
    }
}
