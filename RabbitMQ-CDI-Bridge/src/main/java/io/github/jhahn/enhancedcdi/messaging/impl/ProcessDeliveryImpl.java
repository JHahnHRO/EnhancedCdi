package io.github.jhahn.enhancedcdi.messaging.impl;

abstract class ProcessDeliveryImpl {
    protected String exchange;
    protected String routingKey;
    protected boolean vetoed;

    protected ProcessDeliveryImpl(String exchange, String routingKey) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.vetoed = false;
    }

    public String exchange() {
        return exchange;
    }

    public String routingKey() {
        return routingKey;
    }

    public void veto() {
        this.vetoed = true;
    }
}
