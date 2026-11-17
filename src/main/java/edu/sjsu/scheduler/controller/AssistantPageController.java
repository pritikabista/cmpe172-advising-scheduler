package edu.sjsu.scheduler.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The chat page for students (it calls /api/chat and /api/ask with JavaScript). */
@Controller
public class AssistantPageController {

    @GetMapping("/assistant")
    public String assistant() {
        return "assistant";
    }
}
