package io.github.jhahnhro.enhancedcdi.context;

import javax.enterprise.context.spi.AlterableContext;

/**
 * A context that shares all its contextual instances between all threads in which it is active.
 * <p>
 * If this was part of the CDI standard, {@link javax.enterprise.context.ApplicationScoped} and
 * {@link javax.inject.Singleton} would be backed by a {@code SharedContext}, but
 * {@link javax.enterprise.context.RequestScoped}, {@link javax.enterprise.context.SessionScoped}, and
 * {@link javax.enterprise.context.ConversationScoped} would not.
 * <p>
 * It helps to think of such a context as backed by a {@link java.util.Map} mapping
 * {@link javax.enterprise.inject.spi.Bean}s to their contextual instance.
 */
public interface SharedContext extends AlterableContext {}
