package io.github.jhahnhro.enhancedcdi.events.subscription;

import java.util.List;

/**
 * Represents a collection of {@link Subscription subscriptions} with a common maximum lifetime. For example, this could
 * be everything one particular receiving object is subscribed to and once the receiver ends its life, all its
 * subscriptions end.
 * <p>
 * SubscriptionGroups can be organized hierarchically with the help of {@link #addSubgroup(String)}.
 */
public interface SubscriptionGroup extends AutoCloseable {

    /**
     * Returns an unmodifiable list of all subscriptions of this subscription group at the moment this method was
     * called, including subscriptions contained in subgroups and subscriptions that are already cancelled. The
     * resulting list is sorted in some order compatible with their {@link Subscription#getPriority() priority}, i.e.
     * subscriptions with lower priority occur before subscriptions with higher priority. The order in which
     * subscriptions with the same priority is not defined.
     *
     * @return all subscriptions of this subscription group
     */
    List<Subscription<?>> getSubscriptions();

    /**
     * Returns a builder that will be added to this subscription group if and when {@link Subscription.Builder#build()}
     * is called. Note that the {@code build} method will throw {@code IllegalStateException} if this group had been
     * closed in the meantime.
     *
     * @return a builder for a new subscription
     * @throws IllegalStateException if this SubscriptionGroup is already closed
     */
    Subscription.Builder<Object> addSubscription() throws IllegalStateException;

    /**
     * Adds a new subgroup to this group that can be used to group together subscriptions with a common, shorter
     * lifespan than this group, i.e. one can call close() on the subgroup to cancel all contained subscriptions without
     * cancelling the other subscriptions in this group.
     *
     * @param name A name of the new subgroup
     * @return the new subgroup
     * @throws IllegalStateException if this SubscriptionGroup is already closed
     */
    SubscriptionGroup addSubgroup(String name);

    /**
     * Closes this group and cancels all subscription in this group and all of its subgroups. A new subscription or new
     * subgroup cannot be added to this group once it is closed.
     */
    @Override
    void close();
}
