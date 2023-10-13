package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface WithConfirms {}
