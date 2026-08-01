package com.micatechnologies.minecraft.sum.omceapi;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One page of the {@code /getEvents} change feed — the mechanism by which balance changes made
 * <i>outside</i> Minecraft reach the game.
 *
 * <p>A pull feed rather than webhooks, because a Minecraft server is usually behind NAT with no
 * inbound reachability.
 */
public final class OmceEventPage {

    private final List<Event> events;
    @Nullable private final String nextCursor;
    private final boolean hasMore;

    public OmceEventPage(@Nullable List<Event> events, @Nullable String nextCursor, boolean hasMore) {
        this.events = events == null ? Collections.emptyList() : events;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<Event> getEvents() {
        return events;
    }

    /** Cursor to pass on the next poll. Null leaves the caller's stored cursor unchanged. */
    @Nullable
    public String getNextCursor() {
        return nextCursor;
    }

    /** True when more events are immediately available and the caller should poll again at once. */
    public boolean hasMore() {
        return hasMore;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    /** A single change-feed entry. */
    public static final class Event {

        public static final String KIND_BALANCE_CHANGED = "balance_changed";
        public static final String KIND_ACCOUNT_STATUS_CHANGED = "account_status_changed";
        public static final String KIND_ACCOUNT_LINKED = "account_linked";

        private final String cursor;
        private final String kind;
        @Nullable private final UUID playerUuid;
        @Nullable private final OmceBalance balance;
        @Nullable private final String reason;

        public Event(String cursor, String kind, @Nullable UUID playerUuid,
            @Nullable OmceBalance balance, @Nullable String reason) {
            this.cursor = cursor == null ? "" : cursor;
            this.kind = kind == null ? "" : kind;
            this.playerUuid = playerUuid;
            this.balance = balance;
            this.reason = reason;
        }

        public String getCursor() {
            return cursor;
        }

        public String getKind() {
            return kind;
        }

        @Nullable
        public UUID getPlayerUuid() {
            return playerUuid;
        }

        /** The new authoritative balance, present on {@link #KIND_BALANCE_CHANGED}. */
        @Nullable
        public OmceBalance getBalance() {
            return balance;
        }

        /** Human-readable cause, e.g. a payout description. May be shown to the player. */
        @Nullable
        public String getReason() {
            return reason;
        }

        public boolean isBalanceChange() {
            return KIND_BALANCE_CHANGED.equals(kind) && balance != null;
        }

        @Override
        public String toString() {
            return "Event{" + kind + " " + playerUuid + " @" + cursor + "}";
        }
    }
}
