package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.model.AdvisingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRepository {

    private static final RowMapper<AdvisingService> MAPPER = (rs, rowNum) -> new AdvisingService(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("duration_minutes"),
            rs.getBigDecimal("price"));

    private final JdbcTemplate jdbc;

    public ServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdvisingService> findAll() {
        String sql = """
            SELECT id, name, description, duration_minutes, price
            FROM services
            ORDER BY name
            """;
        return jdbc.query(sql, MAPPER);
    }

    public Optional<AdvisingService> findById(long id) {
        String sql = """
            SELECT id, name, description, duration_minutes, price
            FROM services
            WHERE id = ?
            """;
        return jdbc.query(sql, MAPPER, id).stream().findFirst();
    }
}
