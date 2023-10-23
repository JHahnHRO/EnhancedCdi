package io.github.jhahnhro.enhancedcdi.messaging.impl;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Acknowledgement;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;

record InternalDelivery(Incoming<byte[]> rawMessage, Acknowledgement ack) {
}
