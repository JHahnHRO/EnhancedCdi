package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;

public interface Consumers {

    default void startReceiving(String queue) throws IOException {
        startReceiving(queue, new Options());
    }

    void startReceiving(String queue, Options options) throws IOException;

    void stopReceiving(String queue) throws IOException;


    record Options(int qos, boolean autoAck) {
        public Options() {
            this(0, true);
        }

        public Options withAutoAck(boolean autoAck) {
            return new Options(this.qos, autoAck);
        }

        public Options withQoS(int qos) {
            return new Options(qos, this.autoAck);
        }
    }
}
