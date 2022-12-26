package io.github.jhahnhro.enhancedcdi.messaging.serialization;

/**
 * Thrown when serializing a java object results in a byte array larger than 128 MiB, which is the maximum supported
 * size for RabbitMQ messages.
 */
public class MessageTooLargeException extends IllegalArgumentException {}
