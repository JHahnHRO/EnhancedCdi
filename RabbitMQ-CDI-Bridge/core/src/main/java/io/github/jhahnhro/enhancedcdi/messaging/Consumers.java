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
            if (qos < 0 || 65535 < qos) {
                throw new IllegalArgumentException("QoS must be between 0 and 65535");
            }
            if (qos > 0 && autoAck) {
                throw new IllegalArgumentException("If prefetchCount is greater zero, autoAck must be false.");
            }
            arguments = Map.copyOf(arguments);
        }

        public Options() {
            this(0, true, false, Collections.emptyMap());
        }

        public Options withAutoAck(boolean autoAck) {
            return new Options(0, autoAck, this.exclusive, this.arguments);
        }

        /**
         * Returns new Options with qos equal to the given number. If {@code qos > 0}, then {@link #autoAck} will also
         * be set to false.
         *
         * @param qos
         * @return new Options with qos equal to the given number.
         */
        public Options withQoS(int qos) {
            return new Options(qos, qos <= 0 && this.autoAck, this.exclusive, this.arguments);
        }
    }
}
