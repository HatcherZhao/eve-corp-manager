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

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.continew.admin.common.constant.CacheConstants;
import top.continew.starter.cache.redisson.util.RedisUtils;

/** 应用启动后清理登录验证码开关缓存，使新增迁移立即生效，不改写管理员配置。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EveLoginCaptchaCacheInitializer implements ApplicationRunner {

    /** Liquibase 已先完成迁移；只失效当前应用的单个选项，不清理会话或共享 Redis。 */
    @Override
    public void run(ApplicationArguments args) {
        RedisUtils.delete(CacheConstants.OPTION_KEY_PREFIX + "LOGIN_CAPTCHA_ENABLED");
    }
}
