package com.howard.simpinautounlock.hook;

import java.util.function.Supplier;

final class RetainedRegistrationGate<T> {
    private T instance;
    private boolean registered;

    synchronized T initialize(Supplier<? extends T> factory, Registrar<? super T> registrar) {
        if (instance == null) {
            instance = factory.get();
        }
        if (!registered) {
            registrar.register(instance);
            registered = true;
        }
        return instance;
    }

    synchronized T current() {
        return instance;
    }

    @FunctionalInterface
    interface Registrar<T> {
        void register(T instance);
    }
}
