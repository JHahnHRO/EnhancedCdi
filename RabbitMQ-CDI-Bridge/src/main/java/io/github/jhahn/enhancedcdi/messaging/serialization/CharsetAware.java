package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface CharsetAware {
    /**
     * See {@link Charset}
     */
    Pattern CHARSET_PATTERN = Pattern.compile("(; charset=(?<charset>[a-zA-Z0-9][a-zA-Z0-9\\-+.:_]*))?$");

    default Charset getCharset(BasicProperties messageProperties) {
        return Optional.ofNullable(messageProperties)
                .map(BasicProperties::getContentType)
                .map(CHARSET_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group("charset"))
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);
    }
}
