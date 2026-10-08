package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.dto.AppointmentDto;
import edu.sjsu.scheduler.dto.ProviderSlotDto;
import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.ForbiddenException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.model.AdvisingService;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.ProviderRepository;
import edu.sjsu.scheduler.repository.ServiceRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Advisor-side actions: manage availability slots and view bookings. */
@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderRepository providerRepository;
    private final SlotRepository slotRepository;
    private final ServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public ProviderService(ProviderRepository providerRepository, SlotRepository slotRepository,
                           ServiceRepository serviceRepository, AppointmentRepository appointmentRepository,
                           Clock clock) {
        this.clock = clock;
        this.providerRepository = providerRepository;
        this.slotRepository = slotRepository;
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public List<ProviderSlotDto> getMySlots(String username) {
        long providerId = requireProviderId(username);
        LocalDateTime now = LocalDateTime.now(clock);
        return slotRepository.findForProvider(providerId).stream()
                .map(r -> new ProviderSlotDto(r.id(), Formats.date(r.startTime()),
                        Formats.timeRange(r.startTime(), r.endTime()),
                        r.serviceName(), r.bookedBy(), r.startTime().isAfter(now)))
                .toList();
    }

    public List<AppointmentDto> getMyAppointments(String username) {
        long providerId = requireProviderId(username);
        return appointmentRepository.findForProvider(providerId).stream()
                .map(r -> BookingService.toDto(r, clock)).toList();
    }

    /** Creates a slot. End time = start time + the service's duration. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createSlot(String username, Long serviceId, String dateText, String timeText) {
        long providerId = requireProviderId(username);
        if (serviceId == null) {
            throw new InvalidRequestException("Please choose a service.");
        }
        AdvisingService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new InvalidRequestException("Unknown service."));

        LocalDateTime start;
        try {
            start = LocalDate.parse(dateText).atTime(LocalTime.parse(timeText));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRequestException("Please enter a valid date and time.");
        }
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Slots must start in the future.");
        }
        LocalDateTime end = start.plusMinutes(service.durationMinutes());

        if (slotRepository.hasOverlap(providerId, start, end)) {
            throw new ConflictException("This slot overlaps with one of your existing slots.");
        }
        try {
            long id = slotRepository.insert(providerId, serviceId, start, end);
            log.info("Slot {} created by {} ({} at {})", id, username, service.name(), start);
            return id;
        } catch (DuplicateKeyException e) {
            throw new ConflictException("You already have a slot starting at that time.");
        }
    }

    /** Advisors can only delete their own slots, and only if no student ever booked them. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteSlot(String username, long slotId) {
        long providerId = requireProviderId(username);
        long ownerId = slotRepository.findProviderId(slotId)
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " was not found."));
        if (ownerId != providerId) {
            throw new ForbiddenException("You can only delete your own slots.");
        }
        slotRepository.lockById(slotId);
        if (appointmentRepository.hasActiveBooking(slotId)) {
            throw new ConflictException("This slot is booked by a student and can't be deleted.");
        }
        if (appointmentRepository.hasAnyAppointment(slotId)) {
            throw new ConflictException("This slot has booking history and can't be deleted.");
        }
        slotRepository.delete(slotId);
        log.info("Slot {} deleted by {}", slotId, username);
    }

    private long requireProviderId(String username) {
        return providerRepository.findIdByUsername(username)
                .orElseThrow(() -> new ForbiddenException("Only advisors can do this."));
    }
}
