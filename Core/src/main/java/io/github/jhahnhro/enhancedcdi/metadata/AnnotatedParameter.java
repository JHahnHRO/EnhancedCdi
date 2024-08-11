package io.github.jhahnhro.enhancedcdi.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Set;

public class AnnotatedParameter<X> extends AnnotatedElement<Parameter>
        implements jakarta.enterprise.inject.spi.AnnotatedParameter<X> {
    private final int position;
    private final AnnotatedCallable<X> declaringCallable;

    protected AnnotatedParameter(Parameter parameter, Set<Annotation> annotations,
                                 AnnotatedCallable<X> declaringCallable) {
        super(parameter, annotations, declaringCallable.typeResolver.resolve(parameter.getParameterizedType()),
              declaringCallable.typeResolver.resolvedTypeClosure(parameter.getParameterizedType()));

        this.position = getPosition(parameter);
        if (!declaringCallable.getJavaMember().equals(parameter.getDeclaringExecutable())) {
            throw new IllegalArgumentException(
                    "The declaring executable of the given parameter must be equal to the underlying method or "
                    + "constructor of the given declaringCallable");
        }
        this.declaringCallable = declaringCallable;
    }

    private int getPosition(Parameter parameter) {
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            if (p.equals(parameter)) {
                return i;
            }
        }
        // cannot happen
        return -1;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public AnnotatedCallable<X> getDeclaringCallable() {
        return declaringCallable;
    }

    @Override
    public Parameter getJavaParameter() {
        return annotatedElement;
    }
}
