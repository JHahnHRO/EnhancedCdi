package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.UnroutableRpcRequestException;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
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

    private static int getTimeoutInMilliSeconds(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }

        try {
            return Math.toIntExact(timeout.toMillis());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Timeout larger than 2^31-1 milliseconds");
        }
    }

    private void doBasicPublish(Outgoing<byte[]> message, boolean mandatory) throws InterruptedException, IOException {
        infrastructure.setUpForExchange(message.exchange());

        publisherChannels.run(channel -> channel.basicPublish(message.exchange(), message.routingKey(), mandatory,
                                                              message.properties(), message.content()));
    }

    private <T> Incoming.Response<T, byte[]> doRpc(Outgoing.Request<byte[]> serializedRequest,
                                                   Outgoing.Request<T> originalRequest, Duration timeout)
            throws RpcException, InterruptedException, IOException, TimeoutException {
        infrastructure.setUpForExchange(originalRequest.exchange());

        final RpcClient.Response rawResponse;
        try {
            rawResponse = publisherChannels.apply(channel -> doRpc(serializedRequest, channel, timeout));
        } catch (RpcException e) {
            if (e.getCause() instanceof TimeoutException toe) {
                throw toe;
            }
            throw e;
        }

        return new Incoming.Response<>(rawResponse.getEnvelope(), rawResponse.getProperties(), rawResponse.getBody(),
                                       originalRequest);
    }

    private RpcClient.Response doRpc(Outgoing.Request<byte[]> request, Channel channel, Duration timeout)
            throws IOException {

        final RpcClientParams rpcClientParams = new RpcClientParams().channel(channel)
                .exchange(request.exchange())
                .routingKey(request.routingKey())
                .useMandatory()
                .replyTo(request.properties().getReplyTo())
                .timeout(getTimeoutInMilliSeconds(timeout))
                .correlationIdSupplier(() -> request.properties().getCorrelationId());

        try (RpcClient rpcClient = new RpcClient(rpcClientParams)) {
            return rpcClient.doCall(request.properties(), request.content());
        } catch (TimeoutException | UnroutableRpcRequestException e) {
            throw new RpcException(e);
        }
    }

    //endregion

    @Override
    public <T> void publish(Outgoing<T> message)
            throws IOException, InterruptedException, SerializationException {
        final Outgoing<byte[]> serializedMessage = serialization.serialize(message);
        doBasicPublish(serializedMessage, false);
    }

    @Override
    public <T> void publishMandatory(Outgoing<T> message)
            throws IOException, InterruptedException, SerializationException {
        final Outgoing<byte[]> serializedMessage = serialization.serialize(message);
        doBasicPublish(serializedMessage, true);
    }

    @Override
    public <T> void publishConfirmed(Outgoing<T> message) {
        throw new UnsupportedOperationException("Currently not implemented");
    }

    @Override
    public <T, RES> Incoming.Response<T, RES> rpc(Outgoing.Request<T> request, Duration timeout)
            throws IOException, InterruptedException, TimeoutException, RpcException, SerializationException,
                   DeserializationException {
        final Outgoing.Request<byte[]> serializedRequest = serialization.serialize(request);
        final Incoming.Response<T, byte[]> serializedResponse = doRpc(serializedRequest, request, timeout);
        return (Incoming.Response<T, RES>) serialization.deserialize(serializedResponse);
    }

    <T> void observeOutgoing(@ObservesAsync Outgoing<T> message, EventMetadata eventMetadata)
            throws IOException, InterruptedException, TimeoutException, SerializationException {
        final Type runtimeType = getRuntimeType(message, (ParameterizedType) eventMetadata.getType());
        final Outgoing<T> messageWithAdjustedType = message.builder().setType(runtimeType).build();

        if (messageWithAdjustedType instanceof Outgoing.Request<T> originalRequest) {
            final Outgoing.Request<byte[]> serializedRequest = serialization.serialize(originalRequest);
            final Incoming.Response<T, byte[]> serializedResponse = doRpc(serializedRequest, originalRequest, null);
            responseEvent.fireAsync(new InternalDelivery(serializedResponse, AutoAck.INSTANCE));
        } else {
            publish(messageWithAdjustedType);
        }
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
