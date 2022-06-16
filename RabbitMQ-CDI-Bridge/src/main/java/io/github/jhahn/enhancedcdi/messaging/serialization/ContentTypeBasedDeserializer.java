package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ContentTypeBasedDeserializer<T> extends Deserializer<T> {

    Pattern getContentTypePattern();

    @Override
    default boolean canDeserialize(BasicProperties messageProperties) {
        final Matcher matcher = getContentTypeMatcher(messageProperties);
        return matcher != null && matcher.matches();
    }

    default Matcher getContentTypeMatcher(BasicProperties messageProperties) {
        return Optional.ofNullable(messageProperties)
                .map(BasicProperties::getContentType)
                .map(contentType -> getContentTypePattern().matcher(contentType))
                .orElse(null);
    }
}
