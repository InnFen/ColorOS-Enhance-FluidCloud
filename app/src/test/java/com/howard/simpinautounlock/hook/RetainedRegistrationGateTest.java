package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class RetainedRegistrationGateTest {
    @Test
    public void registrationFailureRetainsGraphAndCoordinator() {
        RetainedRegistrationGate<Graph> gate = new RetainedRegistrationGate<>();
        CountingFactory factory = new CountingFactory();

        try {
            gate.initialize(factory::create, ignored -> {
                throw new IllegalStateException("registration failed");
            });
            fail("registration failure must propagate");
        } catch (IllegalStateException expected) {
            // Expected boundary failure.
        }

        Graph retained = gate.current();
        assertSame(factory.created, retained);
        assertSame(factory.created.coordinator, retained.coordinator);
        assertEquals(1, factory.createCount);
    }

    @Test
    public void repeatedInitializationRetriesSameReceiverWithoutDuplicateSuccess() {
        RetainedRegistrationGate<Graph> gate = new RetainedRegistrationGate<>();
        CountingFactory factory = new CountingFactory();
        RecordingRegistrar registrar = new RecordingRegistrar();

        try {
            gate.initialize(factory::create, registrar::register);
            fail("first registration must fail");
        } catch (IllegalStateException expected) {
            // Expected first-attempt failure.
        }
        Graph second = gate.initialize(factory::create, registrar::register);
        Graph third = gate.initialize(factory::create, registrar::register);

        assertSame(factory.created, second);
        assertSame(second, third);
        assertSame(second.coordinator, third.coordinator);
        assertEquals(1, factory.createCount);
        assertEquals(2, registrar.attemptCount);
        assertEquals(1, registrar.successCount);
        assertSame(registrar.receivers.get(0), registrar.receivers.get(1));
        assertSame(second.receiver, registrar.receivers.get(1));
    }

    private static final class Graph {
        final Object coordinator = new Object();
        final Object receiver = new Object();
    }

    private static final class CountingFactory {
        int createCount;
        Graph created;

        Graph create() {
            createCount++;
            created = new Graph();
            return created;
        }
    }

    private static final class RecordingRegistrar {
        final List<Object> receivers = new ArrayList<>();
        int attemptCount;
        int successCount;

        void register(Graph graph) {
            attemptCount++;
            receivers.add(graph.receiver);
            if (attemptCount == 1) {
                throw new IllegalStateException("registration failed");
            }
            successCount++;
        }
    }
}
