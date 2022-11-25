package io.github.jhahnhro.enhancedcdi.pooled;

import io.github.jhahnhro.enhancedcdi.util.BeanInstance;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

public class BeanPool<T> extends LazyBlockingPool<BeanInstance<T>> {

    private final Bean<T> bean;
    private final BeanManager beanManager;

    public BeanPool(BeanManager beanManager, Bean<T> bean, int capacity) {
        super(0, capacity, BeanInstance::destroy);
        if (bean.getScope() != Dependent.class) {
            throw new IllegalArgumentException("Beans can only be pooled if they are @Dependent scoped");
        }
        this.bean = bean;
        this.beanManager = beanManager;
    }

    @Override
    protected BeanInstance<T> create() {
        return BeanInstance.createContextualInstance(bean, beanManager.createCreationalContext(null));
    }
}
