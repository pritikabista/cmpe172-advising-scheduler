package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.dto.ServiceDto;
import edu.sjsu.scheduler.dto.SlotDto;
import edu.sjsu.scheduler.dto.SlotFilter;
import edu.sjsu.scheduler.dto.SlotPage;
import edu.sjsu.scheduler.exception.NotFoundException;
import edu.sjsu.scheduler.model.ProviderOption;
import edu.sjsu.scheduler.model.SlotRow;
import edu.sjsu.scheduler.repository.ProviderRepository;
import edu.sjsu.scheduler.repository.ServiceRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read-only browsing: services, advisors, and available slots. */
@Service
public class SchedulingService {

    public static final int PAGE_SIZE = 5;

    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final ProviderRepository providerRepository;

    public SchedulingService(ServiceRepository serviceRepository, SlotRepository slotRepository,
                             ProviderRepository providerRepository) {
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.providerRepository = providerRepository;
    }

    public List<ServiceDto> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(s -> new ServiceDto(s.id(), s.name(), s.description(), s.durationMinutes()))
                .toList();
    }

    public List<ProviderOption> getAllProviders() {
        return providerRepository.findAll();
    }

    /**
     * One page of available slots. We ask the DB for PAGE_SIZE + 1 rows:
     * if the extra row comes back, we know there is a next page.
     */
    public SlotPage getAvailableSlots(SlotFilter filter, int page) {
        int safePage = Math.max(page, 0);
        List<SlotRow> rows = slotRepository.findAvailable(filter, PAGE_SIZE + 1, safePage * PAGE_SIZE);
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<SlotDto> slots = rows.stream().limit(PAGE_SIZE).map(SchedulingService::toDto).toList();
        return new SlotPage(slots, safePage, hasNext);
    }

    public SlotDto getSlot(long slotId) {
        return slotRepository.findById(slotId)
                .map(SchedulingService::toDto)
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " was not found."));
    }

    static SlotDto toDto(SlotRow r) {
        return new SlotDto(r.id(), Formats.date(r.startTime()),
                Formats.timeRange(r.startTime(), r.endTime()),
                r.serviceName(), r.advisorName(), r.officeLocation());
    }
}
