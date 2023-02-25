package io.github.jhahnhro.enhancedcdi.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Set;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.github.jhahnhro.enhancedcdi.messaging.Topology;
import io.github.jhahnhro.enhancedcdi.pooled.BlockingPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfrastructureTest {

    @Mock
    Channel channel;
    @Mock
    BlockingPool<Channel> channelPool;
    Topology topology;
    private Infrastructure infrastructure;

    @BeforeEach
    void setUp() throws InterruptedException {
        this.topology = createTopology();

        this.infrastructure = new Infrastructure();
        this.infrastructure.channelPool = this.channelPool;
        this.infrastructure.consolidatedTopology = this.topology;

        prepareChannelPool();
    }

    private Topology createTopology() {

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

        return new Topology.Builder().addQueueDeclaration(queue1)
                .addQueueDeclarations(Set.of(queue2))
                .addExchangeDeclaration(exchange1)
                .addExchangeDeclarations(Set.of(exchange2))
                .addQueueBinding(binding1)
                .addQueueBindings(Set.of(binding2))
                .build();
    }

    private void prepareChannelPool() throws InterruptedException {
        lenient().when(channelPool.withItem(any(BlockingPool.ThrowingFunction.class))).then(invocationOnMock -> {
            final BlockingPool.ThrowingFunction<Channel, ?, ?> function = invocationOnMock.getArgument(0);
            return function.apply(this.channel);
        });
        lenient().doCallRealMethod().when(channelPool).withItem(any(BlockingPool.ThrowingConsumer.class));
    }

    @Test
    void testSetUpTopology() throws IOException, InterruptedException {
        infrastructure.setUpTopology(this.topology);

        verifyExchangesWereDeclared("exchange1", "exchange2");
        verifyQueuesWereDeclared("queue1", "queue2");
        verifyQueueBinding("queue1", "exchange1");
        verifyQueueBinding("queue2", "exchange2");

        verifyNoMoreInteractions(channel);
    }

    @Test
    void testSetUpForQueue() throws IOException, InterruptedException {
        infrastructure.setUpForQueue("queue1");

        verifyExchangesWereDeclared("exchange1");
        verifyQueuesWereDeclared("queue1");
        verifyQueueBinding("queue1", "exchange1");

        verifyNoMoreInteractions(channel);
    }

    @Test
    void givenUnknownQueue_whenSetUpQueue_thenThrowIAE() {
        assertThatIllegalArgumentException().isThrownBy(() -> infrastructure.setUpForQueue("unknown-queue"));
    }

    @Test
    void testSetUpForExchange() throws IOException, InterruptedException {
        infrastructure.setUpForExchange("exchange1");

        verifyExchangesWereDeclared("exchange1");

        verifyNoMoreInteractions(channel);
    }

    @Test
    void givenUnknownExchange_whenSetupExchange_thenThrowIAE() {
        assertThatIllegalArgumentException().isThrownBy(() -> infrastructure.setUpForExchange("unknown-exchange"));
    }

    private void verifyQueueBinding(String queue, String exchange) throws IOException {
        verify(channel).queueBind(eq(queue), eq(exchange), any(), any());
    }

    private void verifyQueuesWereDeclared(final String... queues) throws IOException {
        ArgumentCaptor<String> queueNames = ArgumentCaptor.forClass(String.class);
        verify(channel, times(queues.length)).queueDeclare(queueNames.capture(), anyBoolean(), anyBoolean(),
                                                           anyBoolean(), any());
        assertThat(queueNames.getAllValues()).containsExactlyInAnyOrder(queues);
    }

    private void verifyExchangesWereDeclared(final String... exchanges) throws IOException {
        ArgumentCaptor<String> exchangeNames = ArgumentCaptor.forClass(String.class);
        verify(channel, times(exchanges.length)).exchangeDeclare(exchangeNames.capture(), any(String.class),
                                                                 anyBoolean(), anyBoolean(), any());
        assertThat(exchangeNames.getAllValues()).containsExactlyInAnyOrder(exchanges);
    }
}