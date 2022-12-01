package io.github.jhahn.enhancedcdi.messaging.impl;

import io.github.jhahn.enhancedcdi.messaging.MessageAcknowledgment;
import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;

record InternalDelivery(Incoming<byte[]> rawMessage, MessageAcknowledgment ack) {
}
