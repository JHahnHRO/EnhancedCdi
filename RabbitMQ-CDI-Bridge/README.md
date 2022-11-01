# RabbitMQ-CDI-Bridge

An opinionated library that wraps the [RabbitMQ Java Client library](https://www.rabbitmq.com/java-client.html) and
integrates it with a CDI container.

<!-- TOC -->

* [RabbitMQ-CDI-Bridge](#rabbitmq-cdi-bridge)
    * [Features](#features)
        * [Events sent by the library](#events-sent-by-the-library)
        * [Topology discovery](#topology-discovery)
        * [Automatic Serialization and Deserialization](#automatic-serialization-and-deserialization)
    * [Beans required to use the library](#beans-required-to-use-the-library)
    * [Beans provided by the library](#beans-provided-by-the-library)
    * [Events consumed by the library](#events-consumed-by-the-library)
        * [Synchronous Events](#synchronous-events)
        * [Asynchronous Events](#asynchronous-events)

<!-- TOC -->

## Features

### Events sent by the library

Incoming deliveries will be automatically deserialized and fired as CDI Events inside their own
RequestScope.

* The event is fired synchronously and will have
  qualifiers `@Incoming` `@FromQueue(<queue-name>)` `@FromExchange(<exchange-name>)` `@WithRoutingKey(<routing-key>)`  `@Redelivered(<true/false>)`
    * The event's type is variable however and is determined
      by [automatic deserialization](#automatic-serialization-and-deserialization).
* Message metadata can be injected into observer methods for these events. Available for injection are:

| Type                                                                                          | Qualifier                  | Description                                       |
|-----------------------------------------------------------------------------------------------|----------------------------|---------------------------------------------------|
| `String`                                                                                      | `@Exchange`                | the name of the exchange the message was sent to  |
| `String`                                                                                      | `@RoutingKey`              | the routing key the message was sent with         |
| `String`                                                                                      | `@Queue`                   | the name of the queue the message was received on |
| `BasicProperties`                                                                             | `@Default`                 | the message properties                            |
| `String`, primitives & wrapper types, `BigDecimal`, `Instant`, `Map<String,T>`, and `List<T>` | `@Header("<header-name>")` | header values from `BasicProperties.getHeaders()` |

### RPC Endpoints

If an observer method has a return type (which is not forbidden by the CDI spec), it can be annotated as `@RpcEndpoint`
and the return value will automatically be serialized and sent as a response.

You can inject `OutgoingMessageBuilder` into the observer method to manipulate the metadata of the response.

### Topology discovery

Beans of type `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`, as well as the aggregate type `Topology`
are automatically discovered and consolidated. The necessary parts of the topology is
automatically declared to the broker whenever necessary, i.e. when messages are first published to an exchange or
consumed from a queue.

### Automatic Serialization and Deserialization

* CDI Beans that implement `MessageReader` or `MessageWriter` will be automatically be discovered and used.
* Trivial `MessageReader` and `MessageWriter` implementations (with the lowest possible priority) are provided
  that (de)serialize plain text messages (defined as having content type `text/plain`, optionally with a charset
  parameter) and pure binary messages (defined as having content type `application/octet-stream`)
* The optional [`protobuf` module](./protobuf) provides ready-to-use implementations for (de)serializing google's
  protobuf format.
* The optional [`jsonb` module](./jsonb) provides ready-to-use implementations for (de)serialization to and from
  Json using the JSON-B API.

## Beans required to use the library

* A bean of type `Configuration` with qualifier `@Default` that defines how the connection to the broker is to be
  established.
    * Only Auto-Recovering Connections (and therefore Channels) are supported. Auto-Recovery will be enabled
      automatically and a warning logged, if the `ConnectionFactory` bean does not have it enabled.
* At least some beans of types `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`, and/or `Topology` to
  define what exchanges and queue are to be used.

## Beans provided by the library

| Type                              | Scope and Qualifier          | Description                                                                                                                    |
|-----------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `Connection`                      | `@ApplicationScoped`         | the single connection created by the library                                                                                   |
| `BlockingPool<Channel>`           | `@ApplicationScoped`         | a shared pool of channels for the same connection                                                                              |
| `Topology`                        | `@Dependent` `@Consolidated` | the union of all discovered beans of types `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`, and/or `Topology` |
| `Infrastructure`                  | `@ApplicationScoped`         | Provides methods to declare exchanges and queues manually.                                                                     |
| `Publisher`                       | `@ApplicationScoped`         | send messages to the RabbitMQ broker, optionally receiving a response                                                          |
| `OutgoingMessageBuilder<REQ,RES>` | `@Dependent`                 | an instance of `OutgoingMessageBuilder` if the request scope is associated with an incoming request. `null` if not.            |

## Events consumed by the library

### Synchronous Events

* `StartConsuming` starts consuming messages from a queue named in the event.
    * The queue must be known, i.e. there must be `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`,
      and/or `Topology` beans providing fitting declarations. Otherwise, an `IllegalArgumentException` will be thrown.
* `StopConsuming` stops a previously started consumer (if it exists) from a queue named in the event. If no consumer was
  started before, the event is ignored.

### Asynchronous Events

* `Outgoing<T>` serializes and publishes the message to the broker.
    * This is intended to be used with `Outgoing.Cast<T>` or maybe `Outgoing.Response<REQ,RES>`, i.e. i.e.
      fire-and-forget messages.
    * If the event is an instance of `Outgoing.Request<T>`, then the response will be treated as other incoming messages
      and sent as an CDI event in its own request scope (in its own thread). Use `Publisher#send` if you want to handle
      the response more directly.
    * The exchange to which the message is to be published must be known, i.e. there must be `AMQP.Exchange.Declare`
      and/or `Topology` beans providing a fitting declaration. Otherwise, an `IllegalArgumentException` will be thrown.
