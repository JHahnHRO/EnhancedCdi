package io.github.jhahnhro.enhancedcdi.multiton.impl;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class MapBeanTest {

    @Mock
    private CreationalContext<String> childContext;
    @Mock
    private CreationalContext<Map<Color, String>> parentContext;
    @Mock
    private BeanManager beanManager;
    @Mock
    private Bean<String> bean;

    private final BeanAttributes<String> beanAttributes = new BeanAttributes<>() {

        @Override
        public Set<Type> getTypes() {
            return Set.of(String.class, Object.class);
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return RequestScoped.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    };

    private final BeanData<Color, String> beanDataStub = new BeanData<>(beanAttributes, Color.class, null, null) {
        @Override
        public Type beanType() {
            return String.class;
        }

    };

    MapBean<Color, String> mapBean;

    @BeforeEach
    void setUp() {
        beanDataStub.resultingBeans().put(Color.RED, bean);
        beanDataStub.resultingBeans().put(Color.BLUE, bean);
        mapBean = new MapBean<>(beanDataStub, beanManager);
    }

    @Test
    void mapBeanShouldHaveDependentScope(){
        Assertions.assertThat(mapBean.getScope()).isEqualTo(Dependent.class);
    }

    @Test
    void create() {
        // setup
        Mockito.when(beanManager.createCreationalContext(bean)).thenReturn(childContext);
        Mockito.when(bean.create(childContext)).thenReturn("beanInstance");

        // run the test
        final Map<Color, String> mapInstance = mapBean.create(parentContext);

        // verify
        Assertions.assertThat(mapInstance).hasSize(2);
        Mockito.verify(bean, Mockito.times(2)).create(childContext);
    }

    @Test
    void destroy() {
        // setup
        Mockito.when(beanManager.createCreationalContext(bean)).thenReturn(childContext);
        Mockito.when(bean.create(childContext)).thenReturn("beanInstance");
        final Map<Color, String> mapInstance = mapBean.create(parentContext);

        // run the test
        mapBean.destroy(mapInstance, parentContext);

        // verify
        Mockito.verify(bean, Mockito.times(2)).destroy(ArgumentMatchers.any(), ArgumentMatchers.eq(childContext));
        Mockito.verify(childContext, Mockito.times(2)).release();
        Mockito.verify(parentContext).release();
    }
}