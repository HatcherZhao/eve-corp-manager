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

package top.continew.admin;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为显式启用的集成测试安全加载仓库根目录本地环境，不输出任何配置值。
 *
 * @author zhaoyuqing
 */
public final class LocalIntegrationEnvironment {

    private static final Set<String> LOCAL_SECRET_KEYS = Set
        .of("BOOTSTRAP_ADMIN_PASSWORD", "SA_JWT_SECRET", "FIELD_AES_KEY", "FIELD_RSA_PUBLIC_KEY", "FIELD_RSA_PRIVATE_KEY");

    private LocalIntegrationEnvironment() {
    }

    /**
     * 加载专用 G008 集成环境；数据库密码缺失时跳过，禁止误用开发 `.env` 凭据。
     */
    public static void configureOrSkip() {
        String databasePassword = read("G008_IT_DB_PWD");
        Assumptions.assumeTrue(databasePassword != null && !databasePassword
            .isBlank(), "未配置 G008_IT_DB_PWD，跳过真实 MySQL/Redis 集成测试");
        set("DB_HOST", readOrDefault("G008_IT_DB_HOST", "127.0.0.1"));
        set("DB_PORT", readOrDefault("G008_IT_DB_PORT", "3306"));
        set("DB_NAME", readOrDefault("G008_IT_DB_NAME", "eve_corp_manager"));
        set("DB_USER", readOrDefault("G008_IT_DB_USER", "root"));
        set("DB_PWD", databasePassword);
        set("REDIS_HOST", readOrDefault("G008_IT_REDIS_HOST", "127.0.0.1"));
        set("REDIS_PORT", readOrDefault("G008_IT_REDIS_PORT", "6379"));
        set("REDIS_DB", readOrDefault("G008_IT_REDIS_DB", "14"));
        set("REDIS_PWD", readOrDefault("G008_IT_REDIS_PWD", ""));
        loadApplicationSecrets();
    }

    /** 从仓库本地 `.env` 加载非基础设施应用密钥。 */
    private static void loadApplicationSecrets() {
        Path envFile = findEnvFile();
        Map<String, String> values;
        try {
            values = Files.readAllLines(envFile)
                .stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(LocalIntegrationEnvironment::parseEntry)
                .filter(entry -> LOCAL_SECRET_KEYS.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取本地集成测试环境文件: " + envFile, e);
        }
        for (String key : LOCAL_SECRET_KEYS) {
            if (System.getProperty(key) != null || System.getenv(key) != null) {
                continue;
            }
            String value = values.get(key);
            if (value == null) {
                throw new IllegalStateException("本地集成测试环境缺少配置项: " + key);
            }
            System.setProperty(key, value);
        }
    }

    /** 按 JVM 参数、进程环境变量顺序读取专用配置。 */
    private static String read(String key) {
        String value = System.getProperty(key);
        return value != null ? value : System.getenv(key);
    }

    /** 读取可选专用配置，缺失时使用本地默认值。 */
    private static String readOrDefault(String key, String defaultValue) {
        String value = read(key);
        return value == null ? defaultValue : value;
    }

    /** 将基础设施配置设置为 Spring 可解析的系统属性。 */
    private static void set(String key, String value) {
        System.setProperty(key, value);
    }

    /** 从当前 Maven 模块向上定位仓库根目录 `.env`。 */
    private static Path findEnvFile() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到仓库根目录 .env，请先运行 python3 scripts/init_local.py");
    }

    /** 按首个等号拆分 dotenv 条目，保留 Base64 与特殊字符。 */
    private static Map.Entry<String, String> parseEntry(String line) {
        int separator = line.indexOf('=');
        if (separator <= 0) {
            throw new IllegalStateException("本地集成测试环境文件包含无效配置行");
        }
        return Map.entry(line.substring(0, separator), line.substring(separator + 1));
    }
}
