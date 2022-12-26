package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;
import java.util.Objects;

public interface Consumers {

    default void startReceiving(String queue) throws IOException, InterruptedException {
        startReceiving(queue, new Options());
    }

    void startReceiving(String queue, Options options) throws IOException, InterruptedException;

    void stopReceiving(String queue) throws IOException;


    record Options(MessageAcknowledgment.Mode acknowledgementMode, int qos) {
        public Options {
            Objects.requireNonNull(acknowledgementMode);
        }

        public Options() {
            this(MessageAcknowledgment.Mode.AUTO, 0);
        }

        public Options withAcknowledgementMode(MessageAcknowledgment.Mode acknowledgementMode) {
            return new Options(acknowledgementMode, this.qos);
        }

        public Options withQoS(int qos) {
            return new Options(this.acknowledgementMode, qos);
        }
    }
}
