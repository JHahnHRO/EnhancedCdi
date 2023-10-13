package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

/**
 * A connection together keeps track of the connection's channels and allows to wait for one to become available instead
 * of returning {@code null} from {@link #createChannel()}.
 */
interface BookkeepingConnection extends Connection {

    /**
     * Creates a new channel without blocking, returning {@code null} if no channels is available. The blocking analog
     * is {@link #acquireChannel()}.
     *
     * @return a new channel or {@code null} if none is available
     * @throws IOException if an I/O problem is encountered
     * @see #acquireChannel()
     */
    @Override
    Channel createChannel() throws IOException;

    /**
     * Creates a new channel, blocking the calling thread if the {@link #getChannelMax() maximal number of channels} is
     * reached until another channel closes.
     *
     * @return the new channel
     * @throws InterruptedException if the current thread is interrupted while waiting for another channel to close
     * @throws IOException          if an I/O problem is encountered
     */
    Channel acquireChannel() throws InterruptedException, IOException;
}
