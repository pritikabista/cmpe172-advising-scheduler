package edu.sjsu.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** After login, send students to the slots page and advisors to their dashboard. */
    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request) {
        if (request.isUserInRole("PROVIDER")) {
            return "redirect:/provider/slots";
        }
        return "redirect:/slots";
    }
}
