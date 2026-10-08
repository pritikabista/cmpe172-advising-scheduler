package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.dto.AppointmentDto;
import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.ForbiddenException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.model.AppUser;
import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Student-side actions: book, view, and cancel appointments. */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    static final int MAX_NOTES = 255;
    static final int MAX_ATTEMPTS = 3;

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotBookingTransaction bookingTransaction;
    private final Clock clock;

    public BookingService(UserRepository userRepository, AppointmentRepository appointmentRepository,
                          SlotBookingTransaction bookingTransaction, Clock clock) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingTransaction = bookingTransaction;
        this.clock = clock;
    }

    /**
     * Validates input, then runs the booking transaction.
     * Retries only on temporary DB problems (lock timeout, deadlock).
     * A real conflict (slot already booked -> 409) is never retried.
     */
    public long bookSlot(long slotId, String username, String notes) {
        AppUser student = requireStudent(username);
        String cleanNotes = cleanNotes(notes);

        for (int attempt = 1; ; attempt++) {
            try {
                long id = bookingTransaction.book(student.id(), slotId, cleanNotes);
                log.info("Appointment {} booked: slot={} user={} attempt={}", id, slotId, username, attempt);
                return id;
            } catch (TransientDataAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Booking slot {} failed after {} attempts", slotId, attempt);
                    throw new ConflictException("The slot is busy right now. Please try again.");
                }
                log.info("Retrying booking for slot {} (attempt {} failed: {})",
                        slotId, attempt, e.getClass().getSimpleName());
            }
        }
    }

    /** Owner-only cancel of an upcoming BOOKED appointment. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(long appointmentId, String username) {
        AppUser student = requireStudent(username);

        long ownerId = appointmentRepository.findCustomerId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " was not found."));
        if (ownerId != student.id()) {
            log.warn("Cancel denied: appointment {} is not owned by {}", appointmentId, username);
            throw new ForbiddenException("You can only cancel your own appointments.");
        }

        AppointmentRow appt = appointmentRepository.findById(appointmentId).orElseThrow();
        if (!"BOOKED".equals(appt.status())) {
            throw new ConflictException("This appointment is already cancelled.");
        }
        if (!appt.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Past appointments can't be cancelled.");
        }
        // The UPDATE re-checks owner + BOOKED, so a concurrent cancel can't apply twice.
        if (appointmentRepository.cancel(appointmentId, student.id()) != 1) {
            throw new ConflictException("This appointment was already changed. Please refresh.");
        }
        log.info("Appointment {} cancelled by {}", appointmentId, username);
    }

    /** Confirmation page data. Students can only see their own appointments. */
    public AppointmentDto getOwnAppointment(long appointmentId, String username) {
        AppUser student = requireStudent(username);
        long ownerId = appointmentRepository.findCustomerId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment " + appointmentId + " was not found."));
        if (ownerId != student.id()) {
            throw new ForbiddenException("You can only view your own appointments.");
        }
        return appointmentRepository.findById(appointmentId).map(r -> toDto(r, clock)).orElseThrow();
    }

    public List<AppointmentDto> getUpcoming(String username) {
        AppUser student = requireStudent(username);
        return appointmentRepository.findUpcomingForCustomer(student.id()).stream()
                .map(r -> toDto(r, clock)).toList();
    }

    public List<AppointmentDto> getHistory(String username) {
        AppUser student = requireStudent(username);
        return appointmentRepository.findHistoryForCustomer(student.id()).stream()
                .map(r -> toDto(r, clock)).toList();
    }

    private AppUser requireStudent(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ForbiddenException("Unknown user."));
        if (!"CUSTOMER".equals(user.role())) {
            throw new ForbiddenException("Only students can do this.");
        }
        return user;
    }

    static String cleanNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > MAX_NOTES) {
            throw new InvalidRequestException("Notes must be " + MAX_NOTES + " characters or less.");
        }
        return trimmed;
    }

    /** Status shown to users: a BOOKED appointment whose time has passed is shown as COMPLETED. */
    static AppointmentDto toDto(AppointmentRow r, Clock clock) {
        boolean upcoming = r.startTime().isAfter(LocalDateTime.now(clock));
        String status = r.status();
        if ("BOOKED".equals(status) && !upcoming) {
            status = "COMPLETED";
        }
        return new AppointmentDto(r.id(), Formats.date(r.startTime()),
                Formats.timeRange(r.startTime(), r.endTime()),
                r.serviceName(), r.advisorName(), r.location(),
                r.studentName(), r.studentEmail(),
                status, r.notes(), "BOOKED".equals(status) && upcoming);
    }
}
