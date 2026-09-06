package top.continew.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalAdminBootstrapTest {
    @Test
    void existingAdministratorPasswordIsNeverReplaced() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("already-initialized");
        new LocalAdminBootstrap(jdbc, new BCryptPasswordEncoder(), "").run(new DefaultApplicationArguments());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void missingInitialSecretFailsBeforeAnyWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("!eve-bootstrap-required!");
        assertThrows(IllegalStateException.class,
            () -> new LocalAdminBootstrap(jdbc, new BCryptPasswordEncoder(), "").run(new DefaultApplicationArguments()));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void firstStartupWritesOnlyAHashUsingCompareAndSet() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = UUID.randomUUID().toString();
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("!eve-bootstrap-required!");
        new LocalAdminBootstrap(jdbc, encoder, password).run(new DefaultApplicationArguments());
        verify(jdbc).update(eq("UPDATE sys_user SET password = ?, email = NULL, phone = NULL WHERE id = 1 AND password = ?"),
            argThat((String value) -> !value.equals(password) && encoder.matches(password, value)), eq("!eve-bootstrap-required!"));
    }
}
