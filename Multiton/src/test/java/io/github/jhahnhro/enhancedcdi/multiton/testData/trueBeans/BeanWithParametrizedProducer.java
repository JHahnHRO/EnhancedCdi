package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@Dependent
public class BeanWithParametrizedProducer {

    @Produces
    @ParametrizedBean
    @ParametrizedAnnotation(TestQualifier.class)
    public String colorString(@BeanParameter Color myColor) {
        return myColor.name();
    }
}
