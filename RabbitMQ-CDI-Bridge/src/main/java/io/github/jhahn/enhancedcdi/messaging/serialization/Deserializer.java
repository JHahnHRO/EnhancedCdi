package io.github.jhahn.enhancedcdi.messaging.serialization;

import com.rabbitmq.client.BasicProperties;
import io.github.jhahn.enhancedcdi.messaging.InvalidMessageException;

import javax.enterprise.inject.spi.Prioritized;
import java.io.IOException;
import java.io.InputStream;

public interface Deserializer<T> extends Prioritized {
    boolean canDeserialize(BasicProperties messageProperties);

    Deserialized<T> deserialize(InputStream messageBody, BasicProperties properties)
            throws InvalidMessageException, IOException;

}
