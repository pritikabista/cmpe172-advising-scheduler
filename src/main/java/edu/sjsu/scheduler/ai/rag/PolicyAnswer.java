package edu.sjsu.scheduler.ai.rag;

import java.util.List;

/** A grounded answer plus the titles of the policy sections it was based on. */
public record PolicyAnswer(String answer, List<String> sources) {}
