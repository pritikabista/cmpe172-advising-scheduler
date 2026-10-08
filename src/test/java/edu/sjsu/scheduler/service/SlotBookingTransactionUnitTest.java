package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.exception.SlotAlreadyBookedException;
import edu.sjsu.scheduler.model.SlotLock;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the rules inside one booking transaction (repositories mocked). */
class SlotBookingTransactionUnitTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    private SlotRepository slotRepo;
    private AppointmentRepository apptRepo;
    private SlotBookingTransaction tx;

    @BeforeEach
    void setUp() {
        slotRepo = mock(SlotRepository.class);
        apptRepo = mock(AppointmentRepository.class);
        tx = new SlotBookingTransaction(slotRepo, apptRepo, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private void slotExists(long slotId, LocalDateTime start) {
        when(slotRepo.lockById(slotId)).thenReturn(Optional.of(new SlotLock(slotId, 1L, 2L, start, 0)));
    }

    @Test
    void happyPath_locksThenInsertsThenBumpsVersion() {
        slotExists(5L, NOW.plusDays(1));
        when(apptRepo.hasActiveBooking(5L)).thenReturn(false);
        when(apptRepo.insertBooked(3L, 5L, 2L, "note")).thenReturn(77L);
        when(slotRepo.bumpVersion(5L, 0)).thenReturn(true);

        assertThat(tx.book(3L, 5L, "note")).isEqualTo(77L);

        var order = inOrder(slotRepo, apptRepo);
        order.verify(slotRepo).lockById(5L);
        order.verify(apptRepo).hasActiveBooking(5L);
        order.verify(apptRepo).insertBooked(3L, 5L, 2L, "note");
        order.verify(slotRepo).bumpVersion(5L, 0);
    }

    @Test
    void slotNotFound_is404() {
        when(slotRepo.lockById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tx.book(3L, 5L, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void pastSlot_is400() {
        slotExists(5L, NOW.minusMinutes(1));
        assertThatThrownBy(() -> tx.book(3L, 5L, null)).isInstanceOf(InvalidRequestException.class);
        verify(apptRepo, never()).insertBooked(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void alreadyBooked_is409_andNothingInserted() {
        slotExists(5L, NOW.plusDays(1));
        when(apptRepo.hasActiveBooking(5L)).thenReturn(true);
        assertThatThrownBy(() -> tx.book(3L, 5L, null)).isInstanceOf(SlotAlreadyBookedException.class);
        verify(apptRepo, never()).insertBooked(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void uniqueIndexViolation_becomes409() {
        slotExists(5L, NOW.plusDays(1));
        when(apptRepo.hasActiveBooking(5L)).thenReturn(false);
        when(apptRepo.insertBooked(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(new DuplicateKeyException("uq_one_active_booking_per_slot"));
        assertThatThrownBy(() -> tx.book(3L, 5L, null)).isInstanceOf(SlotAlreadyBookedException.class);
    }

    @Test
    void versionMismatch_is409() {
        slotExists(5L, NOW.plusDays(1));
        when(apptRepo.hasActiveBooking(5L)).thenReturn(false);
        when(apptRepo.insertBooked(anyLong(), anyLong(), anyLong(), any())).thenReturn(1L);
        when(slotRepo.bumpVersion(5L, 0)).thenReturn(false);
        assertThatThrownBy(() -> tx.book(3L, 5L, null)).isInstanceOf(ConflictException.class);
    }
}
