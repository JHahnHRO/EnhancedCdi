package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.InvalidMessageException;

import javax.enterprise.inject.spi.Prioritized;
import java.io.IOException;
import java.io.InputStream;

/**
 * Deserializes Java objects from certain RabbitMQ messages. A Deserializer decides whether it is applicable to a given
 * delivery by examining the metadata, i.e. the delivery's {@link Envelope} (contain the name of the exchange and the
 * routing key) and {@link BasicProperties}.
 * <p>
 * It should always be tested (with {@link #isApplicable(Envelope, BasicProperties)}) whether a specific Deserializer is
 * applicable to a given message before attempting serialization.
 * <p>
 * Deserializers should be CDI beans or created by a CDI bean of type {@link DeserializerProvider} in order to be
 * automatically discovered. It is also possible to register custom Deserializer instances with
 * {@link SerializationSelector#register(Deserializer)}.
 * <p>
 * By default, what {@link Deserializer} to use for any given delivery is determined by selecting the Deserializer with
 * the highest {@link #getPriority() priority} among those that are
 * {@link #isApplicable(Envelope, BasicProperties) applicable}. If multiple Deserializer instances with the same
 * priority are applicable to a delivery, it is undefined which one will be selected.
 * <p>
 * But it is possible to disable automatic selection and force the usage of a specific Deserializer instance by
 * {@link javax.enterprise.event.Observes observing} a
 * {@link io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming} event and calling
 * {@link io.github.jhahn.enhancedcdi.messaging.processing.ProcessIncoming#setDeserializer(Deserializer)} on it.
 *
 * @param <T> type of the deserialized objects
 * @implSpec Deserializer instances should be thread-safe and re-entrant. Ideally they are completely stateless.
 * @see SerializationSelector
 * @see ByteArrayCodec
 * @see PlainTextCodec
 */
public interface Deserializer<T> extends Prioritized {

    /**
     * Automatic Deserializer-selection uses {@link #isApplicable(Envelope, BasicProperties)} to determine the
     * applicable Deserializers and then selects the one with the highest priority.
     *
     * @return the priority of this Deserializer
     */
    @Override
    int getPriority();

    /**
     * Tests whether this Deserializer is applicable to a message with the given metadata, e.g. by evaluating the
     * message's {@link BasicProperties#getContentType() content type}, its headers or something else.
     *
     * @return {@code true} iff this Deserializer is applicable to a message with the given metadata
     */
    boolean isApplicable(Envelope envelope, BasicProperties messageProperties);

    /**
     * Deserializes an incoming RabbitMQ delivery. If this deserializer was automatically selected, it
     *
     * @param envelope          envelope of the delivery, containing the name of the exchange and the delivery's
     *                          routingKey
     * @param messageProperties message properties of the delivery
     * @param messageBody       the {@link InputStream} from which to read the message body
     * @return the content of the message
     * @throws IllegalArgumentException if this Deserializer is not applicable to a message with the provided metadata
     * @throws InvalidMessageException  if the message could not be serialized
     * @throws IOException              if the message could not be read from the InputStream
     */
    T deserialize(Envelope envelope, BasicProperties messageProperties, InputStream messageBody)
            throws InvalidMessageException, IOException;

}
