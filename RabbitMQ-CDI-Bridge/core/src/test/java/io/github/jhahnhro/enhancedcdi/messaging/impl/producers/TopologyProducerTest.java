package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
class TopologyProducerTest {

    @Produces
    @Dependent
    static Topology topology = new Topology.Builder().build();
    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(TopologyProducer.class, TopologyProducerTest.class).build();

    @Test
    void testConsolidation(@Consolidated Topology topology) {
        // no-op. If the injection was successful, everything went well
    }
}