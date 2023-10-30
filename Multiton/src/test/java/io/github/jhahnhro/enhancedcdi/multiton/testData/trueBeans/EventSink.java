package io.github.jhahnhro.enhancedcdi.multiton.testData.trueBeans;

import java.util.function.BiConsumer;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.EventMetadata;

import io.github.jhahnhro.enhancedcdi.multiton.testData.Color;

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
