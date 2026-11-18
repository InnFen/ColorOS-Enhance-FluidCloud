package com.howard.simpinautounlock.ui;

import java.util.Objects;

public final class SlotCallbackGate {
    private long nextOwnerGeneration;
    private long activeOwnerGeneration;

    public Owner replaceOwner() {
        activeOwnerGeneration = ++nextOwnerGeneration;
        return new Owner(this, activeOwnerGeneration);
    }

    private boolean isActive(long ownerGeneration) {
        return activeOwnerGeneration == ownerGeneration;
    }

    private void close(long ownerGeneration) {
        if (isActive(ownerGeneration)) {
            activeOwnerGeneration = 0;
        }
    }

    public static final class Owner {
        private final SlotCallbackGate gate;
        private final long ownerGeneration;
        private final long[] actionGenerations = new long[2];
        private final long[] queryGenerations = new long[2];

        private Owner(SlotCallbackGate gate, long ownerGeneration) {
            this.gate = gate;
            this.ownerGeneration = ownerGeneration;
        }

        public ActionToken beginAction(int slotId) {
            queryGenerations[slotId]++;
            return new ActionToken(this, slotId, ++actionGenerations[slotId]);
        }

        public QueryToken beginQuery(int slotId) {
            return new QueryToken(this, slotId, ++queryGenerations[slotId]);
        }

        public boolean runIfCurrent(ActionToken token, Runnable callback) {
            if (!isCurrent(token)) {
                return false;
            }
            Objects.requireNonNull(callback).run();
            return true;
        }

        public boolean runIfCurrent(QueryToken token, Runnable callback) {
            if (!isCurrent(token)) {
                return false;
            }
            Objects.requireNonNull(callback).run();
            return true;
        }

        public boolean runIfCurrent(
                ActionToken first,
                ActionToken second,
                Runnable callback) {
            if (!isCurrent(first) || !isCurrent(second)) {
                return false;
            }
            Objects.requireNonNull(callback).run();
            return true;
        }

        public void close() {
            gate.close(ownerGeneration);
        }

        private boolean isCurrent(ActionToken token) {
            return token != null
                    && token.owner == this
                    && gate.isActive(ownerGeneration)
                    && actionGenerations[token.slotId] == token.generation;
        }

        private boolean isCurrent(QueryToken token) {
            return token != null
                    && token.owner == this
                    && gate.isActive(ownerGeneration)
                    && queryGenerations[token.slotId] == token.generation;
        }
    }

    public static final class ActionToken {
        private final Owner owner;
        private final int slotId;
        private final long generation;

        private ActionToken(Owner owner, int slotId, long generation) {
            this.owner = owner;
            this.slotId = slotId;
            this.generation = generation;
        }
    }

    public static final class QueryToken {
        private final Owner owner;
        private final int slotId;
        private final long generation;

        private QueryToken(Owner owner, int slotId, long generation) {
            this.owner = owner;
            this.slotId = slotId;
            this.generation = generation;
        }
    }
}
