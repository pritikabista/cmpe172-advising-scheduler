package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.dto.SlotFilter;
import edu.sjsu.scheduler.dto.SlotPage;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.service.SchedulingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
public class SlotController {

    private final SchedulingService schedulingService;

    public SlotController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping("/slots")
    public String slots(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) Long providerId,
                        @RequestParam(required = false) Long serviceId,
                        @RequestParam(required = false) String date,
                        Model model) {
        LocalDate day = parseDate(date);
        SlotPage result = schedulingService.getAvailableSlots(new SlotFilter(providerId, serviceId, day), page);

        model.addAttribute("slots", result.slots());
        model.addAttribute("page", result.page());
        model.addAttribute("hasNext", result.hasNext());

        // keep the filter values so the form + pagination links remember them
        model.addAttribute("providerId", providerId);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("date", day == null ? null : day.toString());
        model.addAttribute("providers", schedulingService.getAllProviders());
        model.addAttribute("services", schedulingService.getAllServices());
        return "slots";
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Date must look like 2026-10-15.");
        }
    }
}
