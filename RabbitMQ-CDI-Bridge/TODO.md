# TODO

* rewrite `ChannelProducerTest`, extract the parts that only test `LazyBlockingPool`
* find a more flexible way for logging incoming and outgoing messages
    * lifecycle events? OnDelivery, OnDeserialization, ... ?
    * interface with specific log-callbacks?
    * interface with general-purpose `log(level,Message<T>,String,Throwable)` method?
    * SLF4J + Markers?
* write tests for `IncomingMessageHandler` and `OutgoingMessageHandler`
* write tests for `Incoming` and `Outgoing`
* increase unit test coverage
* write proper integration test with Cucumber & RabbitMQ Docker Container

