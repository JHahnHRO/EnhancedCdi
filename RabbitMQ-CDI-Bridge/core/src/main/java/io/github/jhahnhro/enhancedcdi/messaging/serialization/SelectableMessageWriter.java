package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import jakarta.enterprise.inject.spi.Prioritized;

/**
 * A {@link MessageWriter} that participates in automatic serialization. If a bean has this interface in its bean types,
 * then it will be automatically discovered and among the discovered writers the appropriate one will be automatically
 * selected for each incoming message.
 * <p>
 * Whether a particular writer is applicable to a given message is decided by the {@link #canWrite(Outgoing)} method.
 * <p>
 * It should always be tested (with {@link #canWrite(Outgoing)}) whether a specific {@code SelectableMessageWriter} is
 * applicable to a given message before attempting serialization.
 * <p>
 * The {@code SelectableMessageWriter} that is selected for any given message is determined by selecting the writer with
 * the highest {@link #getPriority() priority} among those that are applicable. If multiple instances with the same
 * priority are applicable to a message, it is undefined which one will be selected.
 * <p>
 * The library provides a {@link jakarta.enterprise.context.Dependent} scoped bean with type {@code MessageWriter<T>} and
 * qualifier {@link Selected} that automatically selects the right {@code SelectableMessageWriter} for each call to
 * {@link #write(Outgoing, MessageBuilder)}.
 *
 * @param <T> type of the message contents that this {@code SelectableMessageWriter} can serialize.
 * @see ByteArrayReaderWriter
 * @see PlainTextReaderWriter
 */
public interface SelectableMessageWriter<T> extends Prioritized, MessageWriter<T> {

    /**
     * Automatic {@code MessageWriter}-selection uses {@link #canWrite(Outgoing)} to determine the
     * {@code SelectableMessageWriter}s that are applicable to an outgoing message and then selects the one with the
     * highest priority.
     *
     * @return the priority of this {@link SelectableMessageWriter}.
     */
    @Override
    int getPriority();

    /**
     * A (reasonably quick) test whether this {@code SelectableMessageWriter} can serialize the given message, e.g. by
     * examining the {@link BasicProperties#getHeaders() headers} or do an {@code instanceof} check on the content.
     *
     * @param message a message
     * @return {@code true} this {@code SelectableMessageWriter} can serialize the given message.
     * @implNote It is not recommended to make an attempt at serialization, because this method will be called on all
     * {@code SelectableMessageWriter}-instances to determine which {@code SelectableMessageWriter} should perform the
     * serialization in the end.
     */
    boolean canWrite(Outgoing<T> message);

}
