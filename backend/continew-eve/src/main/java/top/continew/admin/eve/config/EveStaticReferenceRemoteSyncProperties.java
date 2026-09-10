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

package top.continew.admin.eve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * EVE 静态资料公开源的定时同步参数。
 *
 * <p>该资料不依赖角色授权，下载与解析失败时保留当前数据库快照。</p>
 *
 * @author zhaoyuqing
 */
@Data
@Component
@ConfigurationProperties(prefix = "eve.reference.remote-sync")
public class EveStaticReferenceRemoteSyncProperties {

    /** 是否启用每日公开资料同步。 */
    private boolean enabled = true;
    /** 可信公开资料下载地址，仅允许 HTTPS。 */
    private String sourceUrl = "https://www.ceve-market.org/dumps/evedata.xlsx";
    /** 每日调度表达式，默认中国时区凌晨 04:15。 */
    private String cron = "0 15 4 * * *";
    /** 调度时区。 */
    private String zone = "Asia/Shanghai";
    /** 下载连接与读取超时。 */
    private Duration timeout = Duration.ofMinutes(2);
    /** 单次下载的最大字节数，防止异常响应耗尽内存。 */
    private long maxDownloadBytes = 10 * 1024 * 1024;
}
