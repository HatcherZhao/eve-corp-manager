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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import top.continew.admin.eve.config.EveOfficialNewsProperties;
import top.continew.admin.eve.mapper.EveOfficialNewsMapper;
import top.continew.admin.eve.mapper.EveOfficialNewsSyncStateMapper;
import top.continew.admin.eve.model.EveOfficialNewsResp;
import top.continew.admin.eve.model.EveOfficialNewsSyncResp;
import top.continew.admin.eve.model.EveOfficialNewsSyncStatusResp;
import top.continew.admin.eve.model.entity.EveOfficialNewsDO;
import top.continew.admin.eve.model.entity.EveOfficialNewsSyncStateDO;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同步并提供网易 EVE 国服官网公开资讯。
 *
 * <p>官网没有公开资讯 API，当前页面为稳定服务端 HTML。服务只请求明确白名单的栏目与文章地址，
 * 并在入库前净化正文标签，避免将远程脚本或事件属性带入本站阅读页。</p>
 *
 * @author zhaoyuqing
 */
@Service
@Slf4j
public class EveOfficialNewsService {

    private static final String SYNC_LOCK = "eve:official-news:sync";
    private static final String OFFICIAL_HOST = "evepc.163.com";
    private static final Set<String> IMAGE_HOSTS = Set.of("evepc.163.com", "nie.res.netease.com", "xz.res.netease.com");
    private static final Pattern LIST_ITEM_PATTERN = Pattern
        .compile("(?is)<li\\b[^>]*>\\s*<a\\s+title=\\\"([^\\\"]+)\\\"\\s+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>\\s*</li>");
    private static final Pattern GENERIC_LINK_PATTERN = Pattern
        .compile("(?is)<a\\s+[^>]*title=\\\"([^\\\"]+)\\\"[^>]*href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>");
    private static final Pattern KIND_PATTERN = Pattern
        .compile("(?is)<span\\s+class=\\\"kindname\\\"[^>]*data-name=\\\"([^\\\"]+)\\\"");
    private static final Pattern SUMMARY_PATTERN = Pattern
        .compile("(?is)<span\\s+class=\\\"comment\\\"[^>]*>(.*?)</span>");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<h1\\s+class=\\\"artTitle\\\"[^>]*>(.*?)</h1>");
    private static final Pattern DATE_PATTERN = Pattern
        .compile("(?is)<span\\s+class=\\\"artDate\\\"[^>]*>([^<]+)</span>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<(/?)([a-z0-9]+)([^>]*)>");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern
        .compile("(?is)\\b%s\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern FIRST_IMAGE_PATTERN = Pattern.compile("(?is)<img\\s+src=\\\"([^\\\"]+)\\\"");
    private static final DateTimeFormatter PUBLISHED_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> SAFE_TAGS = Set
        .of("p", "br", "strong", "b", "em", "i", "u", "ul", "ol", "li", "table", "thead", "tbody", "tr", "td", "th", "a", "img", "blockquote", "h2", "h3", "h4");

    private final EveOfficialNewsProperties properties;
    private final EveOfficialNewsMapper newsMapper;
    private final EveOfficialNewsSyncStateMapper syncStateMapper;
    private final RedissonClient redissonClient;
    @Qualifier("eveOfficialNewsRestClient")
    private final RestClient restClient;

    /** 创建官网资讯服务，并明确使用受控官网访问客户端。 */
    public EveOfficialNewsService(EveOfficialNewsProperties properties,
                                  EveOfficialNewsMapper newsMapper,
                                  EveOfficialNewsSyncStateMapper syncStateMapper,
                                  RedissonClient redissonClient,
                                  @Qualifier("eveOfficialNewsRestClient") RestClient restClient) {
        this.properties = properties;
        this.newsMapper = newsMapper;
        this.syncStateMapper = syncStateMapper;
        this.redissonClient = redissonClient;
        this.restClient = restClient;
    }

    /** 分页查询官网新闻；版本更新以独立分类供二级菜单复用。 */
    public PageResp<EveOfficialNewsResp> page(int page, int size, String category, String keyword) {
        LambdaQueryWrapper<EveOfficialNewsDO> query = new LambdaQueryWrapper<EveOfficialNewsDO>()
            .eq(EveOfficialNewsDO::getDeleted, 0L)
            .orderByDesc(EveOfficialNewsDO::getPublishedAt)
            .orderByDesc(EveOfficialNewsDO::getId);
        if (category != null && !category.isBlank() && !"ALL".equals(category)) {
            query.eq(EveOfficialNewsDO::getSourceCode, category);
        } else {
            query.ne(EveOfficialNewsDO::getSourceCode, Source.VERSION.code);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(EveOfficialNewsDO::getTitle, value)
                .or()
                .like(EveOfficialNewsDO::getSummary, value)
                .or()
                .like(EveOfficialNewsDO::getContentText, value));
        }
        Page<EveOfficialNewsDO> result = newsMapper.selectPage(new Page<>(page, size), query);
        return new PageResp<>(result.getRecords().stream().map(item -> toResponse(item, false)).toList(), result
            .getTotal());
    }

    /**
     * 按官网原文地址读取一篇已入库资讯的完整快照。
     *
     * <p>列表不返回长正文，避免新闻数量增长后占用不必要的网络与浏览器内存；原文地址是官网公开地址，
     * 不向界面暴露本站数据库主键。</p>
     */
    public EveOfficialNewsResp detail(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new BusinessException("官网资讯地址不能为空");
        }
        EveOfficialNewsDO news = newsMapper.selectOne(new LambdaQueryWrapper<EveOfficialNewsDO>()
            .eq(EveOfficialNewsDO::getOriginalUrl, originalUrl.trim())
            .eq(EveOfficialNewsDO::getDeleted, 0L));
        if (news == null) {
            throw new BusinessException("官网资讯不存在或尚未同步");
        }
        return toResponse(news, true);
    }

    /** 返回任意资讯栏目最近一次同步状态，供阅读页简洁提示新鲜度。 */
    public EveOfficialNewsSyncStatusResp syncStatus() {
        List<EveOfficialNewsSyncStateDO> states = syncStateMapper.selectList(null);
        LocalDateTime lastSuccess = states.stream()
            .map(EveOfficialNewsSyncStateDO::getLastSuccessfulAt)
            .filter(item -> item != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
        EveOfficialNewsSyncStateDO latestFailure = states.stream()
            .filter(item -> item.getLastFailureAt() != null)
            .max(Comparator.comparing(EveOfficialNewsSyncStateDO::getLastFailureAt))
            .orElse(null);
        return new EveOfficialNewsSyncStatusResp(lastSuccess, latestFailure == null
            ? null
            : latestFailure.getLastFailureAt(), latestFailure == null
                ? null
                : latestFailure.getLastFailureMessage(), latestFailure == null || latestFailure
                    .getFailureCount() == null ? 0 : latestFailure.getFailureCount());
    }

    /** 执行一次全局资讯检查；调度和手动入口共享分布式锁，避免对官网并发重复请求。 */
    public EveOfficialNewsSyncResp synchronize() {
        if (!properties.isEnabled()) {
            return new EveOfficialNewsSyncResp(false, "官网资讯同步未启用", 0, utcNow());
        }
        RLock lock = redissonClient.getLock(SYNC_LOCK);
        if (!lock.tryLock()) {
            return new EveOfficialNewsSyncResp(false, "官网资讯正在同步，请稍后刷新", 0, utcNow());
        }
        try {
            LocalDateTime synchronizedAt = utcNow();
            int discovered = 0;
            Set<String> handledUrls = new HashSet<>();
            for (Source source : Source.values()) {
                discovered += synchronizeSource(source, synchronizedAt, handledUrls);
            }
            return new EveOfficialNewsSyncResp(true, discovered == 0
                ? "官网资讯已是最新"
                : "已同步 %d 条官网资讯".formatted(discovered), discovered, synchronizedAt);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 同步一个栏目首页；相同文章链接本轮只抓一次正文，仍会更新各栏目的健康状态。 */
    private int synchronizeSource(Source source, LocalDateTime synchronizedAt, Set<String> handledUrls) {
        try {
            List<ListItem> items = parseList(source, downloadHtml(source.listUrl));
            int discovered = 0;
            for (ListItem item : items) {
                if (!handledUrls.add(item.url)) {
                    continue;
                }
                if (upsert(source, item, synchronizedAt)) {
                    discovered++;
                }
            }
            saveSuccessState(source, synchronizedAt, discovered);
            return discovered;
        } catch (RuntimeException e) {
            saveFailureState(source, synchronizedAt, safeFailureMessage(e));
            log.warn("EVE 国服官网资讯同步失败，sourceCode={}, errorType={}", source.code, e.getClass().getSimpleName());
            return 0;
        }
    }

    /** 新文章或到达复核周期的文章才抓正文，减少重复请求并允许官网后续修订生效。 */
    private boolean upsert(Source source, ListItem item, LocalDateTime synchronizedAt) {
        EveOfficialNewsDO existing = newsMapper.selectOne(new LambdaQueryWrapper<EveOfficialNewsDO>()
            .eq(EveOfficialNewsDO::getOriginalUrl, item.url)
            .eq(EveOfficialNewsDO::getDeleted, 0L));
        boolean shouldFetchContent = existing == null || existing.getLastContentCheckedAt() == null || existing
            .getLastContentCheckedAt()
            .plus(properties.getContentRecheckInterval())
            .isBefore(synchronizedAt);
        ArticleDetail detail = shouldFetchContent ? parseDetail(item, downloadHtml(item.url)) : null;
        if (existing == null) {
            EveOfficialNewsDO created = new EveOfficialNewsDO();
            apply(created, source, item, detail, synchronizedAt);
            created.setFirstSyncedAt(synchronizedAt);
            created.setCreateUser(1L);
            created.setDeleted(0L);
            newsMapper.insert(created);
            return true;
        }
        String previousHash = existing.getContentHash();
        apply(existing, source, item, detail, synchronizedAt);
        newsMapper.updateById(existing);
        return detail != null && !java.util.Objects.equals(previousHash, existing.getContentHash());
    }

    /** 将列表元数据和可选正文快照写入实体；最新栏目不会覆盖文章更具体的原始栏目。 */
    private static void apply(EveOfficialNewsDO target,
                              Source source,
                              ListItem item,
                              ArticleDetail detail,
                              LocalDateTime synchronizedAt) {
        if (target.getSourceCode() == null || source != Source.LATEST) {
            target.setSourceCode(source.code);
            target.setSourceCategory(item.category == null || item.category.isBlank() ? source.label : item.category);
        }
        target.setTitle(detail == null || detail.title.isBlank() ? item.title : detail.title);
        target.setSummary(item.summary);
        target.setOriginalUrl(item.url);
        target.setLastSyncedAt(synchronizedAt);
        if (detail != null) {
            target.setContentHtml(detail.safeHtml);
            target.setContentText(detail.text);
            target.setCoverUrl(detail.coverUrl);
            target.setPublishedAt(detail.publishedAt);
            target.setContentHash(sha256(detail.safeHtml));
            target.setLastContentCheckedAt(synchronizedAt);
        }
    }

    /** 下载受控 HTTPS 页面并限制最大响应体，避免异常响应占满 JVM 内存。 */
    private String downloadHtml(String url) {
        URI uri = requireOfficialArticleUri(url);
        try {
            ResponseEntity<byte[]> response = restClient.get().uri(uri).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0 || body.length > properties.getMaxDocumentBytes() || response
                .getHeaders()
                .getContentLength() > properties.getMaxDocumentBytes()) {
                throw new BusinessException("官网资讯页面为空或超过大小限制");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            throw new BusinessException("请求官网资讯失败");
        }
    }

    /** 仅允许固定官网主机及新闻、版本路径，杜绝远程页面链接转为 SSRF 请求。 */
    static URI requireOfficialArticleUri(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl).normalize();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !OFFICIAL_HOST.equalsIgnoreCase(uri.getHost()) || !(path
                .startsWith("/news/") || path.startsWith("/updates/"))) {
                throw new IllegalArgumentException("not official news url");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("官网资讯地址无效");
        }
    }

    /** 从官网栏目 HTML 提取稳定列表项，并为页面结构小改保留受限的通用链接回退。 */
    private static List<ListItem> parseList(Source source, String html) {
        List<ListItem> items = parseListWithPattern(source, html, LIST_ITEM_PATTERN);
        return items.isEmpty() ? parseListWithPattern(source, html, GENERIC_LINK_PATTERN) : items;
    }

    /** 将符合官网文章地址规则的锚点转换为列表记录。 */
    private static List<ListItem> parseListWithPattern(Source source, String html, Pattern pattern) {
        List<ListItem> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String url = normalizeArticleUrl(source.listUrl, matcher.group(2));
            if (url == null || result.stream().anyMatch(item -> item.url.equals(url))) {
                continue;
            }
            String innerHtml = matcher.group(3);
            String title = normalizeText(matcher.group(1));
            if (title.isBlank()) {
                continue;
            }
            String category = extract(KIND_PATTERN, innerHtml);
            String summary = normalizeText(extract(SUMMARY_PATTERN, innerHtml));
            result.add(new ListItem(title, summary, category == null ? source.label : category, url));
        }
        return result;
    }

    /** 解析文章标题、发布时间、正文并将正文转为严格白名单 HTML。 */
    private static ArticleDetail parseDetail(ListItem item, String html) {
        String title = normalizeText(extract(TITLE_PATTERN, html));
        String date = normalizeText(extract(DATE_PATTERN, html));
        String content = findDivContent(html, "artText");
        if (content == null || content.isBlank()) {
            throw new BusinessException("官网资讯正文结构无法识别");
        }
        String safeHtml = sanitizeHtml(content);
        return new ArticleDetail(title.isBlank()
            ? item.title
            : title, parsePublishedAt(date), safeHtml, toPlainText(safeHtml), extract(FIRST_IMAGE_PATTERN, safeHtml));
    }

    /** 寻找指定 class 的 div 并按嵌套 div 深度定位结束位置，避免正则在表格正文中提前截断。 */
    static String findDivContent(String html, String className) {
        Pattern opening = Pattern.compile("(?is)<div\\b(?=[^>]*\\bclass=\\\"[^\\\"]*\\b" + Pattern
            .quote(className) + "\\b[^\\\"]*\\\")[^>]*>");
        Matcher first = opening.matcher(html);
        if (!first.find()) {
            return null;
        }
        int contentStart = first.end();
        Matcher div = Pattern.compile("(?is)</?div\\b[^>]*>").matcher(html);
        div.region(contentStart, html.length());
        int depth = 1;
        while (div.find()) {
            if (div.group().startsWith("</")) {
                depth--;
                if (depth == 0) {
                    return html.substring(contentStart, div.start());
                }
            } else {
                depth++;
            }
        }
        return null;
    }

    /** 将远程正文收敛至安全标签、白名单链接和图片地址，移除全部样式、脚本与事件属性。 */
    static String sanitizeHtml(String rawHtml) {
        String withoutDangerousBlocks = rawHtml
            .replaceAll("(?is)<(script|style|iframe|object|embed|form)[^>]*>.*?</\\1>", "");
        Matcher matcher = TAG_PATTERN.matcher(withoutDangerousBlocks);
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            result.append(withoutDangerousBlocks, cursor, matcher.start());
            String closing = matcher.group(1);
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            String attributes = matcher.group(3);
            if (SAFE_TAGS.contains(name)) {
                if (!closing.isEmpty()) {
                    result.append("</").append(name).append('>');
                } else if ("a".equals(name)) {
                    String href = allowedUrl(attribute(attributes, "href"), false);
                    if (href == null) {
                        result.append("<a>");
                    } else {
                        result.append("<a href=\"").append(href).append("\">");
                    }
                } else if ("img".equals(name)) {
                    String src = allowedUrl(attribute(attributes, "src"), true);
                    if (src != null) {
                        result.append("<img src=\"").append(src).append("\" alt=\"官网资讯图片\">");
                    }
                } else {
                    result.append('<').append(name).append('>');
                }
            }
            cursor = matcher.end();
        }
        result.append(withoutDangerousBlocks.substring(cursor));
        return result.toString().replaceAll("(?is)<!--.*?-->", "").trim();
    }

    /** 从单个 HTML 标签属性安全读取值。 */
    private static String attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile(ATTRIBUTE_PATTERN.pattern().formatted(name)).matcher(attributes);
        if (!matcher.find()) {
            return null;
        }
        for (int index = 1; index <= 3; index++) {
            if (matcher.group(index) != null) {
                return HtmlUtils.htmlUnescape(matcher.group(index));
            }
        }
        return null;
    }

    /** 校验文章链接或图片链接使用 HTTPS 且命中固定网易公开域名。 */
    private static String allowedUrl(String rawUrl, boolean image) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl).normalize();
            Set<String> hosts = image ? IMAGE_HOSTS : Set.of(OFFICIAL_HOST);
            return "https".equalsIgnoreCase(uri.getScheme()) && hosts.contains(uri.getHost()) ? uri.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 将相对官网文章地址补全为绝对地址并执行路径白名单校验。 */
    private static String normalizeArticleUrl(String listUrl, String rawUrl) {
        try {
            URI uri = URI.create(listUrl).resolve(rawUrl).normalize();
            requireOfficialArticleUri(uri.toString());
            return uri.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 从正则捕获组中提取第一段内容，不匹配时返回空串。 */
    private static String extract(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 将 HTML 片段转换为可检索、可降级展示的正文纯文本。 */
    private static String toPlainText(String html) {
        String formatted = html.replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)</(p|div|li|tr|h[2-4]|blockquote)>", "\n")
            .replaceAll("(?is)<[^>]+>", "");
        return normalizeText(formatted).replace(" ", " ");
    }

    /** 规范化 HTML 文本与实体，保留段落换行并限制无意义空白。 */
    private static String normalizeText(String source) {
        if (source == null) {
            return "";
        }
        return HtmlUtils.htmlUnescape(source.replaceAll("(?is)<[^>]+>", ""))
            .replace('\u00a0', ' ')
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    /** 解析官网 YYYY-MM-DD 日期；异常日期不阻断列表入库。 */
    static LocalDateTime parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), PUBLISHED_DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 对正文生成稳定哈希，用于辨别官网文章修订。 */
    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /** 记录一次成功检查，即使没有新文章也能让页面准确显示数据新鲜度。 */
    private void saveSuccessState(Source source, LocalDateTime checkedAt, int discovered) {
        EveOfficialNewsSyncStateDO state = syncStateMapper.selectById(source.code);
        if (state == null) {
            state = new EveOfficialNewsSyncStateDO();
            state.setSourceCode(source.code);
            state.setSourceUrl(source.listUrl);
            state.setLastCheckedAt(checkedAt);
            state.setLastSuccessfulAt(checkedAt);
            state.setFailureCount(0);
            state.setLastDiscoveredCount(discovered);
            syncStateMapper.insert(state);
            return;
        }
        state.setLastCheckedAt(checkedAt);
        state.setLastSuccessfulAt(checkedAt);
        state.setLastFailureAt(null);
        state.setLastFailureMessage(null);
        state.setFailureCount(0);
        state.setLastDiscoveredCount(discovered);
        syncStateMapper.updateById(state);
    }

    /** 记录受限长度的失败摘要，连续失败次数用于调度告警但不泄露远程正文。 */
    private void saveFailureState(Source source, LocalDateTime checkedAt, String message) {
        EveOfficialNewsSyncStateDO state = syncStateMapper.selectById(source.code);
        if (state == null) {
            state = new EveOfficialNewsSyncStateDO();
            state.setSourceCode(source.code);
            state.setSourceUrl(source.listUrl);
            state.setFailureCount(1);
            state.setLastDiscoveredCount(0);
            syncStateMapper.insert(state);
        } else {
            state.setFailureCount((state.getFailureCount() == null ? 0 : state.getFailureCount()) + 1);
        }
        state.setLastCheckedAt(checkedAt);
        state.setLastFailureAt(checkedAt);
        state.setLastFailureMessage(message);
        syncStateMapper.updateById(state);
    }

    /** 不把可能包含远程响应正文的异常消息写入数据库或日志。 */
    private static String safeFailureMessage(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return truncate(businessException.getMessage());
        }
        return "官网资讯同步出现未分类错误";
    }

    private static String truncate(String value) {
        return value == null ? "官网资讯同步失败" : value.substring(0, Math.min(value.length(), 500));
    }

    /** 转换为不含本地主键的接口记录；列表仅返回摘要，详情才返回净化后的完整正文。 */
    private static EveOfficialNewsResp toResponse(EveOfficialNewsDO source, boolean includeContent) {
        return new EveOfficialNewsResp(source.getSourceCode(), source.getSourceCategory(), source.getTitle(), source
            .getSummary(), source.getOriginalUrl(), includeContent ? source.getContentHtml() : null, includeContent
                ? source.getContentText()
                : null, source.getCoverUrl(), source.getPublishedAt(), source.getLastSyncedAt());
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 固定官网栏目，避免管理员配置任意抓取地址。 */
    private enum Source {
        NEWS("NEWS", "新闻", "https://evepc.163.com/news/official/"),
        MAINTENANCE("MAINTENANCE", "维护", "https://evepc.163.com/news/update/"),
        UPDATE_NOTICE("UPDATE_NOTICE", "更新通知", "https://evepc.163.com/news/meiti/"),
        VERSION("VERSION", "版本更新", "https://evepc.163.com/updates/"),
        LATEST("LATEST", "最新", "https://evepc.163.com/news/");

        private final String code;
        private final String label;
        private final String listUrl;

        Source(String code, String label, String listUrl) {
            this.code = code;
            this.label = label;
            this.listUrl = listUrl;
        }
    }

    /** 官网栏目页中的单篇新闻摘要。 */
    private record ListItem(String title, String summary, String category, String url) {
    }

    /** 官网原文页解析结果。 */
    private record ArticleDetail(String title, LocalDateTime publishedAt, String safeHtml, String text,
                                 String coverUrl) {
    }
}
