package org.vaadin.addons.leif.veaktor;

import java.time.Duration;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;

import reactor.core.publisher.Flux;

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
