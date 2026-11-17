package edu.sjsu.scheduler.ai.agent;

import edu.sjsu.scheduler.dto.AppointmentDto;
import edu.sjsu.scheduler.dto.ServiceDto;
import edu.sjsu.scheduler.dto.SlotFilter;
import edu.sjsu.scheduler.exception.AppException;
import edu.sjsu.scheduler.exception.SlotAlreadyBookedException;
import edu.sjsu.scheduler.model.ProviderOption;
import edu.sjsu.scheduler.model.SlotRow;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.repository.SlotRepository;
import edu.sjsu.scheduler.service.BookingService;
import edu.sjsu.scheduler.service.SchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tools the chat agent can call. Each one wraps an EXISTING service-layer method, so the agent
 * gets exactly the same rules as the website:
 *   - writes go through BookingService (transaction + row lock + unique index -> no double booking)
 *   - BookingService.requireStudent checks the role (RBAC)
 *   - the user comes from ToolContext (the logged-in session), never from the model
 * Tools return plain text that the model reads; they never throw, so errors become messages
 * the model can explain to the student.
 */
@Component
public class AppointmentTools {

    private static final Logger log = LoggerFactory.getLogger(AppointmentTools.class);
    private static final DateTimeFormatter SLOT_FORMAT =
            DateTimeFormatter.ofPattern("EEE yyyy-MM-dd h:mm a", Locale.US);
    static final int MAX_RESULTS = 8;

    private final SchedulingService schedulingService;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingService bookingService;
    private final PendingActionStore pendingActions;
    private final Clock clock;

    public AppointmentTools(SchedulingService schedulingService, SlotRepository slotRepository,
                            AppointmentRepository appointmentRepository, BookingService bookingService,
                            PendingActionStore pendingActions, Clock clock) {
        this.schedulingService = schedulingService;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingService = bookingService;
        this.pendingActions = pendingActions;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ read tools

    @Tool(description = """
            Search OPEN advising slots, earliest first. All filters are optional.
            Use this before proposing a booking, and to offer alternatives.""")
    public String findAvailableSlots(
            @ToolParam(description = "Earliest date to include, format yyyy-MM-dd. Omit for today.", required = false)
            String fromDate,
            @ToolParam(description = "Last date to include (inclusive), format yyyy-MM-dd. Omit for no limit.", required = false)
            String toDate,
            @ToolParam(description = "Service name or part of it, e.g. 'Registration Help'.", required = false)
            String service,
            @ToolParam(description = "Advisor name or part of it, e.g. 'Kim'.", required = false)
            String advisor) {
        LocalDate from;
        LocalDate to;
        try {
            from = isBlank(fromDate) ? LocalDate.now(clock) : LocalDate.parse(fromDate.strip());
            to = isBlank(toDate) ? null : LocalDate.parse(toDate.strip());
        } catch (DateTimeParseException e) {
            return "ERROR: dates must use the format yyyy-MM-dd.";
        }

        Long serviceId = null;
        if (!isBlank(service)) {
            List<ServiceDto> matches = schedulingService.getAllServices().stream()
                    .filter(s -> containsIgnoreCase(s.name(), service)).toList();
            if (matches.isEmpty()) {
                return "No service matches '" + service + "'. Services: "
                        + schedulingService.getAllServices().stream().map(ServiceDto::name)
                        .collect(Collectors.joining(", "));
            }
            serviceId = matches.get(0).id();
        }
        Long providerId = null;
        if (!isBlank(advisor)) {
            List<ProviderOption> matches = schedulingService.getAllProviders().stream()
                    .filter(p -> containsIgnoreCase(p.name(), advisor)).toList();
            if (matches.isEmpty()) {
                return "No advisor matches '" + advisor + "'. Advisors: "
                        + schedulingService.getAllProviders().stream().map(ProviderOption::name)
                        .collect(Collectors.joining(", "));
            }
            providerId = matches.get(0).id();
        }

        // Same SQL as the website's slot page (future, not booked, ordered by start time)
        List<SlotRow> slots = slotRepository.findAvailable(new SlotFilter(providerId, serviceId, null), 100, 0)
                .stream()
                .filter(s -> !s.startTime().toLocalDate().isBefore(from))
                .filter(s -> to == null || !s.startTime().toLocalDate().isAfter(to))
                .limit(MAX_RESULTS)
                .toList();
        if (slots.isEmpty()) {
            return "No open slots match these filters.";
        }
        return "Open slots (earliest first):\n" + slots.stream().map(AppointmentTools::describe)
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "List the logged-in student's upcoming appointments and history (with appointment ids).")
    public String listMyAppointments(ToolContext context) {
        String username = username(context);
        try {
            List<AppointmentDto> upcoming = bookingService.getUpcoming(username);
            List<AppointmentDto> history = bookingService.getHistory(username);
            return "UPCOMING:\n" + describeAppointments(upcoming) + "\nHISTORY:\n" + describeAppointments(history);
        } catch (AppException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ write tools (two-step)

    @Tool(description = """
            Step 1 of booking. Checks the slot and saves it as a PENDING booking. It does NOT book.
            After calling this, show the user the summary and ask them to confirm.""")
    public String proposeBooking(
            @ToolParam(description = "The slotId from findAvailableSlots") long slotId,
            @ToolParam(description = "Optional note for the advisor", required = false) String notes,
            ToolContext context) {
        SlotRow slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null) {
            return "ERROR: slot " + slotId + " does not exist. Search again with findAvailableSlots.";
        }
        if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
            return "ERROR: slot " + slotId + " is in the past.";
        }
        if (appointmentRepository.hasActiveBooking(slotId)) {
            return "UNAVAILABLE: slot " + slotId + " is already booked. " + alternativesFor(slot);
        }
        String summary = "Book " + describe(slot) + (isBlank(notes) ? "" : " | note: " + notes.strip());
        pendingActions.propose(conversation(context), new PendingAction(PendingAction.Type.BOOK, slotId,
                isBlank(notes) ? null : notes.strip(), summary, turn(context), LocalDateTime.now(clock)));
        log.info("Agent proposed booking slot {} for {}", slotId, username(context));
        return "PENDING (not booked yet): " + summary
                + "\nAsk the user to confirm. Only call confirmPendingAction after the user says yes in a NEW message.";
    }

    @Tool(description = """
            Step 1 of cancelling. Checks the appointment belongs to the user and saves a PENDING
            cancellation. It does NOT cancel. Then ask the user to confirm.""")
    public String proposeCancellation(
            @ToolParam(description = "The appointment id from listMyAppointments") long appointmentId,
            ToolContext context) {
        try {
            AppointmentDto appt = bookingService.getOwnAppointment(appointmentId, username(context));
            if (!appt.canCancel()) {
                return "ERROR: appointment " + appointmentId + " cannot be cancelled (status " + appt.status() + ").";
            }
            String summary = "Cancel appointment " + appointmentId + ": " + appt.serviceName() + " with "
                    + appt.advisorName() + " on " + appt.date() + ", " + appt.time();
            pendingActions.propose(conversation(context), new PendingAction(PendingAction.Type.CANCEL,
                    appointmentId, null, summary, turn(context), LocalDateTime.now(clock)));
            return "PENDING (not cancelled yet): " + summary
                    + "\nAsk the user to confirm. Only call confirmPendingAction after the user says yes in a NEW message.";
        } catch (AppException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = """
            Step 2: carry out the pending booking/cancellation. Call ONLY when the user's latest
            message clearly confirms it (e.g. 'yes', 'confirm', 'book it').""")
    public String confirmPendingAction(ToolContext context) {
        String conversation = conversation(context);
        String username = username(context);
        PendingAction action = pendingActions.peek(conversation).orElse(null);
        if (action == null) {
            return "Nothing is pending. Propose a booking or cancellation first.";
        }
        if (!PendingActionStore.userHasRepliedSince(action, turn(context))) {
            log.warn("Agent tried to confirm in the same turn it proposed (user={}) - refused", username);
            return "REFUSED: the user has not confirmed yet. Show the summary and wait for their reply.";
        }
        pendingActions.clear(conversation);

        try {
            if (action.type() == PendingAction.Type.BOOK) {
                // Same transactional path as the website: row lock + version + unique index + RBAC
                long appointmentId = bookingService.bookSlot(action.targetId(), username, action.notes());
                AppointmentDto appt = bookingService.getOwnAppointment(appointmentId, username);
                log.info("Agent booked appointment {} (slot {}) for {}", appointmentId, action.targetId(), username);
                return "BOOKED: appointment " + appointmentId + " - " + appt.serviceName() + " with "
                        + appt.advisorName() + " on " + appt.date() + ", " + appt.time() + " at " + appt.location();
            }
            bookingService.cancel(action.targetId(), username);
            log.info("Agent cancelled appointment {} for {}", action.targetId(), username);
            return "CANCELLED: appointment " + action.targetId();
        } catch (SlotAlreadyBookedException e) {
            // Someone else got the slot between "propose" and "confirm": the transaction blocked it.
            log.info("Agent booking blocked by double-booking guard: slot {} user={}", action.targetId(), username);
            String alternatives = slotRepository.findById(action.targetId()).map(this::alternativesFor).orElse("");
            return "FAILED: that slot was just booked by someone else. " + alternatives;
        } catch (AppException e) {
            return "FAILED (" + e.getStatus().value() + "): " + e.getMessage();
        }
    }

    @Tool(description = "Drop the pending booking/cancellation when the user says no or changes their mind.")
    public String cancelPendingAction(ToolContext context) {
        pendingActions.clear(conversation(context));
        return "OK, nothing pending anymore.";
    }

    // ------------------------------------------------------------------ helpers

    /** Up to 3 other open slots for the same service, so the agent can offer alternatives. */
    private String alternativesFor(SlotRow taken) {
        List<SlotRow> others = slotRepository.findAvailable(new SlotFilter(null, null, null), 100, 0).stream()
                .filter(s -> s.serviceName().equals(taken.serviceName()) && !s.id().equals(taken.id()))
                .limit(3)
                .toList();
        if (others.isEmpty()) {
            return "There are no other open " + taken.serviceName() + " slots right now.";
        }
        return "Alternatives:\n" + others.stream().map(AppointmentTools::describe).collect(Collectors.joining("\n"));
    }

    static String describe(SlotRow s) {
        return "slotId=" + s.id() + " | " + s.startTime().format(SLOT_FORMAT) + " - "
                + s.endTime().toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                + " | " + s.serviceName() + " | " + s.advisorName() + " | " + s.officeLocation();
    }

    private static String describeAppointments(List<AppointmentDto> list) {
        if (list.isEmpty()) {
            return "(none)";
        }
        return list.stream().map(a -> "appointmentId=" + a.id() + " | " + a.date() + ", " + a.time() + " | "
                        + a.serviceName() + " | " + a.advisorName() + " | " + a.status()
                        + (a.canCancel() ? " | cancellable" : ""))
                .collect(Collectors.joining("\n"));
    }

    private static String username(ToolContext context) {
        return (String) context.getContext().get(ToolContextKeys.USERNAME);
    }

    private static String conversation(ToolContext context) {
        return (String) context.getContext().get(ToolContextKeys.CONVERSATION_ID);
    }

    private static int turn(ToolContext context) {
        return (Integer) context.getContext().get(ToolContextKeys.TURN);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean containsIgnoreCase(String text, String part) {
        return text.toLowerCase(Locale.ROOT).contains(part.strip().toLowerCase(Locale.ROOT));
    }
}
