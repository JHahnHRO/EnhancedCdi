package io.github.jhahnhro.enhancedcdi.context;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Singleton;

/**
 * A context that shares all its contextual instances between all threads in which it is active.
 * <p>
 * If this was part of the CDI standard, {@link ApplicationScoped} and {@link Singleton} could be backed by a
 * {@code SharedContext}, but {@link RequestScoped}, {@link SessionScoped}, and {@link ConversationScoped} would not.
 * <p>
 * It helps to think of such a context as backed by a simple {@link java.util.Map} mapping {@link Bean}s to their
 * contextual instance as opposed to a more complicated structure that maps tuples like {@code (bean, sessionId)} to
 * contextual instances or something similar.
 *
 * @see GlobalContext
 */
public interface SharedContext extends CloseableContext {}
