package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.model.SlotRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SlotRepository {

    private final JdbcTemplate jdbc;

    public SlotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SlotRow> findAvailable(int limit, int offset) {
        String sql = """
            SELECT s.id, s.start_time, s.end_time,
                   sv.name AS service_name, sv.duration_minutes,
                   u.full_name AS advisor_name, p.department, p.office_location
            FROM availability_slots s
            JOIN services  sv ON sv.id = s.service_id
            JOIN providers p  ON p.id  = s.provider_id
            JOIN users     u  ON u.id  = p.user_id
            WHERE s.start_time > CURRENT_TIMESTAMP
              AND NOT EXISTS (
                  SELECT 1 FROM appointments a
                  WHERE a.slot_id = s.id AND a.status = 'BOOKED')
            ORDER BY s.start_time
            LIMIT ? OFFSET ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new SlotRow(
                        rs.getLong("id"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getString("service_name"),
                        rs.getInt("duration_minutes"),
                        rs.getString("advisor_name"),
                        rs.getString("department"),
                        rs.getString("office_location")),
                limit, offset);
    }
}