# RabbitMQ-CDI-Bridge

An opinionated library that wraps the [RabbitMQ Java Client library](https://www.rabbitmq.com/java-client.html) and
integrates it with a CDI container.

<!-- TOC -->

* [RabbitMQ-CDI-Bridge](#rabbitmq-cdi-bridge)
    * [Features](#features)
        * [Events sent by the library](#events-sent-by-the-library)
        * [RPC Endpoints](#rpc-endpoints)
        * [Topology discovery](#topology-discovery)
        * [Automatic Serialization and Deserialization](#automatic-serialization-and-deserialization)
    * [Beans required to use the library](#beans-required-to-use-the-library)
    * [Beans provided by the library](#beans-provided-by-the-library)
        * [Extension points](#extension-points)
    * [Events consumed by the library](#events-consumed-by-the-library)

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

| Qualifier                  | Type                                                                                                    | Description                                       |
|----------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `@Exchange`                | `String`                                                                                                | the name of the exchange the message was sent to  |
| `@RoutingKey`              | `String`                                                                                                | the routing key the message was sent with         |
| `@Queue`                   | `String`                                                                                                | the name of the queue the message was received on |
| `@Default`                 | `BasicProperties`                                                                                       | the message properties                            |
| `@Header("<header-name>")` | `String`, primitives & wrapper types,<br/> `BigDecimal`, `Instant`,<br/> `Map<String,T>`, and `List<T>` | header values from `BasicProperties.getHeaders()` |

### RPC Endpoints

If an observer method has a return type (which is not forbidden by the CDI spec), it can be annotated as `@RpcEndpoint`
and the return value will automatically be serialized and sent as a response.

You can inject `Outgoing.Response.Builder` into the observer method to manipulate the metadata of the response, or you
can return your own `Response` object directly from the observer method.

### Topology discovery

Beans of type `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`, as well as the aggregate type `Topology`
are automatically discovered and consolidated. The necessary parts of the topology is
automatically declared to the broker whenever necessary, i.e. when messages are first published to an exchange or
consumed from a queue.

### Automatic Serialization and Deserialization

* CDI Beans that implement `SelectableMessageReader` or `SelectableMessageWriter` will be automatically be discovered
  and used.
    * All readers are considered for incoming messages.
    * Only the writers with the same type are considered for outgoing messages.
    * The library determines with the `canRead` and `canWrite` methods which readers/writers are applicable to a given
      incoming / outgoing message.
    * If more than one instance is applicable, the one with the highest priority wins.
* Trivial implementations (with the lowest possible priority) are provided that (de)serialize plain text messages (
  defined as having content type `text/plain`, optionally with a charset parameter) and pure binary messages (defined as
  having content type `application/octet-stream`)
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
    * The library validates (as early as it can) that all exchanges and queues that are necessary are declared via such
      CDI beans and that the declarations are non-contradictory.
    * The CDI container will fail to start due to a DeploymentException if a contradiction is detected.
    * Missing declarations will be detected at the latest when messages are to be sent/received.

## Beans provided by the library

| Scope and Qualifier        | Type                                 | Description                                                                                                    |
|----------------------------|--------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `@ApplicationScoped`       | `Connection`                         | The single connection created by the library                                                                   |
| `@ApplicationScoped`       | `BlockingPool<Channel>`              | A shared pool of channels for the same connection                                                              |
| `@Dependent @Consolidated` | `Topology`                           | The union of all beans of type `AMQP.Exchange.Declare`, `AMQP.Queue.Declare`, `AMQP.Queue.Bind`, or `Topology` |
| `@ApplicationScoped`       | `Publisher`                          | Provides methods to send messages to the RabbitMQ broker, optionally receiving a response                      |
| `@ApplicationScoped`       | `Consumers`                          | Provides methods to start and stop consuming from queues                                                       |
| `@Dependent`               | `Outgoing.Response.Builder<REQ,RES>` | An `Outgoing.Response.Builder` for the current request scope if an `Incoming.Request` was received             |
| `@Dependent`               | `Acknowledgement`                    | Provides methods to acknowledge or reject the message                                                          |

### Extension points

* The library provides a `@RequestScoped` bean of type `MessageReader<Object>` with qualifier `@Selected`
  that can be targeted by decorators to intercept the de-serialization of incoming messages independent of
  which `MessageReader` bean has been automatically selected for this purpose.
    * As an example, the library provides such a
      decorator - `GzipReader` - that decompresses incoming messages whose Content-Encoding is set to "gzip" or "
      deflate".
* The library provides a `@Dependent` scoped bean of type `MessageWriter<T>` for any `T` with
  qualifier `@Selected` that can be the targeted by decorators to intercept the serialization of outgoing messages
  independent of which `MessageWriter` bean has been automatically selected for this purpose.
    * As an example, the library provides such a
      decorator - `GzipWriter` - that compresses outgoing response messages with gzip or deflate if the request has
      indicated via "Accept-Encoding" header that it can support gzipped responses.

## Events consumed by the library

Asynchronous events of type `Outgoing<T>` will be consumed. They will be serialized and published to the broker.

* This is intended to be used with `Outgoing.Cast<T>` or maybe `Outgoing.Response<REQ,RES>`, i.e. fire-and-forget
  messages.
* If the event is an instance of `Outgoing.Request<T>`, then the response will be treated as other incoming messages
  and sent as an CDI event in its own request scope (in its own thread). Use `Publisher#send` if you want to handle
  the response more directly.
* The exchange to which the message is to be published must be known, i.e. there must be `AMQP.Exchange.Declare`
  and/or `Topology` beans providing a fitting declaration. Otherwise, an `IllegalArgumentException` will be thrown.
