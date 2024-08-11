package io.github.jhahnhro.enhancedcdi.messaging.impl.producers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import io.github.jhahnhro.enhancedcdi.messaging.Consolidated;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Qualifier;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
class TopologyProducerTest {

    public static final AMQP.Exchange.Declare EXCHANGE_1 = new AMQP.Exchange.Declare.Builder().exchange("exchange1")
            .type(BuiltinExchangeType.TOPIC.getType())
            .build();
    public static final AMQP.Exchange.Declare EXCHANGE_2 = new AMQP.Exchange.Declare.Builder().exchange("exchange2")
            .type(BuiltinExchangeType.TOPIC.getType())
            .build();
    @Produces
    @Dependent
    static Topology topologyWithoutQualifier = new Topology.Builder().addExchangeDeclaration(EXCHANGE_1).build();
    @Produces
    @MyQualifier
    @Dependent
    static Topology topologyWithQualifier = new Topology.Builder().addExchangeDeclaration(EXCHANGE_2).build();


    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(TopologyProducer.class, TopologyProducerTest.class).build();

    @Test
    void testConsolidation(@Consolidated Topology topology) {
        assertThat(topology.exchangeDeclarations()).containsExactlyInAnyOrder(EXCHANGE_1, EXCHANGE_2);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface MyQualifier {}
}