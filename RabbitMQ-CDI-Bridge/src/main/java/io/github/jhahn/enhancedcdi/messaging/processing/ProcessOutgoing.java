package io.github.jhahn.enhancedcdi.messaging.processing;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;
import io.github.jhahn.enhancedcdi.messaging.serialization.Serializer;

import java.io.OutputStream;

public sealed interface ProcessOutgoing<T> extends ProcessDelivery
        permits ProcessOutgoing.Message, ProcessOutgoing.Request, ProcessOutgoing.Response {


    /**
     * @return The java object that will be serialized and published to RabbitMQ
     */
    T content();

    /**
     * @return A {@link PropertiesBuilder} that mutates the properties of the response before publishing it to the
     * broker. Only the final state of the builder is used in the message sent to the broker.
     */
    @Override
    PropertiesBuilder properties();

    /**
     * @return The {@link OutputStream} into which the content will be serialized
     */
    OutputStream body();

    /**
     * Replaces the {@link OutputStream} into which the content will be serialized. Be careful to ensure that the new
     * {@code OutputStream} still delegates to the old one, otherwise no bytes will be written to the original stream
     *
     * @param outputStream the new {@link OutputStream}
     */
    void setBody(OutputStream outputStream);

    /**
     * Enforces the usage of a specific {@link Serializer} that serialize the content into the {@link OutputStream}. If
     * it is not called, a serializer will be selected automatically.
     *
     * @param serializer the {@link Serializer}
     * @throws IllegalStateException if another observer method has already set a serializer.
     */
    void setSerializer(Serializer<T> serializer);

    /**
     * Convenience method that returns an instance of {@link Outgoing} representing what message would be sent if no
     * further processing happened.
     *
     * @return an instance of {@link Outgoing} representing the current state.
     */
    default Outgoing<T> getOutgoing() {
        return new Outgoing<>(this.exchange(), this.routingKey(), this.properties().build(), this.content());
    }

    /**
     * An event that is fired synchronously for every outgoing fire-and-forget style delivery.
     */
    non-sealed interface Message<T> extends ProcessOutgoing<T> {

        /**
         * Replaces the name of  the exchange the delivery should be published to. Only the final value, after all event
         * observers are finished, will be used.
         *
         * @param exchange name of the exchange the delivery should be published to
         */
        void setExchange(String exchange);

        /**
         * Replaces the routing key the delivery should be published with. Only the final value, after all event *
         * observers are finished, will be used.
         *
         * @param routingKey routing key the delivery should be published with
         */
        void setRoutingKey(String routingKey);
    }

    non-sealed interface Request<T> extends ProcessOutgoing<T> {

        /**
         * Replaces the name of  the exchange the delivery should be published to. Only the final value, after all event
         * observers are finished, will be used.
         *
         * @param exchange name of the exchange the delivery should be published to
         */
        void setExchange(String exchange);

        /**
         * Replaces the routing key the delivery should be published with. Only the final value, after all event *
         * observers are finished, will be used.
         *
         * @param routingKey routing key the delivery should be published with
         */
        void setRoutingKey(String routingKey);
    }

    /**
     * An event fired for every outgoing RPC response. Does not allow mutating {@link ProcessDelivery#exchange()} the
     * name of the reply exchange{@link BasicProperties#getCorrelationId()}
     */
    non-sealed interface Response<T> extends ProcessOutgoing<T> {

        /**
         * @return the name of the exchange of the outgoing RPC response, which is always the empty string, i.e. the
         * built-in default exchange.
         */
        @Override
        default String exchange() {
            return "";
        }

        /**
         * @return the routing key of the outgoing RPC response, which is always equal to the
         * {@link BasicProperties#getReplyTo() replyTo property} of the request.
         */
        @Override
        default String routingKey() {
            return this.request().getProperties().getReplyTo();
        }

        /**
         * If called, no further handling of the delivery will happen after the observers of this event are finished,
         * i.e. no message will be published to the broker. There is no way to "un-veto" in later observer methods once
         * {@code veto()} has been called.
         * <p>
         * This means that the client which sent the request may end up waiting indefinitely, so use with caution!
         */
        @Override
        void veto();

        /**
         * @return the delivery that is being responded to.
         */
        Delivery request();

        /**
         * Allows mutating almost all the properties of the response except {@link BasicProperties#getCorrelationId()},
         * because that is needed to route the response back correctly to the client and to associate the response with
         * the request respectively. Any attempt to overwrite the value will result in an
         * {@link UnsupportedOperationException}.
         * <p>
         * Only the final state of the builder is used in the message sent to the broker.
         *
         * @return A {@link PropertiesBuilder} that allows mutating the properties of the response expect the
         * correlation ID.
         */
        @Override
        PropertiesBuilder properties();
    }
}
