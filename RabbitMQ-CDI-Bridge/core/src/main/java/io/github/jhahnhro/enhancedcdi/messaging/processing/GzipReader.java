package io.github.jhahnhro.enhancedcdi.messaging.processing;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

import com.rabbitmq.client.AMQP;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageReader;

/**
 * A decorator that adds automatic decompression for incoming messages with
 * {@link AMQP.BasicProperties#getContentEncoding() content encoding} set to "gzip" or "deflate".
 *
 * @param <T> type of the read message.
 */
@Decorator
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 500)
abstract class GzipReader<T> implements MessageReader<T> {
    @Inject
    @Any
    @Delegate
    MessageReader<T> messageReader;

    @Override
    public T read(Incoming<InputStream> message) throws IOException {
        final String contentEncoding = message.properties().getContentEncoding();

        InputStream inputStream = message.content();
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            inputStream = new GZIPInputStream(inputStream);
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            inputStream = new InflaterInputStream(inputStream);
        }

        try (var decoratedStream = inputStream) {
            return messageReader.read(message.withContent(decoratedStream));
        }
    }

}