package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.service.SchedulingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final SchedulingService schedulingService;

    public HomeController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("services", schedulingService.getAllServices());
        return "index";
    }
}