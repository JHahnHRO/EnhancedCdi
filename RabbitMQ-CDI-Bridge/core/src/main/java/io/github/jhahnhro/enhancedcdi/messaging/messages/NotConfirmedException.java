package io.github.jhahnhro.enhancedcdi.messaging.messages;

/**
 * Thrown when a message was published to the broker with publisher confirms enabled, but the broker could not process
 * the message.
 *
 * @see <a href="https://www.rabbitmq.com/confirms.html#publisher-confirms">RabbitMQ doc on Publisher Confirms</a>
 */
public class NotConfirmedException extends RuntimeException {}
