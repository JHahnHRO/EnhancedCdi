package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.github.jhahn.enhancedcdi.messaging.ConnectionShutdown;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.TimeoutException;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

@ApplicationScoped
class ConnectionProducer {

    private static final System.Logger LOG = System.getLogger(ConnectionProducer.class.getCanonicalName());
    @Inject
    Event<ConnectionShutdown> connectionShutdownEvent;
    @Inject
    ConnectionFactory connectionFactory;

    @Produces
    @ApplicationScoped
    Connection openConnection() throws IOException, TimeoutException {
        warnMisconfiguration();

        LOG.log(Level.INFO, "Creating connection to RabbitMQ broker...");
        final Connection connection = connectionFactory.newConnection();
        LOG.log(Level.INFO, "Successfully created connection to RabbitMQ broker.");
        
        connection.addShutdownListener(cause -> {
            LOG.log(Level.WARNING, () -> "Shutting down connection, because '%s".formatted(cause.getReason()), cause);
            connectionShutdownEvent.fire(new ConnectionShutdown(cause));
        });
        return connection;
    }

    private void warnMisconfiguration() {
        if (!connectionFactory.isAutomaticRecoveryEnabled()) {
            LOG.log(Level.WARNING,
                    "The RabbitMQ CDI bridge should only be used with automatic recovery enabled. Enabling now.");
            connectionFactory.setAutomaticRecoveryEnabled(true);
        }
    }

    void disposeConnection(@Disposes Connection connection) throws IOException {
        connection.close();
    }

    /**
     * If the connection gets shutdown by the broker, loss of connectivity or other external reasons, we still need to
     * destroy the contextual instance.
     *
     * @param shutdownEvent
     * @param clientProxy
     * @param instance
     */
    void destroyOnConnectionShutdown(@Observes @Priority(LIBRARY_BEFORE
                                                         + 1) ConnectionShutdown shutdownEvent,
                                     Connection clientProxy, Instance<Connection> instance) {
        instance.destroy(clientProxy);
    }
}