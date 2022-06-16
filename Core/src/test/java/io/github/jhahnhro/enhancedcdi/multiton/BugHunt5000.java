package io.github.jhahnhro.enhancedcdi.multiton;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jboss.weld.util.AnnotatedTypes;
import org.junit.jupiter.api.Test;

import javax.enterprise.event.Observes;
import javax.enterprise.event.Reception;
import javax.enterprise.event.TransactionPhase;
import javax.enterprise.inject.spi.*;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnableWeld
public class BugHunt5000 {

    Weld weld = WeldInitiator.createWeld()
            .addExtension(new MyExtension())
            .addBeanClass(io.github.jhahnhro.enhancedcdi.multiton.BugHunt5000.A.class);

    @WeldSetup
    WeldInitiator w = WeldInitiator.of(weld);

    @Inject
    BeanManager beanManager;

    @Test
    void testAnnotatedMethodsNonEquality() {
        final MyExtension myExtension = beanManager.getExtension(MyExtension.class);
        final AnnotatedMethod<?> originalMethod = myExtension.originalMethod;
        final AnnotatedMethod<?> observerMethod = myExtension.observerMethod;

        assertThat(originalMethod).isNotEqualTo(observerMethod);
        assertThat(originalMethod.getDeclaringType()).isNotEqualTo(observerMethod.getDeclaringType());
    }

    @Test
    void testAnnotatedMethodCreation() {
        final AnnotatedType<B> annotatedType = beanManager.createAnnotatedType(B.class);

        final BeanAttributes<?> beanAttributes = beanManager.createBeanAttributes(new AnnotatedMethod<B>() {
            @Override
            public Method getJavaMember() {
                return B.class.getDeclaredMethods()[0];
            }

            @Override
            public List<AnnotatedParameter<B>> getParameters() {
                return Collections.emptyList();
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public AnnotatedType<B> getDeclaringType() {
                return annotatedType;
            }

            @Override
            public Type getBaseType() {
                return Integer.class;
            }

            @Override
            public Set<Type> getTypeClosure() {
                return Set.of(Integer.class);
            }

            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
                return null;
            }

            @Override
            public Set<Annotation> getAnnotations() {
                return Collections.emptySet();
            }

            @Override
            public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
                return false;
            }
        });
    }

    private static class MyExtension implements Extension {
        private AnnotatedMethod<?> originalMethod;
        private AnnotatedMethod<?> observerMethod;

        <X> void configureMethod(@Observes ProcessAnnotatedType<io.github.jhahnhro.enhancedcdi.multiton.BugHunt5000.A> pat) {
            AnnotatedType<io.github.jhahnhro.enhancedcdi.multiton.BugHunt5000.A> annotatedType = pat.getAnnotatedType();
            originalMethod = annotatedType.getMethods().iterator().next();
            var typeConfigurator = pat.configureAnnotatedType();

            class ObservesLiteral extends AnnotationLiteral<Observes> implements Observes {
                @Override
                public Reception notifyObserver() {return Reception.ALWAYS;}

                @Override
                public TransactionPhase during() {return TransactionPhase.IN_PROGRESS;}
            }
            var methodConfigurator = typeConfigurator.methods().iterator().next();
            var parameterConfigurator = methodConfigurator.params().get(0);
            parameterConfigurator.add(new ObservesLiteral());
        }

        <T, X> void findObserver(@Observes ProcessObserverMethod<T, X> pom) {
            this.observerMethod = pom.getAnnotatedMethod();
        }
    }

    private static class A {
        void a(Object param) {}
    }

    private static class B {
        Integer b() {
            return 0;
        }
    }
}
