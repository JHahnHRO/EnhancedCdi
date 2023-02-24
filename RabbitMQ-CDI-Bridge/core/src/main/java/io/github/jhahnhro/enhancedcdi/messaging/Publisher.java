package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Message;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing.Request;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;

/**
 *
 */
public interface Publisher {

    /**
     * Like {@link #send(Outgoing, Type)}, but with the {@code runtimeType} parameter equal to the runtime class of the
     * {@link Message#content() message content}, i.e. {@code message.content().getClass()}.
     * <p>
     * This method is sufficient if the content's runtime class does not contain unresolved type variables in its class
     * hierarchy; if {@code message.content()} is an instance of {@code Foo} where {@code Foo} implements only
     * {@code List<String>} and has no type variables on its own, then this method is fine. If however, the content's
     * runtime class contains type variables, say {@code ArrayList}, then {@link #send(Outgoing, Type)} is preferable
     * and may even be necessary, because the appropriate {@link MessageWriter} may be unable to be resolved without
     * full knowledge of the relevant type variables. For example, there may not be any bean of type
     * {@code MessageWriter<List>}, but there may be one of type {@code MessageWriter<List<String>>}.
     *
     * @see #send(Outgoing, Type)
     */
    default <T, RES> Optional<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Response<T, RES>> send(Outgoing<T> message)
            throws IOException, InterruptedException {
        return send(message, message.content().getClass());
    }

    /**
     * Like {@link #send(Request, Type)}, but with the {@code runtimeType} parameter equal to the runtime class of the
     * {@link Message#content() message content}, i.e. {@code message.content().getClass()}.
     * <p>
     * This method is sufficient if the content's runtime class does not contain unresolved type variables in its class
     * hierarchy; if {@code message.content()} is an instance of {@code Foo} where {@code Foo} implements only
     * {@code List<String>} and has no type variables on its own, then this method is fine. If however, the content's
     * runtime class contains type variables, say {@code ArrayList}, then {@link #send(Request, Type)} is preferable and
     * may even be necessary, because the appropriate {@link MessageWriter} may be unable to be resolved without full
     * knowledge of the relevant type variables. For example, there may not be any bean of type
     * {@code MessageWriter<List>}, but there may be one of type {@code MessageWriter<List<String>>}.
     *
     * @see #send(Request, Type)
     */
    default <T, RES> io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Response<T, RES> send(Request<T> request) throws IOException, InterruptedException {
        return send(request, request.content().getClass());
    }

    /**
     * Sends the given request to the broker and returns the response to the caller.
     *
     * @param request     the request to send
     * @param <T>         the type of the request's content
     * @param <RES>       the expected response type. Note that this method does not perform any type checks on the
     *                    response content, i.e. a {@link ClassCastException} may be thrown if the response was in fact
     *                    deserialized to something incompatible with {@code RES}.
     * @param runtimeType Optional. The runtime type of the content of the message which will be used to resolve the
     *                    correct {@link MessageWriter} for this message, i.e. the type {@code T} (or a supertype
     *                    thereof). If {@code null}, then {@code message.content().getClass()} will be used instead.
     * @return the response.
     * @throws IOException           if anything goes wrong
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an available
     *                               {@link com.rabbitmq.client.Channel} to publish the message.
     * @throws IllegalStateException if no {@link MessageWriter} for the content can be found.
     */
    default <T, RES> io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Response<T, RES> send(Request<T> request, Type runtimeType)
            throws IOException, InterruptedException {
        //noinspection unchecked,OptionalGetWithoutIsPresent
        return (io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Response<T, RES>) send((Outgoing<T>) request, runtimeType).get();
    }

    /**
     * Sends the given message to the broker. If the message is a {@link Request}, the method blocks until a response is
     * received (or until the current thread is interrupted) which is then returned to the caller.
     *
     * @param message            the outgoing message
     * @param messageContentType Optional. The runtime type of {@code message.content()} which will be used to resolve
     *                           the correct {@link MessageWriter} for this message, i.e. the type {@code T} (or a
     *                           supertype thereof). If {@code null}, then {@code message.content().getClass()} will be
     *                           used instead.
     * @param <T>                the type of the content of the message
     * @param <RES>              the type of the received response
     * @return the response if there is any.
     * @throws IOException           if anything goes wrong
     * @throws InterruptedException  if the current thread gets interrupted while waiting for an available
     *                               {@link com.rabbitmq.client.Channel} to publish the message.
     * @throws IllegalStateException if no {@link MessageWriter} for the content can be found.
     * @apiNote Note that this method does not perform any type checks on the response content, i.e. a
     * {@link ClassCastException} may be thrown if the response was in fact deserialized to something incompatible with
     * {@code RES}.
     */
    <T, RES> Optional<io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming.Response<T, RES>> send(Outgoing<T> message, final Type messageContentType)
            throws IOException, InterruptedException;

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