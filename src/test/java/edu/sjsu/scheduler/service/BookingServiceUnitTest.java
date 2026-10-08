package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.dto.AppointmentDto;
import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.ForbiddenException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.exception.SlotAlreadyBookedException;
import edu.sjsu.scheduler.model.AppUser;
import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit tests: repositories and the booking transaction are mocked, no database needed. */
class BookingServiceUnitTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    private UserRepository userRepo;
    private AppointmentRepository apptRepo;
    private SlotBookingTransaction tx;
    private BookingService service;

    private final AppUser alex = new AppUser(3L, "student_alex", "hash", "Alex Nguyen", "alex@sjsu.edu", "CUSTOMER");
    private final AppUser maya = new AppUser(4L, "student_maya", "hash", "Maya Johnson", "maya@sjsu.edu", "CUSTOMER");
    private final AppUser kim  = new AppUser(1L, "advisor_kim", "hash", "Dr. Sarah Kim", "kim@sjsu.edu", "PROVIDER");

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        apptRepo = mock(AppointmentRepository.class);
        tx = mock(SlotBookingTransaction.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new BookingService(userRepo, apptRepo, tx, clock);

        when(userRepo.findByUsername("student_alex")).thenReturn(Optional.of(alex));
        when(userRepo.findByUsername("student_maya")).thenReturn(Optional.of(maya));
        when(userRepo.findByUsername("advisor_kim")).thenReturn(Optional.of(kim));
    }

    private AppointmentRow row(long id, String status, LocalDateTime start) {
        return new AppointmentRow(id, status, null, NOW.minusDays(1), null, start, start.plusMinutes(30),
                "Registration Help", "Dr. Sarah Kim", "MQH 215", "Alex Nguyen", "alex@sjsu.edu");
    }

    // ---------- booking rules ----------

    @Test
    void book_happyPath_returnsAppointmentId() {
        when(tx.book(3L, 7L, "hi")).thenReturn(42L);
        assertThat(service.bookSlot(7L, "student_alex", "  hi  ")).isEqualTo(42L); // notes are trimmed
    }

    @Test
    void book_providerCannotBook() {
        assertThatThrownBy(() -> service.bookSlot(7L, "advisor_kim", null))
                .isInstanceOf(ForbiddenException.class);
        verify(tx, never()).book(anyLong(), anyLong(), any());
    }

    @Test
    void book_notesTooLong_is400() {
        String longNotes = "x".repeat(BookingService.MAX_NOTES + 1);
        assertThatThrownBy(() -> service.bookSlot(7L, "student_alex", longNotes))
                .isInstanceOf(InvalidRequestException.class);
        verify(tx, never()).book(anyLong(), anyLong(), any());
    }

    @Test
    void book_blankNotesBecomeNull() {
        when(tx.book(3L, 7L, null)).thenReturn(1L);
        service.bookSlot(7L, "student_alex", "   ");
        verify(tx).book(3L, 7L, null);
    }

    @Test
    void book_slotAlreadyBooked_isNotRetried() {
        when(tx.book(anyLong(), anyLong(), any())).thenThrow(new SlotAlreadyBookedException(7L));
        assertThatThrownBy(() -> service.bookSlot(7L, "student_alex", null))
                .isInstanceOf(SlotAlreadyBookedException.class);
        verify(tx, times(1)).book(anyLong(), anyLong(), any());
    }

    @Test
    void book_lockTimeout_isRetriedThenSucceeds() {
        when(tx.book(anyLong(), anyLong(), any()))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenReturn(99L);
        assertThat(service.bookSlot(7L, "student_alex", null)).isEqualTo(99L);
        verify(tx, times(2)).book(anyLong(), anyLong(), any());
    }

    @Test
    void book_givesUpAfterMaxAttempts_with409() {
        when(tx.book(anyLong(), anyLong(), any())).thenThrow(new CannotAcquireLockException("lock timeout"));
        assertThatThrownBy(() -> service.bookSlot(7L, "student_alex", null))
                .isInstanceOf(ConflictException.class);
        verify(tx, times(BookingService.MAX_ATTEMPTS)).book(anyLong(), anyLong(), any());
    }

    // ---------- owner-only cancel ----------

    @Test
    void cancel_ownerCanCancelUpcoming() {
        when(apptRepo.findCustomerId(10L)).thenReturn(Optional.of(3L));
        when(apptRepo.findById(10L)).thenReturn(Optional.of(row(10L, "BOOKED", NOW.plusDays(1))));
        when(apptRepo.cancel(10L, 3L)).thenReturn(1);

        service.cancel(10L, "student_alex");
        verify(apptRepo).cancel(10L, 3L);
    }

    @Test
    void cancel_otherStudent_is403() {
        when(apptRepo.findCustomerId(10L)).thenReturn(Optional.of(3L)); // owned by alex
        assertThatThrownBy(() -> service.cancel(10L, "student_maya"))
                .isInstanceOf(ForbiddenException.class);
        verify(apptRepo, never()).cancel(anyLong(), anyLong());
    }

    @Test
    void cancel_unknownAppointment_is404() {
        when(apptRepo.findCustomerId(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(10L, "student_alex"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_alreadyCancelled_is409() {
        when(apptRepo.findCustomerId(10L)).thenReturn(Optional.of(3L));
        when(apptRepo.findById(10L)).thenReturn(Optional.of(row(10L, "CANCELLED", NOW.plusDays(1))));
        assertThatThrownBy(() -> service.cancel(10L, "student_alex"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_pastAppointment_is400() {
        when(apptRepo.findCustomerId(10L)).thenReturn(Optional.of(3L));
        when(apptRepo.findById(10L)).thenReturn(Optional.of(row(10L, "BOOKED", NOW.minusDays(1))));
        assertThatThrownBy(() -> service.cancel(10L, "student_alex"))
                .isInstanceOf(InvalidRequestException.class);
        verify(apptRepo, never()).cancel(anyLong(), anyLong());
    }

    // ---------- status display ----------

    @Test
    void pastBookedAppointment_showsAsCompleted() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        AppointmentDto past = BookingService.toDto(row(1L, "BOOKED", NOW.minusHours(2)), clock);
        AppointmentDto future = BookingService.toDto(row(2L, "BOOKED", NOW.plusHours(2)), clock);

        assertThat(past.status()).isEqualTo("COMPLETED");
        assertThat(past.canCancel()).isFalse();
        assertThat(future.status()).isEqualTo("BOOKED");
        assertThat(future.canCancel()).isTrue();
    }
}
