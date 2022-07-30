package io.github.jhahnhro.enhancedcdi.context.businessprocess;

import javax.enterprise.context.NormalScope;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@NormalScope
public @interface BusinessProcessScoped {}
