/*
 * Copyright 2026 EVE Corp Manager contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package top.continew.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Set the initial administrator password exactly once, never overwrite a user's password. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalAdminBootstrap implements ApplicationRunner {
    private static final String UNINITIALIZED = "!eve-bootstrap-required!";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String initialPassword;

    public LocalAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
                               @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String initialPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String current = jdbc.queryForObject("SELECT password FROM sys_user WHERE id = 1", String.class);
        if (!UNINITIALIZED.equals(current)) {
            return;
        }
        if (initialPassword.length() < 16) {
            throw new IllegalStateException("Initialize BOOTSTRAP_ADMIN_PASSWORD with at least 16 characters before first startup");
        }
        jdbc.update("UPDATE sys_user SET password = ?, email = NULL, phone = NULL WHERE id = 1 AND password = ?",
            encoder.encode(initialPassword), UNINITIALIZED);
    }
}
