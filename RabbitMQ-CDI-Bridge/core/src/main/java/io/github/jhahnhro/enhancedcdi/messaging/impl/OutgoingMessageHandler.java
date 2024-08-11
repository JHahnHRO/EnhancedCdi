package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.UnroutableRpcRequestException;
import io.github.jhahnhro.enhancedcdi.messaging.Publisher;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.NotConfirmedException;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.DeserializationException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SerializationException;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.inject.Inject;

@ApplicationScoped
class OutgoingMessageHandler implements Publisher {

    @Inject
    @Default
    BlockingPool<Channel> publisherChannels;

    @Inject
    Confirmations confirmations;
    @Inject
    @WithConfirms
    BlockingPool<Channel> publisherChannelsWithConfirms;

    @Inject
    Event<InternalDelivery> responseEvent;

    @Inject
    Infrastructure infrastructure;

    @Inject
    Serialization serialization;

    //region low-level

    private static int getTimeoutInMilliSeconds(Duration timeout) {
        if (timeout == null) {
            return -1; // no timeout
        }
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

    private RpcClient.Response doRcp(Outgoing.Request<byte[]> serializedRequest, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        infrastructure.setUpForExchange(serializedRequest.exchange());
        final String replyTo = serializedRequest.properties().getReplyTo();
        if (!Topology.RABBITMQ_REPLY_TO.equals(replyTo)) {
            infrastructure.setUpForQueue(replyTo);
        }

        final RpcClient.Response rawResponse;
        try {
            rawResponse = publisherChannels.apply(channel -> doRpc(serializedRequest, channel, timeout));
        } catch (RpcException e) {
            if (e.getCause() instanceof TimeoutException toe) {
                throw toe;
            }
            throw e;
        }
        return rawResponse;
    }

    private RpcClient.Response doRpc(Outgoing.Request<byte[]> request, Channel channel, Duration timeout)
            throws IOException {

        final AMQP.BasicProperties requestProperties = request.properties();
        final RpcClientParams rpcClientParams = new RpcClientParams().channel(channel)
                .exchange(request.exchange())
                .routingKey(request.routingKey())
                .useMandatory()
                .replyTo(requestProperties.getReplyTo())
                .correlationIdSupplier(requestProperties::getCorrelationId);

        try (RpcClient rpcClient = new RpcClient(rpcClientParams)) {
            return rpcClient.doCall(requestProperties, request.content(), getTimeoutInMilliSeconds(timeout));
        } catch (TimeoutException | UnroutableRpcRequestException e) {
            throw new RpcException(e);
        }
    }

    private void doPublishConfirmed(Outgoing<byte[]> serializedMessage)
            throws InterruptedException, IOException, NotConfirmedException {

        infrastructure.setUpForExchange(serializedMessage.exchange());

        final Future<Confirmations.Result> resultFuture = publisherChannelsWithConfirms.apply(
                channel -> confirmations.publishConfirmed(channel, serializedMessage));

        final Confirmations.Result result;
        try {
            result = resultFuture.get();
        } catch (ExecutionException e) {
            throw (ShutdownSignalException) e.getCause();
        }

        if (result == Confirmations.Result.NACK) {
            throw new NotConfirmedException();
        }
    }
    //endregion

    @Override
    public <T> void publish(Outgoing<T> message) throws IOException, InterruptedException, SerializationException {
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
    public <T> void publishConfirmed(Outgoing<T> message)
            throws IOException, InterruptedException, NotConfirmedException, SerializationException {
        final Outgoing<byte[]> serializedMessage = serialization.serialize(message);
        doPublishConfirmed(serializedMessage);
    }

    @Override
    public <T, RES> Incoming.Response<T, RES> rpc(Outgoing.Request<T> request, Duration timeout)
            throws IOException, InterruptedException, TimeoutException, RpcException, SerializationException,
                   DeserializationException {
        final Incoming.Response<T, byte[]> serializedResponse = serializeAndRpc(request, timeout);
        return (Incoming.Response<T, RES>) serialization.deserialize(serializedResponse);
    }

    private <T> Incoming.Response<T, byte[]> serializeAndRpc(Outgoing.Request<T> request, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        final Outgoing.Request<byte[]> serializedRequest = serialization.serialize(request);
        final RpcClient.Response rawResponse = doRcp(serializedRequest, timeout);
        return new Incoming.Response<>(rawResponse.getEnvelope(), rawResponse.getProperties(), rawResponse.getBody(),
                                       request);
    }

    <T> void observeOutgoing(@ObservesAsync Outgoing<T> message, EventMetadata eventMetadata)
            throws IOException, InterruptedException, TimeoutException, SerializationException {
        final Type runtimeType = getRuntimeType(message, (ParameterizedType) eventMetadata.getType());
        final Outgoing<T> messageWithAdjustedType = message.builder().setType(runtimeType).build();

        if (messageWithAdjustedType instanceof Outgoing.Request<T> originalRequest) {
            final Incoming.Response<T, byte[]> serializedResponse = serializeAndRpc(originalRequest, null);
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
