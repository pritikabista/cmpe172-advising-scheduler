package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.dto.SlotFilter;
import edu.sjsu.scheduler.model.ProviderSlotRow;
import edu.sjsu.scheduler.model.SlotLock;
import edu.sjsu.scheduler.model.SlotRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SlotRepository {

    private final JdbcTemplate jdbc;

    public SlotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Future slots with no BOOKED appointment, filtered by advisor / service / date.
     * Filters are added as "?" parameters (never string-concatenated values) to avoid SQL injection.
     */
    public List<SlotRow> findAvailable(SlotFilter filter, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT s.id, s.start_time, s.end_time,
                   sv.name AS service_name, sv.duration_minutes,
                   u.full_name AS advisor_name, p.department, p.office_location
            FROM availability_slots s
            JOIN services  sv ON sv.id = s.service_id
            JOIN providers p  ON p.id  = s.provider_id
            JOIN users     u  ON u.id  = p.user_id
            WHERE s.start_time > LOCALTIMESTAMP
              AND NOT EXISTS (
                  SELECT 1 FROM appointments a
                  WHERE a.slot_id = s.id AND a.status = 'BOOKED')
            """);
        List<Object> args = new ArrayList<>();

        if (filter.providerId() != null) {
            sql.append(" AND s.provider_id = ?");
            args.add(filter.providerId());
        }
        if (filter.serviceId() != null) {
            sql.append(" AND s.service_id = ?");
            args.add(filter.serviceId());
        }
        if (filter.date() != null) {
            sql.append(" AND s.start_time >= ? AND s.start_time < ?");
            args.add(filter.date().atStartOfDay());
            args.add(filter.date().plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY s.start_time, s.id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new SlotRow(
                        rs.getLong("id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getObject("end_time", LocalDateTime.class),
                        rs.getString("service_name"),
                        rs.getInt("duration_minutes"),
                        rs.getString("advisor_name"),
                        rs.getString("department"),
                        rs.getString("office_location")),
                args.toArray());
    }

    /** Single slot with display info (used on the booking confirm page). */
    public Optional<SlotRow> findById(long slotId) {
        String sql = """
            SELECT s.id, s.start_time, s.end_time,
                   sv.name AS service_name, sv.duration_minutes,
                   u.full_name AS advisor_name, p.department, p.office_location
            FROM availability_slots s
            JOIN services  sv ON sv.id = s.service_id
            JOIN providers p  ON p.id  = s.provider_id
            JOIN users     u  ON u.id  = p.user_id
            WHERE s.id = ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new SlotRow(
                        rs.getLong("id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getObject("end_time", LocalDateTime.class),
                        rs.getString("service_name"),
                        rs.getInt("duration_minutes"),
                        rs.getString("advisor_name"),
                        rs.getString("department"),
                        rs.getString("office_location")),
                slotId).stream().findFirst();
    }

    /** Only affects the current transaction (SET LOCAL). */
    public void setLockTimeout() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
    }

    /**
     * Locks the slot row until the current transaction ends.
     * A second transaction that calls this for the same slot waits here
     * until the first one commits or rolls back.
     */
    public Optional<SlotLock> lockById(long slotId) {
        String sql = """
            SELECT id, provider_id, service_id, start_time, version
            FROM availability_slots
            WHERE id = ?
            FOR UPDATE
            """;
        return jdbc.query(sql, (rs, rowNum) -> new SlotLock(
                        rs.getLong("id"),
                        rs.getLong("provider_id"),
                        rs.getLong("service_id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getInt("version")),
                slotId).stream().findFirst();
    }

    /** Optimistic version check: only succeeds if nobody changed the slot since we read it. */
    public boolean bumpVersion(long slotId, int expectedVersion) {
        String sql = """
            UPDATE availability_slots
            SET version = version + 1
            WHERE id = ? AND version = ?
            """;
        return jdbc.update(sql, slotId, expectedVersion) == 1;
    }

    public List<ProviderSlotRow> findForProvider(long providerId) {
        String sql = """
            SELECT s.id, s.start_time, s.end_time, sv.name AS service_name,
                   cu.full_name AS booked_by
            FROM availability_slots s
            JOIN services sv ON sv.id = s.service_id
            LEFT JOIN appointments a ON a.slot_id = s.id AND a.status = 'BOOKED'
            LEFT JOIN users cu ON cu.id = a.customer_id
            WHERE s.provider_id = ?
            ORDER BY s.start_time DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> new ProviderSlotRow(
                        rs.getLong("id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getObject("end_time", LocalDateTime.class),
                        rs.getString("service_name"),
                        rs.getString("booked_by")),
                providerId);
    }

    /** True if this advisor already has a slot that overlaps [start, end). */
    public boolean hasOverlap(long providerId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM availability_slots
                WHERE provider_id = ?
                  AND start_time < ?
                  AND end_time   > ?)
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, providerId, end, start));
    }

    public long insert(long providerId, long serviceId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;
        Long id = jdbc.queryForObject(sql, Long.class, providerId, serviceId, start, end);
        return id == null ? -1 : id;
    }

    public Optional<Long> findProviderId(long slotId) {
        return jdbc.queryForList("SELECT provider_id FROM availability_slots WHERE id = ?",
                Long.class, slotId).stream().findFirst();
    }

    public int delete(long slotId) {
        return jdbc.update("DELETE FROM availability_slots WHERE id = ?", slotId);
    }
}
