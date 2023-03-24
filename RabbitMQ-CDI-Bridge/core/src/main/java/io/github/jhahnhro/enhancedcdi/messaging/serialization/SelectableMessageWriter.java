package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.OutputStream;
import javax.enterprise.inject.spi.Prioritized;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

/**
 * Serializes Java objects into an {@link OutputStream}.The {@link #canWrite(Outgoing)} method decides whether this
 * {@link SelectableMessageWriter} is applicable to a given {@link Outgoing outgoing message}.
 * <p>
 * It should always be tested (with {@link #canWrite(Outgoing)}) whether a specific {@code SelectableMessageWriter} is applicable
 * to a given message before attempting serialization.
 * <p>
 * By default, what {@code SelectableMessageWriter} to use for any given object is determined by selecting the
 * {@code SelectableMessageWriter} with the highest {@link #getPriority() priority} among those that are applicable. If multiple
 * {@code SelectableMessageWriter} instances with the same priority are applicable, it is undefined which one will be selected.
 *
 * @param <T> type of the message contents that this {@code SelectableMessageWriter} can serialize.
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface SelectableMessageWriter<T> extends Prioritized, MessageWriter<T> {

    /**
     * Automatic {@code MessageReader}-selection uses {@link #canWrite(Outgoing)} to determine the
     * {@code SelectableMessageWriter}s that are applicable to an incoming message and then selects the one with the highest
     * priority.
     *
     * @return the priority of this {@link SelectableMessageWriter}.
     */
    @Override
    int getPriority();

    /**
     * A (reasonably quick) test whether this {@code SelectableMessageWriter} can serialize the given message, e.g. by examining
     * the {@link BasicProperties#getHeaders() headers} or do an {@code instanceof} check on the content.
     *
     * @param message a message
     * @return {@code true} this {@code SelectableMessageWriter} can serialize the given message.
     * @implNote It is not recommended to make an attempt at serialization, because this method will be called on all
     * {@code SelectableMessageWriter}-instances to determine which {@code SelectableMessageWriter} should perform the serialization in the
     * end.
     */
    boolean canWrite(Outgoing<T> message);

}
