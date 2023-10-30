package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.multiton.BeanParameter;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedAnnotation;
import io.github.jhahnhro.enhancedcdi.multiton.ParametrizedBean;
import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

@ApplicationScoped
@ParametrizedBean
@ParametrizedAnnotation(TestQualifier.class)
public class ParametrizedBeanClass {

    @Inject
    @BeanParameter
    Color myColor;

    @Inject
    @ParametrizedAnnotation(TestQualifier.class)
    Event<Color> colorEvent;

    public Color getMyColor() {
        return myColor;
    }

    public void fireEvent() {
        this.colorEvent.fire(myColor);
    }
}
