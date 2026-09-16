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

package top.continew.admin.eve.service;

import org.junit.jupiter.api.Test;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 网易 EVE 国服官网资讯 HTML 边界处理测试。
 *
 * @author zhaoyuqing
 */
class EveOfficialNewsServiceTest {

    /** 嵌套 div 的正文应完整截取，不能因内部排版块提前结束。 */
    @Test
    void shouldExtractNestedOfficialArticleContent() {
        String html = "<div class=\"other\">忽略</div><div class=\"artText\"><p>第一段</p><div><p>第二段</p></div></div><footer>忽略</footer>";

        assertThat(EveOfficialNewsService.findDivContent(html, "artText")).isEqualTo("<p>第一段</p><div><p>第二段</p></div>");
    }

    /** 正文净化只保留允许的 HTTPS 官网链接和图片，并清除脚本与事件属性。 */
    @Test
    void shouldSanitizeRemoteArticleHtml() {
        String rawHtml = "<p onclick=\"evil()\">公告<script>alert(1)</script><img src=\"https://nie.res.netease.com/eve/a.jpg\" onerror=\"evil()\"><a href=\"javascript:alert(1)\">危险链接</a><a href=\"https://evepc.163.com/news/a.html\">官网链接</a></p><iframe src=\"https://bad.example\"></iframe>";

        String safeHtml = EveOfficialNewsService.sanitizeHtml(rawHtml);

        assertThat(safeHtml)
            .contains("公告", "<img src=\"https://nie.res.netease.com/eve/a.jpg\" alt=\"官网资讯图片\">", "<a>危险链接</a>", "<a href=\"https://evepc.163.com/news/a.html\">官网链接</a>")
            .doesNotContain("script", "onclick", "onerror", "javascript:", "iframe");
    }

    /** 仅固定国服官网的新闻与版本路径可以成为抓取请求目标。 */
    @Test
    void shouldRejectNonOfficialArticleUri() {
        assertThat(EveOfficialNewsService
            .requireOfficialArticleUri("https://evepc.163.com/news/20260827/38679_1312460.html")
            .getHost()).isEqualTo("evepc.163.com");
        assertThatThrownBy(() -> EveOfficialNewsService.requireOfficialArticleUri("https://example.com/news/a.html"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> EveOfficialNewsService
            .requireOfficialArticleUri("https://evepc.163.com/account/login")).isInstanceOf(BusinessException.class);
    }

    /** 沙箱原页必须固定官网资源基址，并清除主动跳转、脚本和嵌入式内容。 */
    @Test
    void shouldRenderOfficialDocumentAsSafeSandboxContent() {
        String document = EveOfficialNewsService.renderOfficialDocument("<html><head><base href=\"https://bad.example\"><meta http-equiv=\"refresh\" content=\"0;url=https://bad.example\"><script>alert(1)</script></head><body><link rel=\"stylesheet\" href=\"css/article.css\"><p>正文</p><iframe src=\"https://bad.example\"></iframe></body></html>", "https://evepc.163.com/news/a.html");

        assertThat(document)
            .contains("<base href=\"https://evepc.163.com/news/a.html\">", "Content-Security-Policy", "css/article.css", "正文")
            .doesNotContain("bad.example", "<script", "<iframe", "http-equiv=\"refresh\"");
    }

    /** 官网图片代理只接受固定的网易 HTTPS 静态资源域名。 */
    @Test
    void shouldRejectNonOfficialImageUri() {
        assertThat(EveOfficialNewsService.requireOfficialImageUri("https://xz.res.netease.com/eve/cover.jpg").getHost())
            .isEqualTo("xz.res.netease.com");
        assertThatThrownBy(() -> EveOfficialNewsService.requireOfficialImageUri("https://example.com/cover.jpg"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> EveOfficialNewsService.requireOfficialImageUri("https://evepc.163.com:8443/cover.jpg"))
            .isInstanceOf(BusinessException.class);
    }

    /** 官网发布日没有时分秒时固定归一到当天零点，异常值不阻断同步。 */
    @Test
    void shouldParseOfficialPublishedDateSafely() {
        assertThat(EveOfficialNewsService.parsePublishedAt("2026-08-27")).isEqualTo(LocalDateTime
            .of(2026, 8, 27, 0, 0));
        assertThat(EveOfficialNewsService.parsePublishedAt("不是日期")).isNull();
    }

    /** 版本专题页中的图文卡片应同步为可阅读的版本条目，不能被新闻列表规则忽略。 */
    @Test
    void shouldParseOfficialVersionCards() {
        String html = "<div class=\"gallery_item tsslide\"><img src=\"https://xz.res.netease.com/eve/version.jpg\" alt=\"版本封面\"><a class=\"tbnLink\" href=\"yc121-12.html\" target=\"_blank\" title=\"YC121.12\"></a></div>";

        List<EveOfficialNewsService.ListItem> items = EveOfficialNewsService.parseVersionList(html);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("YC121.12");
            assertThat(item.url()).isEqualTo("https://evepc.163.com/updates/yc121-12.html");
            assertThat(item.coverUrl()).isEqualTo("https://xz.res.netease.com/eve/version.jpg");
        });
    }
}
