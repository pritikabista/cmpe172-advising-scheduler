package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.service.ProviderService;
import edu.sjsu.scheduler.service.SchedulingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Advisor pages: manage my slots and see who booked them. */
@Controller
public class ProviderController {

    private final ProviderService providerService;
    private final SchedulingService schedulingService;

    public ProviderController(ProviderService providerService, SchedulingService schedulingService) {
        this.providerService = providerService;
        this.schedulingService = schedulingService;
    }

    @GetMapping("/provider/slots")
    public String mySlots(Principal principal, Model model) {
        model.addAttribute("slots", providerService.getMySlots(principal.getName()));
        model.addAttribute("services", schedulingService.getAllServices());
        return "provider-slots";
    }

    @PostMapping("/provider/slots")
    public String createSlot(@RequestParam(required = false) Long serviceId,
                             @RequestParam(required = false) String date,
                             @RequestParam(required = false) String time,
                             Principal principal, RedirectAttributes flash) {
        providerService.createSlot(principal.getName(), serviceId, date, time);
        flash.addFlashAttribute("message", "Slot created.");
        return "redirect:/provider/slots";
    }

    @PostMapping("/provider/slots/{id}/delete")
    public String deleteSlot(@PathVariable long id, Principal principal, RedirectAttributes flash) {
        providerService.deleteSlot(principal.getName(), id);
        flash.addFlashAttribute("message", "Slot deleted.");
        return "redirect:/provider/slots";
    }

    @GetMapping("/provider/appointments")
    public String myAppointments(Principal principal, Model model) {
        model.addAttribute("appointments", providerService.getMyAppointments(principal.getName()));
        return "provider-appointments";
    }
}
