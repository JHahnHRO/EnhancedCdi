package io.github.jhahnhro.enhancedcdi.messaging.impl;

import java.util.Comparator;
import javax.enterprise.inject.spi.Prioritized;

public enum PriorityComparator implements Comparator<Prioritized> {
    LOWEST_FIRST {
        @Override
        public int compare(Prioritized left, Prioritized right) {
            return left.getPriority() - right.getPriority();
        }
    }, HIGHEST_FIRST {
        @Override
        public int compare(Prioritized left, Prioritized right) {
            return right.getPriority() - left.getPriority();
        }
    }
}
