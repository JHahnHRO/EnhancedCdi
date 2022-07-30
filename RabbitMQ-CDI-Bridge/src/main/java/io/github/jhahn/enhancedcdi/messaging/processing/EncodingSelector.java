package io.github.jhahn.enhancedcdi.messaging.processing;

import io.github.jhahn.enhancedcdi.messaging.Header;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.inject.Singleton;
import java.util.Optional;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;

@Singleton
public class EncodingSelector {

    void selectEncoding(@Observes @Priority(LIBRARY_AFTER + 899) ProcessOutgoing.Response<?> processOutgoing, //
                        @Header("Accept-Encoding") String acceptableEncodings) {
        if (processOutgoing.properties().getContentEncoding() == null && acceptableEncodings != null) {
            Optional<String> selectedEncoding = selectEncoding(acceptableEncodings);
            selectedEncoding.ifPresent(s -> processOutgoing.properties().setContentEncoding(s));
        }
    }


    private Optional<String> selectEncoding(String acceptableEncodings) {
        return QValues.parse(acceptableEncodings).getPreferredValue();
    }
}
