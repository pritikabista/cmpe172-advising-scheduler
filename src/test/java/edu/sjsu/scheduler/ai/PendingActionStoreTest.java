package edu.sjsu.scheduler.ai;

import edu.sjsu.scheduler.ai.agent.PendingAction;
import edu.sjsu.scheduler.ai.agent.PendingActionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActionStoreTest {

    @Test
    void confirmationOnlyCountsInALaterTurn() {
        PendingAction a = new PendingAction(PendingAction.Type.BOOK, 2, null, "Book slot 2", 3, LocalDateTime.now());
        assertThat(PendingActionStore.userHasRepliedSince(a, 3)).isFalse();
        assertThat(PendingActionStore.userHasRepliedSince(a, 4)).isTrue();
    }

    @Test
    void turnsCountPerConversation() {
        PendingActionStore store = new PendingActionStore(Clock.systemDefaultZone());
        assertThat(store.nextTurn("a")).isEqualTo(1);
        assertThat(store.nextTurn("a")).isEqualTo(2);
        assertThat(store.nextTurn("b")).isEqualTo(1);
    }

    @Test
    void oldProposalsExpire() {
        PendingActionStore store = new PendingActionStore(Clock.systemDefaultZone());
        store.propose("a", new PendingAction(PendingAction.Type.BOOK, 2, null, "x", 1,
                LocalDateTime.now().minusMinutes(30)));
        assertThat(store.peek("a")).isEmpty();
    }
}
