package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.exception.SlotAlreadyBookedException;
import edu.sjsu.scheduler.model.SlotLock;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * One booking attempt = one database transaction.
 *
 * Kept in its own bean so BookingService can retry it: calling a @Transactional
 * method from another bean goes through Spring's proxy, so every retry gets a fresh transaction.
 *
 * Isolation: READ COMMITTED (PostgreSQL default). The row lock below already serializes
 * bookings for the same slot, and after the lock is released the next transaction's
 * new SELECT sees the committed appointment, so a stricter level is not needed.
 */
@Component
public class SlotBookingTransaction {

    private static final Logger log = LoggerFactory.getLogger(SlotBookingTransaction.class);

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public SlotBookingTransaction(SlotRepository slotRepository, AppointmentRepository appointmentRepository,
                                  Clock clock) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long book(long studentId, long slotId, String notes) {
        // Don't wait forever for the lock; a timeout becomes CannotAcquireLockException -> retried.
        slotRepository.setLockTimeout();

        // 1) Pessimistic lock: a second booking for the same slot waits here.
        SlotLock slot = slotRepository.lockById(slotId)
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " was not found."));

        if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("This slot is in the past and can't be booked.");
        }

        // 2) Business rule check while holding the lock.
        if (appointmentRepository.hasActiveBooking(slotId)) {
            log.info("Booking rejected: slot {} already booked", slotId);
            throw new SlotAlreadyBookedException(slotId);
        }

        // 3) Insert. The partial unique index is the database-level backstop.
        long appointmentId;
        try {
            appointmentId = appointmentRepository.insertBooked(studentId, slotId, slot.serviceId(), notes);
        } catch (DuplicateKeyException e) {
            log.info("Booking rejected by unique index: slot {}", slotId);
            throw new SlotAlreadyBookedException(slotId);
        }

        // 4) Optimistic version bump (also records that the slot changed).
        if (!slotRepository.bumpVersion(slotId, slot.version())) {
            throw new ConflictException("Slot " + slotId + " changed while booking. Please try again.");
        }
        return appointmentId;
    }
}
