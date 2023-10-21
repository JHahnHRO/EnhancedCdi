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
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

/**
 * Manages confirmations
 */
@ApplicationScoped
class Confirmations {

    private final Map<Channel, ConfirmHandler> handlerMap = new ConcurrentHashMap<>();

    public Future<Result> publishConfirmed(Channel channel, Outgoing<byte[]> serializedMessage) throws IOException {
        Future<Result> result = handlerMap.computeIfAbsent(channel, ConfirmHandler::new).preparePublishing();

        channel.basicPublish(serializedMessage.exchange(), serializedMessage.routingKey(), true,
                             serializedMessage.properties(), serializedMessage.content());
        return result;
    }

    public enum Result {ACK, NACK}

    private class ConfirmHandler implements ConfirmListener {
        private final NavigableMap<Long, CompletableFuture<Result>> outstanding;
        private final Channel channel;

        private ConfirmHandler(Channel channel) {
            this.outstanding = new TreeMap<>();
            this.channel = channel;

            installListeners();
        }

        private void installListeners() {
            channel.addConfirmListener(this);
            channel.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    handlerMap.remove(channel); // do not leave references to closed channels lying around
                    channel.removeConfirmListener(ConfirmHandler.this);
                    channel.removeShutdownListener(this);
                    ConfirmHandler.this.shutdownCompleted(cause);
                }
            });
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
            map.values().forEach(action);
            map.clear();
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
