package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import javax.enterprise.context.Dependent;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Dependent
public class StringCodec
        implements ContentTypeBasedDeserializer<String>, CharsetAware, BuiltInCodec<String, CharSequence> {
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^text/[a-zA-Z0-9.+_\\-]+");

    //region Decoder
    @Override
    public Pattern getContentTypePattern() {
        return CONTENT_TYPE_PATTERN;
    }

    @Override
    public Deserialized<String> deserialize(InputStream messageBody, BasicProperties properties) throws IOException {
        final Matcher matcher = getContentTypeMatcher(properties);
        if (!matcher.matches()) {
            throw new IllegalStateException("deserialize() called on non-text message");
        }
        StringWriter sw = new StringWriter();
        new InputStreamReader(messageBody, getCharset(properties)).transferTo(sw);
        return new Deserialized<>(sw.toString());
    }
    //endregion

    //region Encoder
    @Override
    public Class<CharSequence> getEncodableType() {
        return CharSequence.class;
    }

    @Override
    public byte[] serialize(CharSequence payload, PropertiesBuilder responseProperties) {
        return payload == null ? null : payload.toString().getBytes(getCharset(responseProperties));
    }
    //endregion

}
