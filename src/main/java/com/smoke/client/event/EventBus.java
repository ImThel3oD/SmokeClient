package com.smoke.client.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class EventBus {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<Subscriber> ORDERING = Comparator
            .comparingInt(Subscriber::priority)
            .reversed()
            .thenComparingLong(Subscriber::registrationOrder);

    private final AtomicLong registrationCounter = new AtomicLong();
    private final Map<Class<? extends Event>, CopyOnWriteArrayList<Subscriber>> subscribersByType = new ConcurrentHashMap<>();
    private final Map<Object, List<Subscriber>> subscribersByOwner = new ConcurrentHashMap<>();

    public void register(Object owner) {
        Objects.requireNonNull(owner, "owner");
        unregister(owner);

        List<Subscriber> discovered = discover(owner);
        if (discovered.isEmpty()) {
            return;
        }

        subscribersByOwner.put(owner, discovered);
        for (Subscriber subscriber : discovered) {
            CopyOnWriteArrayList<Subscriber> subscribers = subscribersByType.computeIfAbsent(
                    subscriber.eventType(),
                    ignored -> new CopyOnWriteArrayList<>()
            );
            subscribers.add(subscriber);
            subscribers.sort(ORDERING);
        }
    }

    public void unregister(Object owner) {
        Objects.requireNonNull(owner, "owner");
        List<Subscriber> subscribers = subscribersByOwner.remove(owner);
        if (subscribers == null) {
            return;
        }

        for (Subscriber subscriber : subscribers) {
            CopyOnWriteArrayList<Subscriber> typedSubscribers = subscribersByType.get(subscriber.eventType());
            if (typedSubscribers == null) {
                continue;
            }

            typedSubscribers.remove(subscriber);
            if (typedSubscribers.isEmpty()) {
                subscribersByType.remove(subscriber.eventType());
            }
        }
    }

    public <T extends Event> T post(T event) {
        Objects.requireNonNull(event, "event");
        CopyOnWriteArrayList<Subscriber> subscribers = subscribersByType.get(event.getClass());
        if (subscribers == null || subscribers.isEmpty()) {
            return event;
        }

        for (Subscriber subscriber : subscribers) {
            subscriber.invoke(event);
            if (event instanceof CancellableEvent cancellable && cancellable.isPropagationStopped()) {
                break;
            }
        }
        return event;
    }

    private List<Subscriber> discover(Object owner) {
        List<Subscriber> discovered = new ArrayList<>();
        Class<?> type = owner.getClass();

        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                Subscribe subscribe = method.getAnnotation(Subscribe.class);
                if (subscribe == null) {
                    continue;
                }

                if (method.getParameterCount() != 1 || method.getReturnType() != Void.TYPE) {
                    throw new IllegalArgumentException("@Subscribe methods must have exactly one Event parameter and return void: " + method);
                }

                Class<?> parameterType = method.getParameterTypes()[0];
                if (!Event.class.isAssignableFrom(parameterType)) {
                    throw new IllegalArgumentException("@Subscribe parameter must implement Event: " + method);
                }

                method.setAccessible(true);
                try {
                    MethodHandle handle = LOOKUP.unreflect(method).bindTo(owner);
                    discovered.add(new Subscriber(
                            owner,
                            cast(parameterType),
                            handle,
                            subscribe.priority(),
                            registrationCounter.getAndIncrement()
                    ));
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException("Failed to register subscriber " + method, exception);
                }
            }
            type = type.getSuperclass();
        }

        return discovered;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> cast(Class<?> type) {
        return (Class<? extends Event>) type;
    }

    private record Subscriber(
            Object owner,
            Class<? extends Event> eventType,
            MethodHandle handle,
            int priority,
            long registrationOrder
    ) {
        private void invoke(Event event) {
            try {
                handle.invoke(event);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Error error) {
                throw error;
            } catch (Throwable throwable) {
                throw new RuntimeException("Subscriber invocation failed for " + owner.getClass().getName(), throwable);
            }
        }
    }
}
