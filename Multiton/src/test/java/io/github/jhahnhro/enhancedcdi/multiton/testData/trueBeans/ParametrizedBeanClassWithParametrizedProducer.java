package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import java.util.List;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Shape;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
@ParametrizedBean
@ParametrizedAnnotation(TestQualifier.class)
public class ParametrizedBeanClassWithParametrizedProducer {

    @Inject
    @BeanParameter
    Color myColor;

    @Produces
    @ParametrizedBean
    @ParametrizedAnnotation(TestQualifier.class)
    @ParametrizedAnnotation(SomeOtherQualifier.class)
    public List<Object> weirdList(@BeanParameter Color outerParameter, @BeanParameter Shape innerParameter) {
        return List.of(outerParameter, innerParameter);
    }
}
