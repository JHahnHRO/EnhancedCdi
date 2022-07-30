package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionFactoryConfigurator;
import io.github.jhahn.enhancedcdi.messaging.ConnectionShutdown;
import org.eclipse.microprofile.config.Config;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
class ConnectionProducer {

    @Inject
    Event<ConnectionShutdown> connectionShutdownEvent;
    private ConnectionFactory connectionFactory;

    @Inject
    void createConnectionFactory(Config applicationConfig) {
        Map<String, String> config = new HashMap<String, String>();

        applicationConfig.getPropertyNames()
                .forEach(name -> applicationConfig.getOptionalValue(name, String.class)
                        .ifPresent(value -> config.put(name, value)));

        final ConnectionFactory connectionFactory = new ConnectionFactory();
        ConnectionFactoryConfigurator.load(connectionFactory, config);

        this.connectionFactory = connectionFactory;
    }

    @Produces
    @ApplicationScoped
    Connection openConnection() throws IOException, TimeoutException {

        return connectionFactory.newConnection();
    }

    void disposeConnection(@Disposes Connection connection) throws IOException {
        connection.close();
    }
}