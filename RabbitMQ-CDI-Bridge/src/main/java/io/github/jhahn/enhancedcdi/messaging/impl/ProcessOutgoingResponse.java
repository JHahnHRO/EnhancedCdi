package io.github.jhahn.enhancedcdi.messaging.impl;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import io.github.jhahn.enhancedcdi.messaging.processing.Outgoing;
import io.github.jhahn.enhancedcdi.messaging.processing.ProcessOutgoing;

class ProcessOutgoingResponse<T> extends ProcessOutgoingImpl<T> implements ProcessOutgoing.Response<T> {
    private final Delivery request;

    ProcessOutgoingResponse(Outgoing<T> outgoing, Delivery request) {
        super(outgoing,
              new ResponsePropertiesBuilderImpl(outgoing.properties(), request.getProperties().getCorrelationId()));
        this.exchange = "";
        this.request = request;
        this.routingKey = request.getProperties().getReplyTo();
    }

    @Override
    public Delivery request() {
        return request;
    }

    /**
     * @return The empty string, i.e. the name of the built-in exchange for RPC responses
     */
    @Override
    public String exchange() {
        return "";
    }

    /**
     * @return The routing key of the response, i.e. the value of the
     * {@link BasicProperties#getReplyTo() 'replyTo' property} in the request.
     */
    @Override
    public String routingKey() {
        return request().getProperties().getReplyTo();
    }

    private static class ResponsePropertiesBuilderImpl extends PropertiesBuilderImpl {
        ResponsePropertiesBuilderImpl(BasicProperties initialProperties, String correlationId) {
            // initialize with given values
            super.of(initialProperties);
            super.setCorrelationId(correlationId);
        }

        @Override
        public PropertiesBuilderImpl setCorrelationId(String correlationId) {
            throw new UnsupportedOperationException(
                    "'correlationId' property of RPC response message cannot be set manually, "
                    + "because it must be equal the correlationId of the request.");
        }
    }
}
