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

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.continew.admin.common.constant.CacheConstants;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.constant.StringConstants;

/**
 * 在 Liquibase 写入 EVE 菜单和角色授权后，使 Redis 中的角色菜单缓存重新加载。
 *
 * @author zhaoyuqing
 */
@Configuration
public class EveRoleMenuCacheInvalidator {

    /**
     * 启动完成后清理角色菜单缓存，避免数据迁移新增菜单后仍返回旧动态路由。
     *
     * @return 应用启动任务
     */
    @Bean
    ApplicationRunner eveRoleMenuCacheInvalidationRunner() {
        return args -> RedisUtils.deleteByPattern(CacheConstants.ROLE_MENU_KEY_PREFIX + StringConstants.ASTERISK);
    }
}
