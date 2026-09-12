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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.time.LocalDateTime;

/**
 * 网易 EVE 国服官网资讯的全局本地快照。
 *
 * <p>公开资讯不属于任一军团，因此不使用租户字段；所有租户读取同一份已净化快照。</p>
 *
 * @author zhaoyuqing
 */
@Data
@TableName("eve_official_news")
@InterceptorIgnore(tenantLine = "true")
public class EveOfficialNewsDO extends BaseDO {

    /** 官网栏目编码。 */
    private String sourceCode;
    /** 官网栏目显示名称。 */
    private String sourceCategory;
    /** 资讯标题。 */
    private String title;
    /** 官网列表摘要。 */
    private String summary;
    /** 官网原文链接。 */
    private String originalUrl;
    /** 经过白名单净化的正文 HTML。 */
    private String contentHtml;
    /** 正文纯文本，供检索与无样式阅读使用。 */
    private String contentText;
    /** 首张正文图片的受控官网地址。 */
    private String coverUrl;
    /** 官网发布时间。 */
    private LocalDateTime publishedAt;
    /** 正文 SHA-256，用于识别官网修订。 */
    private String contentHash;
    /** 首次发现时间。 */
    private LocalDateTime firstSyncedAt;
    /** 最近一次列表同步时间。 */
    private LocalDateTime lastSyncedAt;
    /** 最近一次正文校验时间。 */
    private LocalDateTime lastContentCheckedAt;
}
