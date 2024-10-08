package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class BeanWithParametrizedProducer {

    @Produces
    @ParametrizedBean
    @ParametrizedAnnotation(TestQualifier.class)
    public String colorString(@BeanParameter Color myColor) {
        return myColor.name();
    }
}
