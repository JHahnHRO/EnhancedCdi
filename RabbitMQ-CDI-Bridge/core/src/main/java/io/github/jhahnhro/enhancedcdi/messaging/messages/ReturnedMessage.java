package io.github.jhahnhro.enhancedcdi.messaging.messages;

import java.util.Objects;

public record ReturnedMessage(int replyCode, String replyText, Outgoing<byte[]> message) {

    public ReturnedMessage {
        Objects.requireNonNull(replyText);
        Objects.requireNonNull(message);
    }
}
