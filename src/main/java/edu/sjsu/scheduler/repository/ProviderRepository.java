package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.model.ProviderOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProviderRepository {

    private final JdbcTemplate jdbc;

    public ProviderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProviderOption> findAll() {
        String sql = """
            SELECT p.id, u.full_name, p.department
            FROM providers p
            JOIN users u ON u.id = p.user_id
            ORDER BY u.full_name
            """;
        return jdbc.query(sql, (rs, rowNum) -> new ProviderOption(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("department")));
    }

    /** Finds the provider profile id for a logged-in advisor. */
    public Optional<Long> findIdByUsername(String username) {
        String sql = """
            SELECT p.id
            FROM providers p
            JOIN users u ON u.id = p.user_id
            WHERE u.username = ?
            """;
        return jdbc.queryForList(sql, Long.class, username).stream().findFirst();
    }
}
