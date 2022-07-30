package io.github.jhahnhro.enhancedcdi.events.subscription.impl;

import io.github.jhahnhro.enhancedcdi.events.subscription.Subscription;
import io.github.jhahnhro.enhancedcdi.events.subscription.SubscriptionGroup;
import io.github.jhahnhro.enhancedcdi.util.Cleaning;

import javax.annotation.PreDestroy;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.lang.ref.Cleaner;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * An {@link Inject injectable} subscription group that has the scope of the bean it is injected into.
 */
@Dependent
public class ManagedSubscriptionGroup implements SubscriptionGroup {

    private final Collection<SubscriptionImpl<?>> managedSubscriptions = new ArrayList<>();
    private final Collection<ManagedSubscriptionGroup> managedSubgroups = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final SubscriptionRegistry subscriptionRegistry;
    private final String groupName;

    private final Cleaner.Cleanable cleanable;
    private volatile boolean closed = false;

    @Inject
    ManagedSubscriptionGroup(SubscriptionRegistry subscriptionRegistry, InjectionPoint injectionPoint) {
        this(subscriptionRegistry, extractGroupName(injectionPoint));
    }

    ManagedSubscriptionGroup(SubscriptionRegistry subscriptionRegistry, String groupName) {
        this.subscriptionRegistry = subscriptionRegistry;
        this.groupName = groupName;

        // if someone misuses Instance<> to create a SubscriptionGroup without ever calling Instance.destroy(), it can
        // be the case that the CDI container never calls preDestroy(). Thus, we also register with a Cleaner to
        // ensure that the subscriptions are removed from the registry and cancelled.
        cleanable = Cleaning.DEFAULT_CLEANER.register(this, new CleaningAction(this.managedSubscriptions,
                                                                               this.subscriptionRegistry,
                                                                               this.managedSubgroups));
    }

    private static String extractGroupName(InjectionPoint injectionPoint) {
        // injectionPoint may be null if instance is created manually with beanManager.getReference
        // injectionPoint.annotated may be null if instance was obtained programmatically with beanManager
        // .getInstance().select(...).borrow()
        Optional<Annotated> annotated = Optional.ofNullable(injectionPoint).map(InjectionPoint::getAnnotated);

        return annotated.filter(AnnotatedField.class::isInstance)
                .map(AnnotatedField.class::cast)
                .map(ManagedSubscriptionGroup::extractFieldName)
                .or(() -> annotated.filter(AnnotatedParameter.class::isInstance)
                        .map(AnnotatedParameter.class::cast)
                        .map(ManagedSubscriptionGroup::extractParameterName))
                .orElseGet(() -> "Subscription#" + UUID.randomUUID());
    }

    private static String extractFieldName(AnnotatedField<?> field) {
        return field.getDeclaringType().getJavaClass().getCanonicalName() + "." + field.getJavaMember().getName();
    }

    private static String extractParameterName(AnnotatedParameter<?> annotatedParameter) {
        Parameter parameter = annotatedParameter.getJavaParameter();
        Executable executable = parameter.getDeclaringExecutable();
        String className = executable.getDeclaringClass().getCanonicalName();
        String executableName = executable.getName();
        String parameterNames = Arrays.stream(executable.getParameters())
                .map(Parameter::getParameterizedType)
                .map(Objects::toString)
                .collect(Collectors.joining(",", "(", ")"));
        return className + "." + executableName + parameterNames + "/" + parameter.getName();
    }

    @PreDestroy
    private void preDestroy() {
        close();
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            closed = true;
            cleanable.clean();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Subscription<?>> getSubscriptions() {
        List<Subscription<?>> subscriptions = new ArrayList<>();

        lock.readLock().lock();
        try {
            managedSubscriptions.stream().map(ManagedSubscription::new).forEach(subscriptions::add);
            managedSubgroups.stream().map(ManagedSubscriptionGroup::getSubscriptions).forEach(subscriptions::addAll);
        } finally {
            lock.readLock().unlock();
        }

        subscriptions.sort(Subscription.PRIORITY_COMPARATOR);
        return Collections.unmodifiableList(subscriptions);
    }

    @Override
    public SubscriptionGroup addSubgroup(String name) {
        lock.writeLock().lock();
        try {
            checkNotClosed();
            ManagedSubscriptionGroup subscriptionGroup = new ManagedSubscriptionGroup(subscriptionRegistry,
                                                                                      this.groupName + "/" + name);
            this.managedSubgroups.add(subscriptionGroup);
            return subscriptionGroup;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Subscription.Builder<Object> addSubscription() {
        checkNotClosed();
        return new ManagedSubscriptionBuilder<>();
    }

    /**
     * gets called by the Builder returned by {@link #addSubscription()} when {@code build()} is called.
     *
     * @param subscription
     */
    private void addSubscription(SubscriptionImpl<?> subscription) {
        lock.writeLock().lock();
        try {
            checkNotClosed();
            managedSubscriptions.add(subscription);
        } finally {
            lock.writeLock().unlock();
        }
        subscriptionRegistry.register(subscription);
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("The SubscriptionGroup " + groupName + " is already closed.");
        }
    }

    /**
     * wraps a subscription in this group in order to intercept calls to {@link Subscription#cancel()}. Only instances
     * of this class leave this package.
     *
     * @param <T>
     */
    private class ManagedSubscription<T> extends ForwardingSubscription<T> {

        ManagedSubscription(Subscription<T> delegate) {
            super(delegate);
        }

        @Override
        public void cancel() {
            subscriptionRegistry.unregister(delegate);
            delegate.cancel();
        }
    }

    /**
     * An implementation of {@link Subscription.Builder} that add freshly built subscriptions to this group.
     *
     * @param <T>
     */
    private class ManagedSubscriptionBuilder<T> extends AbstractSubscriptionBuilder<T> {
        @Override
        public Subscription<T> build() {
            SubscriptionImpl<T> subscription = (SubscriptionImpl<T>) super.build();
            addSubscription(subscription);
            return new ManagedSubscription<>(subscription);
        }
    }

    /**
     * An action that is executed when this group is destroyed by the CDI-container or garbage collected, whichever
     * happens first.
     */
    private static final class CleaningAction implements Runnable {
        private final Collection<SubscriptionImpl<?>> managedSubscriptions;
        private final SubscriptionRegistry subscriptionRegistry;
        private final Collection<ManagedSubscriptionGroup> managedSubgroups;

        private CleaningAction(Collection<SubscriptionImpl<?>> managedSubscriptions,
                               SubscriptionRegistry subscriptionRegistry,
                               Collection<ManagedSubscriptionGroup> managedSubgroups) {
            this.managedSubscriptions = managedSubscriptions;
            this.subscriptionRegistry = subscriptionRegistry;
            this.managedSubgroups = managedSubgroups;
        }

        @Override
        public void run() {
            for (Subscription<?> subscription : managedSubscriptions) {
                subscriptionRegistry.unregister(subscription);
                subscription.cancel();
            }
            managedSubgroups.forEach(ManagedSubscriptionGroup::close);
        }
    }
}
