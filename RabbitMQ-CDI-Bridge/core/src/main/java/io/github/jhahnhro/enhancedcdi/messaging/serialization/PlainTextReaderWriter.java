package io.github.jhahnhro.enhancedcdi.messaging.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;

import io.github.jhahnhro.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahnhro.enhancedcdi.messaging.messages.Outgoing;

@Singleton
public class PlainTextReaderWriter implements SelectableMessageReader<String>, SelectableMessageWriter<String> {

    private static final Pattern CONTENT_TYPE = Pattern.compile(
            "^text/plain(; charset=(?<charset>[a-zA-Z0-9][a-zA-Z0-9\\-+.:_]*))?$");

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    //region Decoder
    @Override
    public boolean canRead(Incoming<byte[]> message) {
        final String contentType = message.properties().getContentType();

        return contentType != null && CONTENT_TYPE.matcher(contentType).matches();
    }

    @Override
    public String read(Incoming<InputStream> messageBody) throws IOException {
        final Matcher matcher = CONTENT_TYPE.matcher(messageBody.properties().getContentType());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("read() called on non-text message");
        }
        final String charsetName = matcher.group("charset");
        final Charset charset = charsetName != null ? Charset.forName(charsetName) : StandardCharsets.UTF_8;

        try (InputStream inputStream = messageBody.content()) {
            return new String(inputStream.readAllBytes(), charset);
        }
    }
    //endregion

    //region Encoder
    @Override
    public boolean canWrite(Outgoing<String> message) {
        return message.content() != null;
    }

    @Override
    public void write(Outgoing<String> originalMessage, Outgoing.Builder<OutputStream> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder().contentType("text/plain; charset=UTF-8");
        try (var stream = outgoingMessageBuilder.content();
             var writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.append(originalMessage.content());
        }
    }
    //endregion

}
