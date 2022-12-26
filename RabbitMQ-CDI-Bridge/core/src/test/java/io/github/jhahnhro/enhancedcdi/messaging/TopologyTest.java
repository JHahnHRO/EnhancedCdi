package io.github.jhahnhro.enhancedcdi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Set;

import com.rabbitmq.client.AMQP;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopologyTest {

    @Nested
    class TestErrors {

        @ParameterizedTest
        @ValueSource(strings = {"", "amq.direct", "amq.fanout", "amq.topic", "amq.match", "amq.headers"})
        void declarePredefinedExchanges(String exchangeName) {

            final AMQP.Exchange.Declare exchange = new AMQP.Exchange.Declare.Builder().exchange(exchangeName)
                    .type("topic")
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addExchangeDeclaration(exchange).build()).isInstanceOf(
                    Topology.IllegalDeclarationException.class);
        }

        @Test
        void declarePseudoQueue() {
            final AMQP.Queue.Declare responseQueue = new AMQP.Queue.Declare.Builder().queue("amq.rabbitmq.reply-to")
                    .exclusive()
                    .durable(false)
                    .autoDelete()
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addQueueDeclaration(responseQueue).build()).isInstanceOf(
                    Topology.IllegalDeclarationException.class);
        }

        @Test
        void inconsistentExchangeDeclarations() {

            final AMQP.Exchange.Declare exchange1 = new AMQP.Exchange.Declare.Builder().exchange("myExchange")
                    .type("topic")
                    .build();
            final AMQP.Exchange.Declare exchange2 = new AMQP.Exchange.Declare.Builder().exchange("myExchange")
                    .type("direct")
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addExchangeDeclaration(exchange1)
                    .addExchangeDeclaration(exchange2)
                    .build()).isInstanceOf(Topology.IllegalDeclarationException.class);
        }

        @Test
        void inconsistentQueueDeclarations() {

            final AMQP.Queue.Declare queue1 = new AMQP.Queue.Declare.Builder().queue("myQueue")
                    .autoDelete(true)
                    .build();
            final AMQP.Queue.Declare queue2 = new AMQP.Queue.Declare.Builder().queue("myQueue")
                    .autoDelete(false)
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addQueueDeclaration(queue1)
                    .addQueueDeclaration(queue2)
                    .build()).isInstanceOf(Topology.IllegalDeclarationException.class);
        }


        @Test
        void bindNonExistingExchange() {
            final AMQP.Queue.Declare queue = new AMQP.Queue.Declare.Builder().queue("myQueue").build();
            final AMQP.Queue.Bind bind = new AMQP.Queue.Bind.Builder().queue("myQueue")
                    .exchange("nonExistingExchange")
                    .routingKey("my.routing.key")
                    .build();

            assertThatThrownBy(
                    () -> new Topology.Builder().addQueueDeclaration(queue).addQueueBinding(bind).build()).isInstanceOf(
                    Topology.IllegalDeclarationException.class);
        }

        @Test
        void bindNonExistingQueue() {
            final AMQP.Exchange.Declare exchange = new AMQP.Exchange.Declare.Builder().exchange("myExchange").build();
            final AMQP.Queue.Bind bind = new AMQP.Queue.Bind.Builder().queue("nonExistingQueue")
                    .exchange("myExchange")
                    .routingKey("my.routing.key")
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addExchangeDeclaration(exchange)
                    .addQueueBinding(bind)
                    .build()).isInstanceOf(Topology.IllegalDeclarationException.class);
        }

        @Test
        void bindPseudoQueue() {

            final AMQP.Exchange.Declare exchange = new AMQP.Exchange.Declare.Builder().exchange("myExchange").build();
            final AMQP.Queue.Bind bind = new AMQP.Queue.Bind.Builder().queue("amq.rabbitmq.reply-to")
                    .exchange("myExchange")
                    .routingKey("my.routing.key")
                    .build();

            assertThatThrownBy(() -> new Topology.Builder().addExchangeDeclaration(exchange)
                    .addQueueBinding(bind)
                    .build()).isInstanceOf(Topology.IllegalDeclarationException.class);
        }
    }

    @Nested
    class TestValidInstances {

        @Test
        void testEmptyTopology() {
            final Topology topology = new Topology(Collections.emptySet(), Collections.emptySet(),
                                                   Collections.emptySet());

            assertThat(topology.queueDeclarations()).isEmpty();
            assertThat(topology.exchangeDeclarations()).isEmpty();
            assertThat(topology.queueBindings()).isEmpty();
        }

        @Test
        void testValidTopology() {
            AMQP.Exchange.Declare exchange1 = new AMQP.Exchange.Declare.Builder().exchange("exchange1")
                    .type("topic")
                    .durable()
                    .build();
            AMQP.Exchange.Declare exchange2 = new AMQP.Exchange.Declare.Builder().exchange("exchange2")
                    .type("direct")
                    .durable()
                    .build();

            AMQP.Queue.Declare queue1 = new AMQP.Queue.Declare.Builder().queue("queue1").durable().build();
            AMQP.Queue.Declare queue2 = new AMQP.Queue.Declare.Builder().queue("queue2").durable().build();

            AMQP.Queue.Bind binding1 = new AMQP.Queue.Bind.Builder().queue("queue1")
                    .exchange("exchange1")
                    .routingKey("my.routing.key")
                    .build();
            AMQP.Queue.Bind binding2 = new AMQP.Queue.Bind.Builder().queue("queue2").exchange("exchange2").build();


            final Topology topology = new Topology.Builder().addQueueDeclaration(queue1)
                    .addQueueDeclarations(Set.of(queue2))
                    .addExchangeDeclaration(exchange1)
                    .addExchangeDeclarations(Set.of(exchange2))
                    .addQueueBinding(binding1)
                    .addQueueBindings(Set.of(binding2))
                    .build();

            assertThat(topology.exchangeDeclarations()).contains(exchange1, exchange2);
            assertThat(topology.queueDeclarations()).contains(queue1, queue2);
            assertThat(topology.queueBindings()).contains(binding1, binding2);
        }
    }

    @Nested
    class TestSubTopologies {
        @Test
        void testSubTopologyForQueues() {

            AMQP.Exchange.Declare exchange1 = new AMQP.Exchange.Declare.Builder().exchange("exchange1")
                    .type("topic")
                    .durable()
                    .build();
            AMQP.Exchange.Declare exchange2 = new AMQP.Exchange.Declare.Builder().exchange("exchange2")
                    .type("direct")
                    .durable()
                    .build();

            AMQP.Queue.Declare queue1 = new AMQP.Queue.Declare.Builder().queue("queue1").durable().build();
            AMQP.Queue.Declare queue2 = new AMQP.Queue.Declare.Builder().queue("queue2").durable().build();

            AMQP.Queue.Bind binding1 = new AMQP.Queue.Bind.Builder().queue("queue1")
                    .exchange("exchange1")
                    .routingKey("my.routing.key")
                    .build();
            AMQP.Queue.Bind binding2 = new AMQP.Queue.Bind.Builder().queue("queue2").exchange("exchange2").build();


            final Topology topology = new Topology.Builder().addQueueDeclaration(queue1)
                    .addQueueDeclarations(Set.of(queue2))
                    .addExchangeDeclaration(exchange1)
                    .addExchangeDeclarations(Set.of(exchange2))
                    .addQueueBinding(binding1)
                    .addQueueBindings(Set.of(binding2))
                    .build();

            final Topology subTopology = topology.subTopologyForQueue("queue1");

            assertThat(subTopology.queueDeclarations()).extracting(AMQP.Queue.Declare::getQueue)
                    .containsExactly("queue1");
            assertThat(subTopology.exchangeDeclarations()).extracting(AMQP.Exchange.Declare::getExchange)
                    .containsExactly("exchange1");
            assertThat(subTopology.queueBindings()).hasSize(1);
        }
    }
}
