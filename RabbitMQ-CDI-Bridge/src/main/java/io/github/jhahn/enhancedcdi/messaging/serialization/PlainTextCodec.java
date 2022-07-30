package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

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
public class PlainTextCodec implements Deserializer<String>, Serializer<CharSequence> {

    public static final Pattern CONTENT_TYPE = Pattern.compile(
            "text/plain(; charset=(?<charset>[a-zA-Z0-9][a-zA-Z0-9\\-+.:_]*))?$");

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    //region Decoder
    @Override
    public boolean isApplicable(Envelope envelope, BasicProperties messageProperties) {
        final String contentType = messageProperties.getContentType();

        return contentType != null && contentType.startsWith("text/plain");
    }

    @Override
    public String deserialize(Envelope envelope, BasicProperties messageProperties, InputStream messageBody) throws IOException {
        final Matcher matcher = CONTENT_TYPE.matcher(messageProperties.getContentType());
        if (!matcher.find()) {
            throw new IllegalArgumentException("deserialize() called on non-text message");
        }
        final String charsetName = matcher.group("charset");
        final Charset charset = charsetName != null ? Charset.forName(charsetName) : StandardCharsets.UTF_8;

        return new String(messageBody.readAllBytes(), charset);
    }
    //endregion

    //region Encoder
    @Override
    public Class<CharSequence> serializableType() {
        return CharSequence.class;
    }

    @Override
    public void serialize(CharSequence payload, PropertiesBuilder responseProperties, OutputStream outputStream)
            throws IOException {
        responseProperties.setContentType("text/plain; charset=UTF-8");
        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8).append(payload);
    }
    //endregion

}
