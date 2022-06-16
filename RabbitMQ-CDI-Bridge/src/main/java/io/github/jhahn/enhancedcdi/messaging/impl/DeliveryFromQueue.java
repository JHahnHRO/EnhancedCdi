package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.Delivery;

record DeliveryFromQueue(Delivery message, String queue) {}
