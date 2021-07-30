# Veactor

Veactor is a set of helpers to use Project Reactor and Vaadin together.

The main functionality is focused on updating parts of a Vaadin UI when new elements are published to a `Flux` or `Mono`.
This is done using an appropriate `subscribe` method in the `Veactor` class.
Each of those methods requires a component instance that is used to control the life cycle of the subscription - the subscription is started when the component is attached (happens immediately if it's already attached when `subscribe` is called) and the subscription is closed when the component is detached again.

```java
@Push
@Route("hello")
public class HelloWorldView extends VerticalLayout {
  public HelloWorldView() {
    Flux<Long> flux = Flux.interval(Duration.ofSeconds(1)).take(10);

    Span message = new Span("Waiting for updates...");

    Veactor.subscribe(this, flux, value -> message.setText("Received value " + value));

    add(message);
  }
}
```

## Development instructions

### Deployment

Starting the test/demo server:
```
mvn
```

This deploys the demo at http://localhost:8080

## Publishing to Vaadin Directory

Create the zip package for [Vaadin Directory](https://vaadin.com/directory/):

```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions 
mvn clean install -Pdirectory
```
