package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;
import java.util.Optional;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing.Request;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;

/**
 * Provides methods to publish {@link Outgoing} messages to the RabbitMQ broker.
 */
public interface Publisher {

    /**
     * Sends the given message to the broker. If the message is a {@link Request}, the method blocks until a response is
     * received (or until the current thread is interrupted) which is then returned to the caller.
     *
     * @param message the outgoing message
     * @param <T>     the type of the content of the message
     * @param <RES>   the type of the received response
     * @return the response if the message was a request, an empty Optional otherwise.
     * @throws IOException           if anything goes wrong
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an available
     *                               {@link com.rabbitmq.client.Channel} to publish the message.
     * @throws IllegalStateException if no {@link MessageWriter} for the content can be found.
     * @apiNote Note that this method does not perform any type checks on the response content, i.e. a
     * {@link ClassCastException} may be thrown if the response was in fact deserialized to something incompatible with
     * {@code RES}.
     */
    <T, RES> Optional<Incoming.Response<T, RES>> send(Outgoing<T> message) throws IOException, InterruptedException;

    /**
     * Sends the given request to the broker and returns the response to the caller.
     *
     * @param request the request to send
     * @param <T>     the type of the request's content
     * @param <RES>   the expected response type. Note that this method does not perform any type checks on the response
     *                content, i.e. a {@link ClassCastException} may be thrown if the response was in fact deserialized
     *                to something incompatible with {@code RES}.
     * @return the response.
     * @throws IOException           if anything goes wrong
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an available
     *                               {@link com.rabbitmq.client.Channel} to publish the message.
     * @throws IllegalStateException if no {@link MessageWriter} for the content can be found.
     */
    @SuppressWarnings({"OptionalGetWithoutIsPresent", "java:S3655"})
    default <T, RES> Incoming.Response<T, RES> send(Request<T> request) throws IOException, InterruptedException {
        return this.<T, RES>send((Outgoing<T>) request).get();
    }

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
