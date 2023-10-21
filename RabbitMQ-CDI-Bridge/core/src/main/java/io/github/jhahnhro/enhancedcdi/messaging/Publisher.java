package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.NotConfirmedException;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.rpc.RpcException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.DeserializationException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SerializationException;

/**
 * Provides methods to publish {@link Outgoing} messages to the RabbitMQ broker.
 */
public interface Publisher {

    /**
     * Sends the given message to the broker fire-and-forget style (non-mandatory and without publisher confirms)
     *
     * @param message the outgoing message
     * @param <T>     the type of the content of the message
     * @throws IOException            if anything goes wrong
     * @throws InterruptedException   if the current thread gets interrupted while waiting for a
     *                                {@link com.rabbitmq.client.Channel} to become available to publish the message.
     * @throws SerializationException if the message could not be serialized
     */
    <T> void publish(Outgoing<T> message) throws IOException, InterruptedException, SerializationException;

    /**
     * Similar to {@link #publish(Outgoing)} but sends the given message to the broker in "mandatory" mode, i.e. the
     * broker will send an error back if the message could not be delivered to any receipts. That error will manifest as
     * an {@link javax.enterprise.event.ObservesAsync asynchronous event} of type
     * {@link io.github.jhahnhro.enhancedcdi.messaging.messages.ReturnedMessage}.
     *
     * @param message the outgoing message
     * @param <T>     the type of the content of the message
     * @throws IOException            if anything goes wrong
     * @throws InterruptedException   if the current thread gets interrupted while waiting for a
     *                                {@link com.rabbitmq.client.Channel} to become available to publish the message.
     * @throws SerializationException if the message could not be serialized
     */
    <T> void publishMandatory(Outgoing<T> message) throws IOException, InterruptedException, SerializationException;

    /**
     * Send a message to the broker and waits until the broker confirms it could process the message.
     *
     * @param message the outgoing message
     * @param <T>     the type of the content of the message
     * @throws IOException                                 if anything goes wrong
     * @throws InterruptedException                        if the current thread gets interrupted while waiting for a
     *                                                     {@link com.rabbitmq.client.Channel} to become available to
     *                                                     publish the message or while waiting for the confirmation.
     * @throws SerializationException                      if the message could not be serialized
     * @throws NotConfirmedException                       if the broker could not process the message
     * @throws com.rabbitmq.client.ShutdownSignalException if the underlying channel/connection was closed before the
     *                                                     confirmation could arrive.
     */
    <T> void publishConfirmed(Outgoing<T> message)
            throws IOException, InterruptedException, NotConfirmedException, SerializationException;

    /**
     * Sends the given request to the broker and returns the response to the caller.
     *
     * @param request the request to send.
     * @param timeout time to wait for a response before giving up.
     * @param <T>     the type of the request's content
     * @param <RES>   the expected response type. Note that this method does not perform any type checks on the response
     *                content, i.e. a {@link ClassCastException} may be thrown if the response was in fact deserialized
     *                to something incompatible with {@code RES}.
     * @return the response.
     * @throws IOException           if anything goes wrong
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an available
     *                               {@link com.rabbitmq.client.Channel} to publish the message.
     * @throws TimeoutException      if no response was received within the given time.
     * @throws RpcException          if the remote procedure call could not be completed for some reason.
     * @throws IllegalStateException if no {@link SelectableMessageWriter} for the content can be found.
     */
    <T, RES> Incoming.Response<T, RES> rpc(Outgoing.Request<T> request, Duration timeout)
            throws IOException, InterruptedException, TimeoutException, RpcException, SerializationException,
                   DeserializationException;

    /**
     * Checks if there is still a consumer waiting for the response to the given request. Because the response to a
     * request may take a long time to compute, the consumer of the response (and with it the reply queue) may have gone
     * away before the response is ready to be sent.
     * <p>
     * This is done by checking that the {@link BasicProperties#getReplyTo() replyTo queue} still exists at the broker
     * and at least one consumer is still listening to it.
     * <p>
     * Use this method if you want to prevent costly computations that nobody can receive. Do not use this method if the
     * additional network roundtrip it requires is not worth it.
     *
     * @param request the original request whose reply-queue should be checked
     * @return {@code true} if the queue was found to still exists, {@code false} if it did not exist.
     * @throws InterruptedException if the current thread was interrupted while waiting for a channel to become
     *                              available.
     */
    boolean checkReplyQueue(Incoming.Request<?> request) throws InterruptedException;

}
