package io.github.jhahnhro.enhancedcdi.messaging.impl;

import io.github.jhahnhro.enhancedcdi.messaging.MessageAcknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

record InternalDelivery(Incoming<byte[]> rawMessage, MessageAcknowledgment ack) {
}
