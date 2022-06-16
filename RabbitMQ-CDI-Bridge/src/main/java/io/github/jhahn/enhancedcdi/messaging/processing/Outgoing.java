package io.github.jhahn.enhancedcdi.messaging.processing;

import com.rabbitmq.client.BasicProperties;

public record Outgoing<T>(String exchange, String routingKey, BasicProperties properties, T content) {

}
