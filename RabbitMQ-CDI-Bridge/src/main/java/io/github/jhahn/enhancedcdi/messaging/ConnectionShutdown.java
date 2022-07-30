package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;

public record ConnectionShutdown(Connection connection, ShutdownSignalException shutdownSignal) {}
