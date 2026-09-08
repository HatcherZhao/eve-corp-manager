/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

/** 仅在首次启动时设置管理员初始密码，不覆盖用户已经修改的密码。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalAdminBootstrap implements ApplicationRunner {
    private static final String UNINITIALIZED = "!eve-bootstrap-required!";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String initialPassword;

    public LocalAdminBootstrap(JdbcTemplate jdbc,
                               PasswordEncoder encoder,
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
        jdbc.update("UPDATE sys_user SET password = ?, email = NULL, phone = NULL WHERE id = 1 AND password = ?", encoder
            .encode(initialPassword), UNINITIALIZED);
    }
}
