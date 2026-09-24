package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.dto.SlotDto;
import edu.sjsu.scheduler.service.SchedulingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SlotController {

    private final SchedulingService schedulingService;

    public SlotController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping("/slots")
    public String slots(@RequestParam(defaultValue = "0") int page, Model model) {
        List<SlotDto> slots = schedulingService.getAvailableSlots(page);
        model.addAttribute("slots", slots);
        model.addAttribute("page", Math.max(page, 0));
        model.addAttribute("hasNext", slots.size() == SchedulingService.PAGE_SIZE);
        return "slots";
    }
}