package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Shape;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.List;

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
