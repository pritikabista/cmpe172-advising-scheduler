package edu.sjsu.scheduler.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds login info to every page so the nav bar can show the right links. */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("username")
    public String username(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
    }

    @ModelAttribute("isStudent")
    public boolean isStudent(HttpServletRequest request) {
        return request.isUserInRole("CUSTOMER");
    }

    @ModelAttribute("isAdvisor")
    public boolean isAdvisor(HttpServletRequest request) {
        return request.isUserInRole("PROVIDER");
    }
}
