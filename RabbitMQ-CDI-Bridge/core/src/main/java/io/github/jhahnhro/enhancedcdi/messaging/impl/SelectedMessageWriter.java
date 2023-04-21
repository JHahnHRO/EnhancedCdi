package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static io.github.jhahnhro.enhancedcdi.messaging.impl.Serialization.HIGHEST_PRIORITY_FIRST;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.messages.MessageBuilder;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;
import io.github.jhahnhro.enhancedcdi.types.ParameterizedTypeImpl;
import io.github.jhahnhro.enhancedcdi.util.EnhancedInstance;

/**
 * Represents the auto-selection process for {@link SelectableMessageWriter}s and makes it available to clients:
 * <ul>
 *     <li>It is possible to inject a {@code @Selected MessageWriter<T>} of any type {@code T} and when
 *     {@link #write(Outgoing, MessageBuilder)} is called on the injected instance, the call will be delegated to
 *     the applicable {@code SelectableMessageWriter<T>} with the highest priority.</li>
 *     <li>Decorators can bind to the {@code @Selected MessageWriter<T>} bean and introduce behaviour that will be
 *     applied to the selected MessageWriter for every outgoing message, not matter what MessageWriter will actually be
 *     selected in each case.</li>
 * </ul>
 *
 * @param <T> the type
 */
@Dependent
@Selected
class SelectedMessageWriter<T> implements MessageWriter<T> {

    private Type typeT;
    private List<SelectableMessageWriter<T>> writers;

    @Inject
    void setWriters(InjectionPoint injectionPoint, EnhancedInstance<SelectableMessageWriter<?>> enhancedInstance) {
        typeT = ((ParameterizedType) injectionPoint.getType()).getActualTypeArguments()[0];
        Type messageWriterType = new ParameterizedTypeImpl(SelectableMessageWriter.class, null, typeT);

        this.writers = enhancedInstance.<SelectableMessageWriter<T>>select(messageWriterType, Any.Literal.INSTANCE)
                .stream()
                .sorted(HIGHEST_PRIORITY_FIRST)
                .toList();

        if (this.writers.isEmpty()) {
            // fail-fast
            throw new IllegalStateException("No MessageWriter for type " + typeT + " exists");
        }
    }

    @Override
    public void write(Outgoing<T> originalMessage, MessageBuilder<OutputStream,?> serializedMessage)
            throws IOException {
        final MessageWriter<T> applicableWriter = writers.stream()
                .filter(writer -> writer.canWrite(originalMessage))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No MessageWriter for type " + typeT + " is applicable to the message"));

        applicableWriter.write(originalMessage, serializedMessage);
    }

}
