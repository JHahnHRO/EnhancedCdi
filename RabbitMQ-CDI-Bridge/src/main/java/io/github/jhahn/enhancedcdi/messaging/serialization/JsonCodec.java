package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.InvalidMessageException;
import io.github.jhahn.enhancedcdi.messaging.PropertiesBuilder;

import javax.enterprise.context.Dependent;
import javax.json.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.regex.Pattern;

@Dependent
public class JsonCodec
        implements ContentTypeBasedDeserializer<JsonStructure>, BuiltInCodec<JsonStructure, JsonValue>, CharsetAware {
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^application/json");

    //region Decoder
    @Override
    public Pattern getContentTypePattern() {
        return CONTENT_TYPE_PATTERN;
    }

    @Override
    public Deserialized<JsonStructure> deserialize(InputStream messageBody, BasicProperties properties) throws InvalidMessageException {
        JsonReaderFactory jsonReaderFactory = Json.createReaderFactory(Collections.emptyMap());

        final Charset charset = getCharset(properties);

        try (JsonReader jsonReader = jsonReaderFactory.createReader(messageBody, charset)) {
            return new Deserialized<>(jsonReader.read());
        }
    }
    //endregion

    //region Encoder
    @Override
    public Class<JsonValue> getEncodableType(){
        return JsonValue.class;
    }

    @Override
    public byte[] serialize(JsonValue payload, PropertiesBuilder responseProperties) {
        final JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(Collections.emptyMap());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final Charset charset = getCharset(responseProperties);
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(outputStream, charset)) {
            jsonWriter.write(payload);
        }
        return outputStream.toByteArray();
    }
    //endregion
}
