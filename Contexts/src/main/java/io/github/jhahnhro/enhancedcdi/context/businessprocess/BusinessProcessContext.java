package io.github.jhahnhro.enhancedcdi.context.businessprocess;

import io.github.jhahnhro.enhancedcdi.context.MultiThreadedNonSharedContext;

import java.lang.annotation.Annotation;

public class BusinessProcessContext extends MultiThreadedNonSharedContext<Integer> {
    @Override
    public Class<? extends Annotation> getScope() {
        return BusinessProcessScoped.class;
    }
}
