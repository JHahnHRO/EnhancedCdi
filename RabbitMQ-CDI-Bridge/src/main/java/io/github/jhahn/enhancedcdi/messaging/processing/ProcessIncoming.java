package io.github.jhahn.enhancedcdi.messaging.processing;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.serialization.Deserializer;

import java.io.InputStream;
import java.util.Optional;

public sealed interface ProcessIncoming extends ProcessDelivery {

    /**
     * @return the name of the queue the delivery was received on
     */
    String queue();

    /**
     * @return The {@link InputStream} from which the content will be deserialized
     */
    InputStream body();

    /**
     * Replaces the {@link InputStream} from which the content will be deserialized.
     *
     * @param inputStream the new {@link InputStream}
     */
    void setBody(InputStream inputStream);

    /**
     * Enforces the usage of a specific {@link Deserializer} which will deserialize the contents of the message. If this
     * method is not called, the Deserializer is chosen automatically.
     *
     * @param deserializer the {@link Deserializer}
     * @throws IllegalStateException if another observer method of this event has already set a {@link Deserializer}.
     */
    void setDeserializer(Deserializer<?> deserializer);

    /**
     * An event synchronously fired for every incoming message that is neither an RPC request nor an RPC response, i.e.
     * that was published in a fire-and-forget style.
     */
    non-sealed interface Broadcast extends ProcessIncoming {

    }

    /**
     * An event synchronously fired for incoming RPC requests, i.e. deliveries that have the
     * {@link BasicProperties#getReplyTo() replyTo property} set to a non-null value.
     *
     * <p>
     * Allows for short-circuiting the request handling by calling {@link #setImmediateResponse}. If one of the
     * overloads of this method is used, the delivery will not be handled further after the observers of this event are
     * finished and the response will be immediately sent. This can be used for example to define an observer method
     * which validates incoming requests and sets an error response when it detects an invalid request.
     */
    non-sealed interface Request extends ProcessIncoming {

        /**
         * If called, no further handling of the incoming delivery will happen after the observers of this event are
         * finished. There is no way to "un-veto" in later observer methods once {@code veto()} has been called.
         * <p>
         * A veto is implicit when calling {@link #setImmediateResponse}. The difference is that calling this method
         * directly will not send any response (which may leave the requester waiting indefinitely, so use with
         * caution!).
         */
        @Override
        void veto();

        /**
         * @return the response set by a previous call to {@link #setImmediateResponse}
         */
        Optional<ImmediateResponse> immediateResponse();

        /**
         * Sets a response to the incoming request. If called, no further handling of the request happens after the
         * observers of this event have finished, i.e. a {@link #veto()} is implicit.
         * <p>
         * At most one observer can call this method. Subsequent observers can still read the request and even the
         * response {@link #immediateResponse()}, but cannot modify the response. If another observer method tries, an
         * {@link IllegalStateException} is thrown. Use observer method ordering with the
         * {@link javax.annotation.Priority} annotation to ensure the correct ordering of observer methods.
         *
         * @param immediateResponse the response
         * @throws IllegalStateException    if this method was already called by a previous observer method.
         * @throws NullPointerException     if {@code immediateResponse} is null
         * @throws IllegalArgumentException if {@code immediateResponse.properties().getCorrelationId()} is not equals
         *                                  to the request's correlation ID.
         */
        void setImmediateResponse(ImmediateResponse immediateResponse);

        record ImmediateResponse(byte[] body, BasicProperties properties) {}
    }

    /**
     * Event fired for incoming responses to RPC requests.
     */
    non-sealed interface Response<R> extends ProcessIncoming {

        /**
         * Returns the request to which the delivery is a response to. It will be returned as it was sent to the broker,
         * i.e. after all processing of the outgoing delivery was handled.
         *
         * @return the request to which the delivery is a response to.
         */
        Outgoing<R> request();
    }
}
