package io.github.jhahn.enhancedcdi.messaging.serialization;

import io.github.jhahn.enhancedcdi.messaging.messages.Incoming;
import io.github.jhahn.enhancedcdi.messaging.messages.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.messages.OutgoingMessageBuilder;

import javax.enterprise.context.Dependent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Dependent
public class PlainTextReaderWriter<CS extends CharSequence> implements MessageReader<String>, MessageWriter<CS> {

    public static final Pattern CONTENT_TYPE = Pattern.compile(
            "text/plain(; charset=(?<charset>[a-zA-Z0-9][a-zA-Z0-9\\-+.:_]*))?$");

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    //region Decoder
    @Override
    public boolean canRead(Incoming<byte[]> message) {
        final String contentType = message.properties().getContentType();

        return contentType != null && contentType.startsWith("text/plain");
    }

    @Override
    public String read(Incoming<InputStream> messageBody) throws IOException {
        final Matcher matcher = CONTENT_TYPE.matcher(messageBody.properties().getContentType());
        if (!matcher.find()) {
            throw new IllegalArgumentException("read() called on non-text message");
        }
        final String charsetName = matcher.group("charset");
        final Charset charset = charsetName != null ? Charset.forName(charsetName) : StandardCharsets.UTF_8;

        return new String(messageBody.content().readAllBytes(), charset);
    }
    //endregion

    //region Encoder
    @Override
    public boolean canWrite(Outgoing<CS> message) {
        return message.content() != null;
    }

    @Override
    public void write(Outgoing<CS> originalMessage, OutgoingMessageBuilder<?, OutputStream> outgoingMessageBuilder)
            throws IOException {
        outgoingMessageBuilder.propertiesBuilder().contentType("text/plain; charset=UTF-8");
        new OutputStreamWriter(outgoingMessageBuilder.content(), StandardCharsets.UTF_8).append(
                originalMessage.content());
    }
    //endregion

}
