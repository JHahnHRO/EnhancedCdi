package io.github.jhahnhro.enhancedcdi.events.subscription;

import javax.enterprise.event.Event;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.Prioritized;
import javax.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represent a subscription for some {@link Event CDI event} similar to an {@link javax.enterprise.event.Observes
 * observer method}.
 * <p>
 * Like observer method, subscriptions are strongly typed and recognize parametrized types in the same way. For example,
 * a subscription for type {@code List<String>} will not borrow notified when an event of type {@code List<Integer>} is
 * fired, but a subscription for the raw type {@code List} will. They also recognize {@link javax.inject.Qualifier
 * qualifiers} in the same way.
 * <p>
 * Unlike observer methods, a subscription is dynamic. They can be created, enabled, disabled, and cancelled at runtime.
 * They can be organised hierarchically in {@link SubscriptionGroup}s.
 * <p>
 * Asynchronous or transactional subscriptions are not supported.
 * <p>
 * Subscriptions are stateful. They can be enabled, disabled or cancelled. A subscription only receives events if it is
 * enabled. All subscriptions begin their life disabled, but can switch from disabled to enabled and back whenever and
 * as often as needed. A subscription can be cancelled which ends its life. A cancelled subscription does not receive
 * events nor can it be re-enabled.
 *
 * @param <T> The observed event type.
 */
public interface Subscription<T> extends Prioritized {

    Comparator<Subscription<?>> PRIORITY_COMPARATOR = Comparator.comparingInt(Subscription::getPriority);

    /**
     * Enables this subscription if it was disabled before. Does nothing if it was already enabled. Throws {@link
     * IllegalStateException} if it was already cancelled.
     *
     * @throws IllegalStateException if the subscription is already cancelled
     */
    void enable() throws IllegalStateException;

    /**
     * @return {@code true} iff this subscription is enabled.
     */
    boolean isEnabled();

    /**
     * Disables this subscription if it was enabled before. Does nothing if it was already enabled. Throws {@link
     * IllegalStateException} if it was already cancelled.
     *
     * @throws IllegalStateException if the subscription is already cancelled
     */
    void disable();


    /**
     * Cancels this subscription.
     */
    void cancel();

    /**
     * @return {@code true} iff this subscription is cancelled
     */
    boolean isCancelled();

    /**
     * Optional. Manually delivers the given event payload to this subscription without {@link Event#fire(Object)
     * firing} any events and without notifying any other subscriptions. If this subscription does not {@link
     * #supportsManualDelivery support manual delivery} an {@link UnsupportedOperationException} is thrown.
     *
     * @param event event payload. Must not be null.
     * @throws UnsupportedOperationException if this subscription does not {@link #supportsManualDelivery support manual
     *                                       delivery}
     * @throws IllegalStateException         if this subscription is cancelled.
     */
    void deliver(T event);

    /**
     * @return {@code true} if {@link #deliver(Object)} is allowed to be called
     */
    boolean supportsManualDelivery();

    /**
     * Different subscriptions borrow notified of an event in ascending order of their priority. No order is defined for
     * subscriptions of equal priority.
     * <p>
     * Note however that subscriptions always borrow notified AFTER regular observer methods.
     */
    @Override
    int getPriority();

    /**
     * Obtains the {@linkplain javax.enterprise.event observed event type}.
     *
     * @return the observed event {@linkplain Type type}
     */
    Type getObservedType();

    /**
     * Obtains the set of {@linkplain javax.enterprise.event observed event qualifiers}.
     *
     * @return the observed event {@linkplain javax.inject.Qualifier qualifiers}
     */
    Set<Annotation> getObservedQualifiers();

    /**
     * A builder for subscriptions. An instance can be obtained from {@link SubscriptionGroup#addSubscription()}.
     *
     * @param <T> the observed event type the subscription will have. Can be set with {@link #setType(Class)} or {@link
     *            #setType(TypeLiteral)}.
     */
    interface Builder<T> {


        /**
         * Sets the qualifiers for the subscription
         *
         * @return this
         */
        Builder<T> setQualifiers(Annotation... qualifiers);

        /**
         * Sets the type of the events that will be delivered to the subscription. For type safety, this method throws
         * {@link IllegalStateException} if called if a callback has already been set.
         *
         * @return this
         * @throws IllegalStateException if a callback has already been set.
         */
        <U> Builder<U> setType(Class<U> observedEventType);


        /**
         * Sets the type of the events that will be delivered to the subscription. For type safety, this method throws
         * {@link IllegalStateException} if called if a callback has already been set.
         *
         * @return this
         * @throws IllegalStateException if a callback has already been set.
         */
        <U> Builder<U> setType(TypeLiteral<U> observedEventType);

        /**
         * Sets the consumer for this subscription that gets called upon (automatic or manual) delivery of an event.
         * {@link Subscription#supportsManualDelivery() Manual delivery} will be supported for the subscription.
         *
         * @param callback A {@link Consumer} that gets called whenever an event gets delivered to this subscription.
         *                 Must not be null.
         * @return this
         */
        Builder<T> setEventConsumer(Consumer<T> callback);

        /**
         * Sets the consumer for this subscription that gets called upon automatic delivery of an event. {@link
         * Subscription#supportsManualDelivery() Manual delivery} will NOT be supported for the subscription.
         *
         * @param callback A {@link BiConsumer} that gets called whenever an event gets delivered to this subscription.
         *                 Must not be null.
         * @return this
         */
        Builder<T> setEventConsumer(BiConsumer<T, EventMetadata> callback);

        /**
         * Sets a descriptive name of the subscription that will be used in {@link Subscription#toString()}.
         *
         * @param name a descriptive name. Must not be null.
         * @return this
         */
        Builder<T> setName(String name);

        /**
         * Sets the priority of the subscription. Events will be delivered to subscriptions in ascending order of their
         * priority. If no priority is set, the default priority {@link ObserverMethod#DEFAULT_PRIORITY} will be used.
         *
         * @param priority the priority
         * @return this
         */
        Builder<T> setPriority(int priority);


        /**
         * @return the newly built subscription
         * @throws IllegalStateException if no callback was set or if the subscription group this builder was created by
         *                               has been closed in the meantime.
         */
        Subscription<T> build() throws IllegalStateException;
    }
}
