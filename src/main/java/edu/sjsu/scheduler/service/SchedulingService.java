package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.dto.ServiceDto;
import edu.sjsu.scheduler.dto.SlotDto;
import edu.sjsu.scheduler.model.SlotRow;
import edu.sjsu.scheduler.repository.ServiceRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SchedulingService {

    public static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;

    public SchedulingService(ServiceRepository serviceRepository, SlotRepository slotRepository) {
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
    }

    public List<ServiceDto> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(s -> new ServiceDto(s.id(), s.name(), s.description(), s.durationMinutes()))
                .toList();
    }

    public List<SlotDto> getAvailableSlots(int page) {
        int safePage = Math.max(page, 0);
        return slotRepository.findAvailable(PAGE_SIZE, safePage * PAGE_SIZE).stream()
                .map(this::toDto)
                .toList();
    }

    private SlotDto toDto(SlotRow r) {
        String time = r.startTime().format(TIME_FMT) + " - " + r.endTime().format(TIME_FMT);
        return new SlotDto(r.id(), r.startTime().format(DATE_FMT), time,
                r.serviceName(), r.advisorName(), r.officeLocation());
    }
}