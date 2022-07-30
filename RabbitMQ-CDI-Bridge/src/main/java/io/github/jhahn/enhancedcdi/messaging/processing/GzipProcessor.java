package io.github.jhahn.enhancedcdi.messaging.processing;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

@Singleton
public class GzipProcessor {

    void unzip(@Observes @Priority(LIBRARY_BEFORE + 100) ProcessIncoming pid) throws IOException {
        final String contentEncoding = pid.properties().getContentEncoding();
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            pid.setBody(new GZIPInputStream(pid.body()));
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            pid.setBody(new DeflaterInputStream(pid.body()));
        }
    }

    void zip(@Observes @Priority(LIBRARY_AFTER + 900) ProcessOutgoing<?> pod) throws IOException {
        final String contentEncoding = pod.properties().getContentEncoding();
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            pod.setBody(new GZIPOutputStream(pod.body()));
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            pod.setBody(new DeflaterOutputStream(pod.body()));
        }
    }
}
