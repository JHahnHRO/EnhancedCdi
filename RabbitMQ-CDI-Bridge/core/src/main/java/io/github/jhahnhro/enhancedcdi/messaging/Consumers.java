package io.github.jhahnhro.enhancedcdi.messaging;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public interface Consumers {

    default void startReceiving(String queue) throws IOException {
        startReceiving(queue, new Options());
    }

    void startReceiving(String queue, Options options) throws IOException;

    void stopReceiving(String queue) throws IOException;


    record Options(int qos, boolean autoAck, boolean exclusive, Map<String, Object> arguments) {

        public Options {
            if (0 > qos || qos > 65535) {
                throw new IllegalArgumentException("QoS must be between 0 and 65535");
            }
            arguments = Map.copyOf(arguments);
        }

        public Options() {
            this(0, true, false, Collections.emptyMap());
        }

        public Options withAutoAck(boolean autoAck) {
            return new Options(this.qos, autoAck, this.exclusive, this.arguments);
        }

        public Options withQoS(int qos) {
            return new Options(qos, this.autoAck, this.exclusive, this.arguments);
        }
    }
}
