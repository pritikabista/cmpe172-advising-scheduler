package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.service.BookingService;
import edu.sjsu.scheduler.service.SchedulingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Student pages: book a slot, see my appointments, cancel. */
@Controller
public class BookingController {

    private final SchedulingService schedulingService;
    private final BookingService bookingService;

    public BookingController(SchedulingService schedulingService, BookingService bookingService) {
        this.schedulingService = schedulingService;
        this.bookingService = bookingService;
    }

    /** Appointment details page: shows the slot and lets the student add notes. */
    @GetMapping("/slots/{slotId}/book")
    public String bookForm(@PathVariable long slotId, Model model) {
        model.addAttribute("slot", schedulingService.getSlot(slotId));
        return "book";
    }

    /** POST-redirect-GET: book, then redirect so a page refresh doesn't book twice. */
    @PostMapping("/slots/{slotId}/book")
    public String book(@PathVariable long slotId,
                       @RequestParam(required = false) String notes,
                       Principal principal) {
        long appointmentId = bookingService.bookSlot(slotId, principal.getName(), notes);
        return "redirect:/appointments/" + appointmentId + "/confirmation";
    }

    @GetMapping("/appointments/{id}/confirmation")
    public String confirmation(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("appt", bookingService.getOwnAppointment(id, principal.getName()));
        return "confirmation";
    }

    @GetMapping("/appointments")
    public String myAppointments(Principal principal, Model model) {
        model.addAttribute("upcoming", bookingService.getUpcoming(principal.getName()));
        model.addAttribute("history", bookingService.getHistory(principal.getName()));
        return "appointments";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancel(@PathVariable long id, Principal principal, RedirectAttributes flash) {
        bookingService.cancel(id, principal.getName());
        flash.addFlashAttribute("message", "Appointment cancelled.");
        return "redirect:/appointments";
    }
}
