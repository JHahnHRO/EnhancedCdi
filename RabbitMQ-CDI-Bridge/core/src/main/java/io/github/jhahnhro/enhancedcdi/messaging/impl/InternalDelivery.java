package io.github.jhahnhro.enhancedcdi.messaging.impl;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgment;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

record InternalDelivery(Incoming<byte[]> rawMessage, Acknowledgment ack) {
}
