package edu.sjsu.scheduler.repository;

import edu.sjsu.scheduler.model.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, role
            FROM users
            WHERE username = ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new AppUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("role")),
                username).stream().findFirst();
    }
}
