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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务线程池生产配置绑定测试。 */
class EveTaskSchedulingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(SchedulingConfiguration.class);

    /** 应用配置默认提供四个调度线程，避免市场全量任务阻塞其他同步。 */
    @Test
    void shouldBindDefaultSchedulingPoolSize() {
        runner.run(context -> assertThat(context.getBean(ThreadPoolTaskScheduler.class)
            .getScheduledThreadPoolExecutor()
            .getCorePoolSize()).isEqualTo(4));
    }

    /** 运维仍可使用标准 Spring 配置覆盖线程数。 */
    @Test
    void shouldAllowSchedulingPoolOverride() {
        runner.withPropertyValues("spring.task.scheduling.pool.size=6")
            .run(context -> assertThat(context.getBean(ThreadPoolTaskScheduler.class)
                .getScheduledThreadPoolExecutor()
                .getCorePoolSize()).isEqualTo(6));
    }

    /** 仅激活 Spring 定时任务基础设施，不加载业务或数据库 Bean。 */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {
    }
}
