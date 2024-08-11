package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import java.util.function.BiConsumer;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.EventMetadata;

@ApplicationScoped
public class EventSink {
    BiConsumer<Color, EventMetadata> colorConsumer = (c, m) -> {};

    public void setColorConsumer(BiConsumer<Color, EventMetadata> colorConsumer) {
        this.colorConsumer = colorConsumer;
    }

    private void receiveAnyColorEvent(@Observes Color colorEvent, EventMetadata metadata) {
        colorConsumer.accept(colorEvent, metadata);
    }
}
