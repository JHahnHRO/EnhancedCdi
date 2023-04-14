package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;

@ApplicationScoped
class OutgoingMessageHandler implements Publisher {

    @Inject
    BlockingPool<Channel> publisherChannels;

    @Inject
    Event<InternalDelivery> responseEvent;

    @Inject
    Infrastructure infrastructure;

    @Inject
    Serialization serialization;

    //region low-level
    private <T> Optional<Incoming.Response<T, byte[]>> serializeAndSend(Outgoing<T> message)
            throws IOException, InterruptedException {
        final Outgoing<byte[]> serializedMessage = serialization.serialize(message);

        return sendDirect(serializedMessage, message);
    }

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
            publish(serializedMessage);
            return Optional.empty();
        }
    }

    private void publish(Outgoing<byte[]> message) throws InterruptedException, IOException {
        publisherChannels.run(
                channel -> channel.basicPublish(message.exchange(), message.routingKey(), message.properties(),
                                                message.content()));
    }

    private RpcClient.Response doRpc(Outgoing.Request<byte[]> request) throws IOException, InterruptedException {
        return publisherChannels.apply(channel -> doRpc(request, channel));
    }

    private RpcClient.Response doRpc(Outgoing.Request<byte[]> request, Channel channel) throws IOException {
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
    }

    //endregion

    @Override
    public <T, RES> Optional<Incoming.Response<T, RES>> send(Outgoing<T> message)
            throws IOException, InterruptedException {
        final Optional<Incoming.Response<T, byte[]>> serializedResponse = serializeAndSend(message);

        if (serializedResponse.isPresent()) {
            final Incoming<?> response = serialization.deserialize(serializedResponse.get());
            return Optional.of((Incoming.Response<T, RES>) response);
        } else {
            return Optional.empty();
        }
    }

    <T> void observeOutgoing(@ObservesAsync Outgoing<T> message, EventMetadata eventMetadata)
            throws IOException, InterruptedException {
        final Type runtimeType = getRuntimeType(message, (ParameterizedType) eventMetadata.getType());
        final Outgoing<T> messageWithAdjustedType = message.builder().setType(runtimeType).build();
        serializeAndSend(messageWithAdjustedType).ifPresent(
                response -> responseEvent.fireAsync(new InternalDelivery(response, AutoAck.INSTANCE)));
    }

    private <T> Type getRuntimeType(Outgoing<T> event, final ParameterizedType eventType) {
        if (event instanceof Outgoing.Response<?, ?>) {
            return eventType.getActualTypeArguments()[1];
        } else {
            return eventType.getActualTypeArguments()[0];
        }
    }

    @Override
    public boolean checkReplyQueue(Incoming.Request<?> request) throws InterruptedException {
        try {
            return publisherChannels.apply(channel -> {
                final AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(request.properties().getReplyTo());
                return declareOk.getConsumerCount() > 0;
            });
        } catch (IOException e) {
            return false;
        }
    }
}
