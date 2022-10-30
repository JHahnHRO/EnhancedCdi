package io.github.jhahn.enhancedcdi.messaging;

import java.io.IOException;
import java.util.Objects;

public interface Consumers {

    default void startReceiving(String queue) throws IOException, InterruptedException {
        startReceiving(queue, new ConsumerOptions());
    }

    void startReceiving(String queue, ConsumerOptions consumerOptions) throws IOException, InterruptedException;

    void stopReceiving(String queue) throws IOException;


    record ConsumerOptions(MessageAcknowledgment.Mode acknowledgementMode, int qos) {
        public ConsumerOptions {
            Objects.requireNonNull(acknowledgementMode);
        }

        public ConsumerOptions() {
            this(MessageAcknowledgment.Mode.AUTO, 0);
        }

        public ConsumerOptions withAcknowledgementMode(MessageAcknowledgment.Mode acknowledgementMode) {
            return new ConsumerOptions(acknowledgementMode, this.qos);
        }

        public ConsumerOptions withQoS(int qos) {
            return new ConsumerOptions(this.acknowledgementMode, qos);
        }
    }
}
