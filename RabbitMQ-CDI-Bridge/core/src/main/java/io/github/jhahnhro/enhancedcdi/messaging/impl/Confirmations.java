package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javax.enterprise.context.ApplicationScoped;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Manages confirmations
 */
@ApplicationScoped
public class Confirmations {

    private final Map<Channel, ConfirmHandler> handlerMap;

    public Confirmations() {
        this.handlerMap = new ConcurrentHashMap<>();
    }

    /**
     * Returns a future for the confirmation of the immediate next message published on the given channel. The returned
     * future completes when either
     * <ul>
     *     <li>the message is confirmed by the broker</li>
     *     <li>the message is disconfirmed by the broker</li>
     *     <li>the channel/connection closes (exceptional completion)</li>
     * </ul>
     *
     * @param channel a channel that was {@link #putChannelInConfirmMode(Channel) put in confirm mode} previously.
     * @return a future for the confirmation of the immediate next message published on the given channel.
     */
    public Future<Result> getNextConfirmationResult(final Channel channel) {
        return handlerMap.get(channel).preparePublishing();
    }

    /**
     * Configures a channel for confirm mode, installs the appropriate {@link ConfirmListener} and
     * {@link ShutdownListener}
     *
     * @param channel
     */
    public void putChannelInConfirmMode(Channel channel) throws IOException {
        final ConfirmHandler newHandler = new ConfirmHandler(channel);

        if (handlerMap.putIfAbsent(channel, newHandler) == null) {
            channel.confirmSelect();
            channel.addConfirmListener(newHandler);
            channel.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException sse) {
                    final ConfirmHandler handler = handlerMap.remove(channel);
                    if (handler != null) {
                        channel.removeConfirmListener(handler);
                        channel.removeShutdownListener(this);
                        handler.shutdownCompleted(sse);
                    }
                }
            });
        }
    }

    public enum Result {ACK, NACK}

    private static class ConfirmHandler implements ConfirmListener {
        private final NavigableMap<Long, CompletableFuture<Result>> outstanding;
        private final Channel channel;

        private ConfirmHandler(Channel channel) {
            this.outstanding = new TreeMap<>();
            this.channel = channel;
        }

        @Override
        public void handleAck(long deliveryTag, boolean multiple) {
            synchronized (outstanding) {
                applyAndClear(deliveryTag, multiple, future -> future.complete(Result.ACK));
            }
        }

        @Override
        public void handleNack(long deliveryTag, boolean multiple) {
            synchronized (outstanding) {
                applyAndClear(deliveryTag, multiple, future -> future.complete(Result.NACK));
            }
        }

        public void shutdownCompleted(ShutdownSignalException sse) {
            synchronized (outstanding) {
                applyAndClear(outstanding, future -> future.completeExceptionally(sse));
            }
        }

        private void applyAndClear(long deliveryTag, boolean multiple, Consumer<CompletableFuture<Result>> action) {
            if (multiple) {
                applyAndClear(outstanding.headMap(deliveryTag, true), action);
            } else {
                applyAndClear(deliveryTag, action);
            }
        }

        private void applyAndClear(long deliveryTag, Consumer<CompletableFuture<Result>> action) {
            final CompletableFuture<Result> result = outstanding.remove(deliveryTag);
            if (result != null) {
                action.accept(result);
            }
        }

        private <X> void applyAndClear(Map<?, X> map, Consumer<X> action) {
            final var values = map.values();
            values.forEach(action);
            values.clear();
        }

        public CompletableFuture<Result> preparePublishing() {
            final long publishSeqNo = channel.getNextPublishSeqNo();
            final CompletableFuture<Result> future = new CompletableFuture<>();
            synchronized (outstanding) {
                outstanding.put(publishSeqNo, future);
            }
            return future;
        }
    }
}
