package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static io.github.jhahnhro.enhancedcdi.messaging.impl.PriorityComparator.HIGHEST_FIRST;

import java.io.IOException;
import java.io.InputStream;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.InvalidMessageException;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.SelectableMessageReader;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;

/**
 * Represents the auto-selection process for {@link SelectableMessageReader}s and makes it available to clients:
 * <ul>
 *     <li>It is possible to inject a {@code @Selected MessageReader<Object>}. All calls to {@link #read(Incoming)}
 *     are delegated to the {@code SelectableMessageReader} instance that has been selected for the incoming RabbitMQ
 *     message in the current RequestScope</li>
 *     <li>Decorators can bind to the {@code @Selected MessageReader<Object>} bean and introduce behaviour that will be
 *     applied to the selected MessageReader for every incoming message, not matter what MessageReader will actually be
 *     selected in each case.</li>
 * </ul>
 */
@RequestScoped
@Selected
class SelectedMessageReader implements MessageReader<Object> {
    @Inject
    @Any
    Instance<SelectableMessageReader<?>> allReaders;

    private MessageReader<?> reader;

    public void selectReader(Incoming<byte[]> rawMessage) {
        this.reader = allReaders.stream()
                .filter(messageReader -> messageReader.canRead(rawMessage))
                .min(HIGHEST_FIRST)
                .orElseThrow(() -> new IllegalStateException("No message reader applicable to incoming message"));
    }

    @Override
    public Object read(Incoming<InputStream> message) throws InvalidMessageException, IOException {
        return reader.read(message);
    }
}
