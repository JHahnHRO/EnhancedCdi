package io.github.jhahn.enhancedcdi.messaging.processing;

import io.github.jhahn.enhancedcdi.messaging.Header;

import javax.enterprise.event.Observes;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipProcessor {

    void unzip(@Observes ProcessIncoming pid) throws IOException {
        final String contentEncoding = pid.properties().getContentEncoding();
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            pid.setBody(new GZIPInputStream(pid.body()));
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            pid.setBody(new DeflaterInputStream(pid.body()));
        }
    }

    private static final Set<String> SUPPORTED_ENCODINGS = Set.of("gzip", "deflate", "identity");

    void zip(@Observes ProcessOutgoing pod, @Header("Accept-Encoding") String acceptableEncodings)
            throws IOException {
        final String contentEncoding = pod.properties().getContentEncoding();
        if (contentEncoding == null) {
            final Optional<PrioritizedOption> supportedEncoding = findAcceptableEncodings(contentEncoding).stream()
                    .sorted(Comparator.comparing(PrioritizedOption::priority).reversed())
                    .filter(SUPPORTED_ENCODINGS::contains)
                    .findFirst();

            if (supportedEncoding.isEmpty()) {
                return;
            }

            if ("gzip".equalsIgnoreCase(supportedEncoding.get().name())) {
                pod.setBody(new GZIPOutputStream(pod.body()));
                pod.properties().setContentEncoding("gzip");
            } else if ("deflate".equalsIgnoreCase(supportedEncoding.get().name())) {
                pod.setBody(new DeflaterOutputStream(pod.body()));
                pod.properties().setContentEncoding("deflate");
            }
        }
    }

    private List<PrioritizedOption> findAcceptableEncodings(String contentEncoding) {
        return null;
    }

    private record PrioritizedOption(String name, float priority) {}
}
