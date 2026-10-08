package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.ForbiddenException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.model.AdvisingService;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.ProviderRepository;
import edu.sjsu.scheduler.repository.ServiceRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for advisor slot rules (repositories mocked). */
class ProviderServiceUnitTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    private SlotRepository slotRepo;
    private AppointmentRepository apptRepo;
    private ProviderService service;

    @BeforeEach
    void setUp() {
        ProviderRepository providerRepo = mock(ProviderRepository.class);
        ServiceRepository serviceRepo = mock(ServiceRepository.class);
        slotRepo = mock(SlotRepository.class);
        apptRepo = mock(AppointmentRepository.class);
        service = new ProviderService(providerRepo, slotRepo, serviceRepo, apptRepo,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));

        when(providerRepo.findIdByUsername("advisor_kim")).thenReturn(Optional.of(1L));
        when(providerRepo.findIdByUsername("student_alex")).thenReturn(Optional.empty());
        when(serviceRepo.findById(1L)).thenReturn(Optional.of(
                new AdvisingService(1L, "Registration Help", "", 30, BigDecimal.ZERO)));
    }

    @Test
    void createSlot_endTimeComesFromServiceDuration() {
        when(slotRepo.insert(1L, 1L, LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 9, 30)))
                .thenReturn(50L);
        assertThat(service.createSlot("advisor_kim", 1L, "2026-10-10", "09:00")).isEqualTo(50L);
    }

    @Test
    void createSlot_studentIsForbidden() {
        assertThatThrownBy(() -> service.createSlot("student_alex", 1L, "2026-10-10", "09:00"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createSlot_badDate_is400() {
        assertThatThrownBy(() -> service.createSlot("advisor_kim", 1L, "10/10/2026", "09:00"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.createSlot("advisor_kim", 1L, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createSlot_inPast_is400() {
        assertThatThrownBy(() -> service.createSlot("advisor_kim", 1L, "2026-10-01", "09:00"))
                .isInstanceOf(InvalidRequestException.class);
        verify(slotRepo, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    void createSlot_missingOrUnknownService_is400() {
        assertThatThrownBy(() -> service.createSlot("advisor_kim", null, "2026-10-10", "09:00"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.createSlot("advisor_kim", 999L, "2026-10-10", "09:00"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createSlot_overlap_is409() {
        when(slotRepo.hasOverlap(anyLong(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createSlot("advisor_kim", 1L, "2026-10-10", "09:00"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteSlot_otherAdvisorsSlot_is403() {
        when(slotRepo.findProviderId(8L)).thenReturn(Optional.of(2L)); // owned by advisor 2
        assertThatThrownBy(() -> service.deleteSlot("advisor_kim", 8L))
                .isInstanceOf(ForbiddenException.class);
        verify(slotRepo, never()).delete(anyLong());
    }

    @Test
    void deleteSlot_bookedSlot_is409() {
        when(slotRepo.findProviderId(8L)).thenReturn(Optional.of(1L));
        when(apptRepo.hasActiveBooking(8L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSlot("advisor_kim", 8L))
                .isInstanceOf(ConflictException.class);
        verify(slotRepo, never()).delete(anyLong());
    }
}
