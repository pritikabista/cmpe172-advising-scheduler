package edu.sjsu.scheduler.dto;

import java.util.List;

public record SlotPage(List<SlotDto> slots, int page, boolean hasNext) {}
