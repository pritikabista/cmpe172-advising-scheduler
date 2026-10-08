package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.model.AppointmentRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentRepository {

    /** Shared SELECT + JOINs for showing an appointment with slot/service/advisor/student info. */
    private static final String BASE_SELECT = """
            SELECT a.id, a.status, a.notes, a.created_at, a.cancelled_at,
                   s.start_time, s.end_time,
                   sv.name AS service_name,
                   pu.full_name AS advisor_name, p.office_location,
                   cu.full_name AS student_name, cu.email AS student_email
            FROM appointments a
            JOIN availability_slots s ON s.id = a.slot_id
            JOIN services  sv ON sv.id = a.service_id
            JOIN providers p  ON p.id  = s.provider_id
            JOIN users     pu ON pu.id = p.user_id
            JOIN users     cu ON cu.id = a.customer_id
            """;

    private static final RowMapper<AppointmentRow> MAPPER = (rs, rowNum) -> new AppointmentRow(
            rs.getLong("id"),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("cancelled_at", LocalDateTime.class),
            rs.getObject("start_time", LocalDateTime.class),
            rs.getObject("end_time", LocalDateTime.class),
            rs.getString("service_name"),
            rs.getString("advisor_name"),
            rs.getString("office_location"),
            rs.getString("student_name"),
            rs.getString("student_email"));

    private final JdbcTemplate jdbc;

    public AppointmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActiveBooking(long slotId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM appointments
                WHERE slot_id = ? AND status = 'BOOKED')
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, slotId));
    }

    public boolean hasAnyAppointment(long slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM appointments WHERE slot_id = ?)", Boolean.class, slotId));
    }

    /** Inserts a BOOKED appointment. The partial unique index throws DuplicateKeyException on a double booking. */
    public long insertBooked(long customerId, long slotId, long serviceId, String notes) {
        String sql = """
            INSERT INTO appointments (customer_id, slot_id, service_id, status, notes)
            VALUES (?, ?, ?, 'BOOKED', ?)
            RETURNING id
            """;
        Long id = jdbc.queryForObject(sql, Long.class, customerId, slotId, serviceId, notes);
        return id == null ? -1 : id;
    }

    public Optional<AppointmentRow> findById(long id) {
        return jdbc.query(BASE_SELECT + " WHERE a.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Long> findCustomerId(long appointmentId) {
        return jdbc.queryForList("SELECT customer_id FROM appointments WHERE id = ?",
                Long.class, appointmentId).stream().findFirst();
    }

    /** Upcoming = still BOOKED and the slot has not started yet. */
    public List<AppointmentRow> findUpcomingForCustomer(long customerId) {
        String sql = BASE_SELECT + """
             WHERE a.customer_id = ?
               AND a.status = 'BOOKED'
               AND s.start_time > LOCALTIMESTAMP
             ORDER BY s.start_time
            """;
        return jdbc.query(sql, MAPPER, customerId);
    }

    /** History = cancelled appointments, or ones whose time has already passed. */
    public List<AppointmentRow> findHistoryForCustomer(long customerId) {
        String sql = BASE_SELECT + """
             WHERE a.customer_id = ?
               AND (a.status = 'CANCELLED' OR s.start_time <= LOCALTIMESTAMP)
             ORDER BY s.start_time DESC
            """;
        return jdbc.query(sql, MAPPER, customerId);
    }

    public List<AppointmentRow> findForProvider(long providerId) {
        String sql = BASE_SELECT + """
             WHERE s.provider_id = ?
             ORDER BY s.start_time DESC, a.created_at DESC
            """;
        return jdbc.query(sql, MAPPER, providerId);
    }

    /**
     * Owner-only cancel. The WHERE clause checks owner + status, so it returns 0
     * if the appointment is not theirs or is no longer BOOKED.
     */
    public int cancel(long appointmentId, long customerId) {
        String sql = """
            UPDATE appointments
            SET status = 'CANCELLED', cancelled_at = LOCALTIMESTAMP
            WHERE id = ? AND customer_id = ? AND status = 'BOOKED'
            """;
        return jdbc.update(sql, appointmentId, customerId);
    }
}
