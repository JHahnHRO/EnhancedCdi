package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import io.github.jhahn.enhancedcdi.messaging.MessageAcknowledgment;
import io.github.jhahn.enhancedcdi.messaging.Publisher;
import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
class OutgoingMessageHandler implements Publisher {

    @Inject
    BlockingPool<Channel> publisherChannels;

    @Inject
    Event<InternalDelivery> internalDeliveryEvent;

    @Inject
    Infrastructure infrastructure;

    @Inject
    Serialization serialization;

    //region low-level
    private <T> Optional<Incoming.Response<T, byte[]>> sendDirect(Outgoing<byte[]> serializedMessage,
                                                                  Outgoing<T> originalMessage)
            throws IOException, InterruptedException {
        infrastructure.setUpForExchange(serializedMessage.exchange());

        if (serializedMessage instanceof Outgoing.Request<byte[]> serializedRequest
            && originalMessage instanceof Outgoing.Request<T> originalRequest) {

            final RpcClient.Response response = doRpc(serializedRequest);
            final Delivery responseDelivery = new Delivery(response.getEnvelope(), response.getProperties(),
                                                           response.getBody());
            return Optional.of(new Incoming.Response<>(responseDelivery, response.getBody(), originalRequest));
        } else {
            cast(serializedMessage);
            return Optional.empty();
        }
    }

    private void cast(Outgoing<byte[]> message) throws InterruptedException, IOException {
        publisherChannels.withItem(channel -> {
            if (message instanceof Outgoing.Response<?, ?> response) {
                checkReplyQueue(channel, response.request());
            }
            channel.basicPublish(message.exchange(), message.routingKey(), message.properties(), message.content());
        });
    }

    private boolean checkReplyQueue(Channel channel, final Incoming.Request<?> request) throws IOException {
        return channel.queueDeclarePassive(request.properties().getReplyTo()).getConsumerCount() > 0;
    }

    private RpcClient.Response doRpc(Outgoing.Request<byte[]> request) throws IOException, InterruptedException {
        return publisherChannels.withItem(channel -> {
            final RpcClientParams rpcClientParams = new RpcClientParams().channel(channel)
                    .exchange(request.exchange())
                    .routingKey(request.routingKey())
                    .replyTo(request.properties().getReplyTo())
                    .correlationIdSupplier(() -> request.properties().getCorrelationId());
            final RpcClient rpcClient = new RpcClient(rpcClientParams);

            try {
                return rpcClient.doCall(request.properties(), request.content());
            } catch (TimeoutException timeoutException) {
                // should not happen, because we do not use RpcClientParams with a timeout so that it defaults to no
                // timeout at all.
                throw new AssertionError(timeoutException);
            }
        });
    }
    //endregion

    @Override
    public <T, RES> Optional<Incoming.Response<T, RES>> send(Outgoing<T> message, Type runtimeType)
            throws IOException, InterruptedException {
        final Optional<Incoming.Response<T, byte[]>> serializedResponse = serializeAndSend(message, runtimeType);

        if (serializedResponse.isPresent()) {
            final Incoming<?> response = serialization.deserialize(serializedResponse.get());
            return Optional.of((Incoming.Response<T, RES>) response);
        } else {
            return Optional.empty();
        }
    }

    private <T> Optional<Incoming.Response<T, byte[]>> serializeAndSend(Outgoing<T> message, Type runtimeType)
            throws IOException, InterruptedException {
        if (runtimeType == null) {
            runtimeType = message.content().getClass();
        }
        final Outgoing<byte[]> serializedMessage = serialization.serialize(message, runtimeType);

        return sendDirect(serializedMessage, message);
    }

    <T> void observeOutgoing(@ObservesAsync Outgoing<T> message, EventMetadata eventMetadata)
            throws IOException, InterruptedException {
        final Type runtimeType = ((ParameterizedType) eventMetadata.getType()).getActualTypeArguments()[0];
        serializeAndSend(message, runtimeType).ifPresent(response -> {
            final var internalDelivery = new InternalDelivery(response, new MessageAcknowledgment.AutoAck());
            internalDeliveryEvent.fireAsync(internalDelivery);
        });
    }

    @Override
    public boolean checkReplyQueue(Incoming.Request<?> request) throws InterruptedException {
        try {
            return publisherChannels.withItem(channel -> {return checkReplyQueue(channel, request);});
        } catch (IOException e) {
            return false;
        }
    }
}
