package io.github.jhahnhro.enhancedcdi.messaging.processing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Priority;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.MessageWriter;
import io.github.jhahnhro.enhancedcdi.messaging.serialization.Selected;

/**
 * Decorator that adds automatic gzip or deflate compression to outgoing responses if
 * <ul>
 *     <li>the corresponding request had a header named "Accept-Encoding". It is interpreted the same way as the
 *     <a href="https://datatracker.ietf.org/doc/html/rfc2616#section-14.3">HTTP-header of the same name</a>,
 *     i.e. as a list of QValues where higher weights indicate the preferred choice of encoding.</li>
 *     <li>No other {@link BasicProperties#getContentEncoding() content encoding} has been set on the outgoing
 *     message.</li>
 * </ul>
 *
 * @param <T>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc2616#section-14.3">RFC 2616, section 14.3</a>
 */
@Decorator
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 500)
abstract class GzipWriter<T> implements MessageWriter<T> {
    private static final Set<String> SUPPORTED_ENCODINGS = Set.of("*", "gzip", "deflate");
    @Inject
    @Any
    @Delegate
    MessageWriter<T> messageWriter;


    @Override
    public void write(Outgoing<T> originalMessage, Outgoing.Builder<OutputStream> messageBuilder) throws IOException {
        OutputStream outputStream = messageBuilder.content();

        if (messageBuilder.properties().getContentEncoding() == null
            && originalMessage instanceof Outgoing.Response<?, ?> response) {
            String contentEncoding = getAcceptableContentEncoding(response.request());

            if ("*".equals(contentEncoding) || "gzip".equals(contentEncoding)) {
                messageBuilder.propertiesBuilder().contentEncoding("gzip");
                outputStream = new GZIPOutputStream(outputStream);
            } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
                messageBuilder.propertiesBuilder().contentEncoding("deflate");
                outputStream = new DeflaterOutputStream(outputStream);
            }
        }

        try (var decoratedStream = outputStream) {
            messageBuilder.setContent(decoratedStream);
            messageWriter.write(originalMessage, messageBuilder);
        }
    }

    /**
     * Returns  the acceptable content encoding with the highest weight or {@code null} if none of the supported
     * encodings is acceptable. Absence of the "Accept-Encoding" header is interpreted as "any encoding is acceptable"
     * in accordance with RFC 2616.
     *
     * @param request the original request
     * @return the acceptable content encoding with the highest weight or {@code null} if none of the supported
     * encodings is acceptable.
     */
    private String getAcceptableContentEncoding(Incoming.Request<?> request) {
        final String acceptEncodingHeader = Optional.ofNullable(request.properties().getHeaders())
                .map(headers -> headers.get("Accept-Encoding"))
                .map(String.class::cast)
                // From RFC 2616 section 14.3:
                // "If no Accept-Encoding field is present in a request, the server MAY
                //  assume that the client will accept any content coding. In this case,
                //  if 'identity' is one of the available content-codings, then the
                //  server SHOULD use the 'identity' content-coding, unless it has
                //  additional information that a different content-coding is meaningful
                //  to the client."
                .orElse("identity");
        return this.selectEncoding(acceptEncodingHeader).orElse(null);
    }

    private Optional<String> selectEncoding(String acceptableEncodings) {
        return QValues.parse(acceptableEncodings)
                .getAcceptableValues()
                .stream()
                .map(QValues.QValue::value)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(SUPPORTED_ENCODINGS::contains)
                .findFirst();
    }
}
