package org.vaadin.addons.leif.veaktor;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.shared.ui.Transport;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Route("")
@Push(transport = Transport.LONG_POLLING)
public class TestView extends VerticalLayout {
    @FunctionalInterface
    private interface SubscriptionStarter<T> {
        Registration start(Component context, T source, Consumer<Object> consumer, Consumer<Throwable> errorHandler,
                Runnable completeHandler);
    }

    private static final DateTimeFormatter ISO_TIME_TO_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VerticalLayout log = new VerticalLayout();

    private final Collection<Registration> registrations = new HashSet<>();

    private final Button stopButton = new Button("Stop all subscriptions", event -> stopAll());
    Button clearButton = new Button("Clear log", event -> log.removeAll());

    private final HorizontalLayout startButtons = new HorizontalLayout();

    private int nextSubscriptionId = 0;
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    public TestView() {
        addFluxSubscriptionButton("Start count", () -> Flux.interval(ONE_SECOND).take(10));
        addMonoSubscriptionButton("Start delay", () -> Mono.delay(ONE_SECOND));
        addFluxSubscriptionButton("Start count + error",
                () -> Flux.concat(Flux.interval(ONE_SECOND).take(2), Flux.error(RuntimeException::new)));
        addMonoSubscriptionButton("Start delay + error",
                () -> Mono.error(RuntimeException::new).delaySubscription(ONE_SECOND));
        addMonoSubscriptionButton("Fail immediately", () -> Mono.error(RuntimeException::new));
        addFluxSubscriptionButton("Start empty Flux", () -> Flux.empty());
        addMonoSubscriptionButton("Start empty Flux", () -> Mono.empty());

        stopButton.setDisableOnClick(true);
        stopButton.setEnabled(false);

        clearButton.setDisableOnClick(true);

        log.setSpacing(false);

        add(startButtons, stopButton, clearButton, log);
    }

    private void addFluxSubscriptionButton(String name, Supplier<Flux<?>> supplier) {
        startButtons.add(new Button(name, event -> startSubscription(supplier.get(), Veactor::subscribe)));
    }

    private void addMonoSubscriptionButton(String name, Supplier<Mono<?>> supplier) {
        startButtons.add(new Button(name, event -> startSubscription(supplier.get(), Veactor::subscribe)));
    }

    private <T> void startSubscription(T target, SubscriptionStarter<T> subscriber) {
        int id = nextSubscriptionId++;
        Registration[] holder = new Registration[1];
        holder[0] = subscriber.start(this, target, value -> {
            log("Subscription " + id + " got " + value);
        }, error -> {
            stopRegistration(holder[0]);
            log("Subscription " + id + " failed with " + error);
        }, () -> {
            stopRegistration(holder[0]);
            log("Subscription " + id + " ended");
        });
        addRegistration(holder[0]);
        log("Subscription " + id + " started");
    }

    private void stopRegistration(Registration registration) {
        if (registration == null) {
            return;
        }
        registration.remove();
        registrations.remove(registration);
        if (registrations.isEmpty()) {
            stopButton.setEnabled(false);
        }
    }

    private void stopAll() {
        registrations.forEach(Registration::remove);
        registrations.clear();
    }

    public void addRegistration(Registration registration) {
        registrations.add(registration);
        stopButton.setEnabled(true);
    }

    private void log(String message) {
        String time = LocalTime.now().format(ISO_TIME_TO_SECONDS);
        log.add(new Span(time + " " + message));
        clearButton.setEnabled(true);
    }
}
