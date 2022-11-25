package io.github.jhahn.enhancedcdi.messaging;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * An event fired synchronously if and when the connection is shutdown by a {@link ShutdownSignalException}, e.g. if the
 * broker is shutting down, connectivity was lost and cannot be recovered.
 *
 * @param shutdownSignal
 */
public record ConnectionShutdown(ShutdownSignalException shutdownSignal) {}
