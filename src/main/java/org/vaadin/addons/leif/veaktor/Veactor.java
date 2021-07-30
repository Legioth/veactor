package org.vaadin.addons.leif.veaktor;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.shared.Registration;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Helpers for subscribing to reactive updates from Vaadin views.
 */
public class Veactor {
    private static final AtomicBoolean pushChecked = new AtomicBoolean(false);

    private Veactor() {
        // Only static helpers here
    }

    /**
     * Gets or creates a scheduler for the given UI. The scheduler uses
     * {@link UI#access(com.vaadin.flow.server.Command)} to run tasks.
     * 
     * @param ui
     *            the UI for which to get a scheduler, not <code>null</code>
     * @return a scheduler based on the provided UI, not <code>null</code>
     */
    public static Scheduler getScheduler(UI ui) {
        Objects.requireNonNull(ui);

        // Only check the first time, assuming all UIs have the same
        // configuration
        if (pushChecked.compareAndSet(false, true)) {
            if (!ui.getPushConfiguration().getPushMode().isEnabled() && ui.getPollInterval() <= 0)
                getLogger().warn("Reactive functionality is used even though server push (or polling) is not enabled. "
                        + "You can add @Push to the application or run Veactor.disablePushCheck() to suppress this warning.");
        }

        Scheduler scheduler = ComponentUtil.getData(ui, Scheduler.class);
        if (scheduler == null) {
            scheduler = Schedulers.fromExecutor(task -> {
                try {
                    ui.access(task::run);
                } catch (UIDetachedException e) {
                    // Ignore detach exception since we will momentarily receive
                    // detach event which closes the whole subscription
                }
            });
            ComponentUtil.setData(ui, Scheduler.class, scheduler);
        }
        return scheduler;
    }

    /**
     * Registers and action that will be run whenever a scheduler is available
     * based on the attach status of the provided component. The scheduler uses
     * {@link UI#access(com.vaadin.flow.server.Command)} to run tasks for
     * updating the corresponding component tree.
     * <p>
     * The action will be run once the component is attached, and the returned
     * {@link Disposable} will be disposed when the component is no longer
     * attached. This is intended to be used for subscribing to updates to a
     * source and then closing that subscription when the associated component
     * is no longer used.
     * 
     * @param context
     *            the component that is used as a context for when the scheduler
     *            should be active, not <code>null</code>
     * @param subscriber
     *            a callback that can use the provided scheduler to set up a
     *            subscription. The returned disposable will be disposed when
     *            the scheduler is no longer valid. Not <code>null</code>.
     * @return a registration that can be used to stop listening for attach and
     *         detach events, and to close the subscription if it's currently
     *         active. Not <code>null</code>.
     */
    public static Registration withScheduler(Component context, Function<Scheduler, Disposable> subscriber) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(subscriber);
        return new Registration() {
            private final Registration attachRegistration = context.addAttachListener(event -> attach(event.getUI()));
            private final Registration detachRegistration = context.addDetachListener(event -> detach());
            private Disposable subscription;

            {
                context.getUI().ifPresent(this::attach);
            }

            private void attach(UI ui) {
                assert subscription == null;
                subscription = subscriber.apply(getScheduler(ui));
            }

            private void detach() {
                clearSubscription();
            }

            private void clearSubscription() {
                if (subscription != null) {
                    subscription.dispose();
                    subscription = null;
                }
            }

            @Override
            public void remove() {
                attachRegistration.remove();
                detachRegistration.remove();
                clearSubscription();
            }
        };
    }

    /**
     * Subscribes to the given flux to receive elements in the context of the
     * given component. The subscription will automatically be open when the
     * component is attached and closed when the component is detached.
     * 
     * @see Flux#subscribe(Consumer)
     * 
     * @param <T>
     *            the flux element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param flux
     *            the flux to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Flux<T> flux, Consumer<? super T> consumer) {
        return subscribe(context, flux, consumer, null, null);
    }

    /**
     * Subscribes to the given flux to receive elements and errors in the
     * context of the given component. The subscription will automatically be
     * open when the component is attached and closed when the component is
     * detached.
     * 
     * @see Flux#subscribe(Consumer, Consumer)
     * 
     * @param <T>
     *            the flux element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param flux
     *            the flux to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @param errorConsumer
     *            the error consumer, or <code>null</code>
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Flux<T> flux, Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer) {
        return subscribe(context, flux, consumer, errorConsumer, null);
    }

    /**
     * Subscribes to the given flux to receive elements, errors and react to
     * completion in the context of the given component. The subscription will
     * automatically be open when the component is attached and closed when the
     * component is detached.
     * 
     * @see Flux#subscribe(Consumer, Consumer, Runnable)
     * 
     * @param <T>
     *            the flux element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param flux
     *            the flux to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @param errorConsumer
     *            the error consumer, or <code>null</code>
     * @param completeConsumer
     *            the callback to run when the flux completes
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Flux<T> flux, Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        Objects.requireNonNull(flux);
        return withScheduler(context,
                scheduler -> flux.publishOn(scheduler).subscribe(consumer, errorConsumer, completeConsumer));
    }

    /**
     * Subscribes to the given mono to receive an element in the context of the
     * given component. The subscription will automatically be open when the
     * component is attached and closed when the component is detached.
     * 
     * @see Mono#subscribe(Consumer)
     * 
     * @param <T>
     *            the mono element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param mono
     *            the mono to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Mono<T> mono, Consumer<? super T> consumer) {
        return subscribe(context, mono, consumer, null, null);
    }

    /**
     * Subscribes to the given mono to receive elements and errors in the
     * context of the given component. The subscription will automatically be
     * open when the component is attached and closed when the component is
     * detached.
     * 
     * @see Mono#subscribe(Consumer, Consumer)
     * 
     * @param <T>
     *            the mono element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param mono
     *            the mono to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @param errorConsumer
     *            the error consumer, or <code>null</code>
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Mono<T> mono, Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer) {
        return subscribe(context, mono, consumer, errorConsumer, null);
    }

    /**
     * Subscribes to the given mono to receive elements, errors and react to
     * completion in the context of the given component. The subscription will
     * automatically be open when the component is attached and closed when the
     * component is detached.
     * 
     * @see Mono#subscribe(Consumer, Consumer, Runnable)
     * 
     * @param <T>
     *            the mono element type
     * @param context
     *            the component that controls whether the subscription is
     *            active, not <code>null</code>
     * @param mono
     *            the mono to subscribe to, not <code>null</code>
     * @param consumer
     *            the element consumer, not <code>null</code>
     * @param errorConsumer
     *            the error consumer, or <code>null</code>
     * @param completeConsumer
     *            the callback to run when the flux completes
     * @return a registration that can be used to permanently close the
     *         subscription, not <code>null</code>
     */
    public static <T> Registration subscribe(Component context, Mono<T> mono, Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        Objects.requireNonNull(mono);
        return withScheduler(context,
                scheduler -> mono.publishOn(scheduler).subscribe(consumer, errorConsumer, completeConsumer));
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(Veactor.class);
    }

    /**
     * Disables the initial check for server push is and the warning that is
     * logged in case it's not enabled.
     */
    public static void disablePushCheck() {
        // "Disable" by marking it as already performed
        pushChecked.set(true);
    }

}
