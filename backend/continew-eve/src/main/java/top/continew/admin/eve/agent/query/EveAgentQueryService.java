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

package top.continew.admin.eve.agent.query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import top.continew.admin.eve.agent.EveAgentPrincipal;
import top.continew.admin.eve.agent.query.EveAgentDatasetRegistry.Dataset;
import top.continew.admin.eve.agent.query.EveAgentDatasetRegistry.Field;
import top.continew.admin.eve.agent.query.EveAgentDatasetRegistry.Type;
import top.continew.admin.eve.service.EveGameNotificationPresentationService;

/**
 * 外部 Agent 全量只读查询入口，统一执行数据集白名单、军团隔离、分页和输出脱敏。
 *
 * @author zhaoyuqing
 */
@Service
public class EveAgentQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FILTERS = 12;
    private static final int MAX_GROUP_FIELDS = 2;
    private static final int MAX_DOCUMENT_LIMIT = 20000;
    private static final int QUERY_TEXT_BUDGET = 65536;
    private static final String HIDDEN_NOTIFICATION_TYPE = "__notificationType";
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EveGameNotificationPresentationService notificationPresentationService;

    /**
     * 使用独立超时配置的 JDBC 模板创建查询服务。
     *
     * @param jdbcTemplate                    Agent 查询专用模板
     * @param notificationPresentationService 游戏通知业务解读服务
     */
    public EveAgentQueryService(@Qualifier("eveAgentQueryJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
                                EveGameNotificationPresentationService notificationPresentationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationPresentationService = notificationPresentationService;
    }

    /**
     * 返回 Agent 可用数据集、字段类型和能力目录。
     *
     * @param principal 已认证军团身份
     * @return 查询目录
     */
    public Map<String, Object> catalog(EveAgentPrincipal principal) {
        requirePrincipal(principal);
        List<Map<String, Object>> datasets = EveAgentDatasetRegistry.all().values().stream().map(dataset -> {
            List<Map<String, Object>> fields = dataset.fields().values().stream().map(field -> {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("name", field.name());
                metadata.put("description", field.name());
                metadata.put("type", field.type().name().toLowerCase(Locale.ROOT));
                metadata.put("searchable", field.searchable());
                metadata.put("sortable", field.sortable());
                metadata.put("aggregatable", field.aggregatable());
                metadata.put("groupable", field.groupable());
                if (field.type() == Type.TIME) {
                    metadata.put("storageTimeBasis", field.timeBasis().name().toLowerCase(Locale.ROOT));
                    metadata.put("outputTimezone", "Asia/Shanghai");
                }
                if (field.type() == Type.DECIMAL) {
                    metadata.put("serialization", "decimal_string");
                }
                return metadata;
            }).toList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", dataset.key());
            item.put("title", dataset.title());
            item.put("scope", dataset.scope().name().toLowerCase(Locale.ROOT));
            item.put("fields", fields);
            item.put("documentReadable", dataset.documentIdExpression() != null || "news".equals(dataset.key()));
            if (dataset.documentIdField() != null) {
                item.put("documentIdField", dataset.documentIdField());
                item.put("documentCharacterIdRequired", List.of("mail", "notifications").contains(dataset.key()));
            }
            if ("notifications".equals(dataset.key())) {
                item.put("scopeSemantics", "当前军团仅表示通知接收角色与授权来源范围，不代表事件建筑归属于该军团");
                item.put("computedResponseFields", List.of(Map
                    .of("name", "title", "description", "通知类型对应的中文标题", "availableIn", "query"), Map
                        .of("name", "eventScope", "description", "事件业务范围，不代表建筑所有者归属", "availableIn", "query"), Map
                            .of("name", "eventScopeLabel", "description", "事件业务范围中文名称", "availableIn", "query"), Map
                                .of("name", "interpretation", "description", "基于同一条已授权通知正文生成的结构化业务解读", "availableIn", "readDocument"), Map
                                    .of("name", "interpretationComplete", "description", "业务解读是否覆盖完整通知正文", "availableIn", "readDocument")));
            }
            return item;
        }).toList();
        return Map.of("application", principal
            .applicationName(), "readOnly", true, "datasets", datasets, "filterOperators", List
                .of("eq", "ne", "contains", "in", "gte", "gt", "lte", "lt", "is_null"));
    }

    /**
     * 对白名单数据集执行军团隔离后的分页查询。
     *
     * @param principal 已认证军团身份
     * @param request   查询请求
     * @return 分页结果
     */
    public Map<String, Object> query(EveAgentPrincipal principal, Map<String, Object> request) {
        requirePrincipal(principal);
        Dataset dataset = EveAgentDatasetRegistry.require(requiredString(request, "dataset"));
        QueryParts parts = buildQueryParts(principal, dataset, request, false);
        int page = positiveInt(request.get("page"), 1, "page");
        int size = boundedInt(request.get("size"), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE, "size");
        int textOffset = boundedInt(request.get("textOffset"), 0, 0, Integer.MAX_VALUE, "textOffset");
        int requestedTextLimit = boundedInt(request.get("textLimit"), 2000, 1, MAX_DOCUMENT_LIMIT, "textLimit");
        long textFieldCount = parts.fields()
            .values()
            .stream()
            .filter(field -> field.type() == Type.STRING || field.type() == Type.JSON)
            .count();
        int budgetLimit = textFieldCount == 0
            ? requestedTextLimit
            : Math.max(1, QUERY_TEXT_BUDGET / Math.toIntExact((long)size * textFieldCount));
        int textLimit = Math.min(requestedTextLimit, budgetLimit);
        String countSql = "SELECT COUNT(*) FROM " + dataset.from() + " WHERE " + parts.where();
        Long totalValue = jdbcTemplate.queryForObject(countSql, parts.params(), Long.class);
        long total = totalValue == null ? 0 : totalValue;
        parts.params()
            .addValue("offset", (long)(page - 1) * size)
            .addValue("limit", size)
            .addValue("textStart", (long)textOffset + 1)
            .addValue("textLimit", textLimit);
        String selectedColumns = projection(parts.fields());
        if ("notifications".equals(dataset.key()) && !parts.fields().containsKey("notificationType")) {
            selectedColumns += ", n.notification_type AS `" + HIDDEN_NOTIFICATION_TYPE + "`";
        }
        String sql = "SELECT " + selectedColumns + " FROM " + dataset.from() + " WHERE " + parts
            .where() + " ORDER BY " + parts.orderBy() + " LIMIT :limit OFFSET :offset";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parts.params());
        List<Map<String, Object>> items = normalizeRows(rows, parts.fields(), textOffset, textLimit);
        if ("notifications".equals(dataset.key())) {
            enrichNotificationRows(rows, items);
        }
        Map<String, Object> result = pageResult(dataset, page, size, total, items, hasConstraints(request));
        result.put("textOffset", textOffset);
        result.put("textLimit", textLimit);
        result.put("requestedTextLimit", requestedTextLimit);
        result.put("textBudget", QUERY_TEXT_BUDGET);
        return result;
    }

    /**
     * 对白名单字段执行军团隔离后的数据库聚合，不在内存中汇总。
     *
     * @param principal 已认证军团身份
     * @param request   聚合请求
     * @return 聚合分页结果
     */
    public Map<String, Object> aggregate(EveAgentPrincipal principal, Map<String, Object> request) {
        requirePrincipal(principal);
        Dataset dataset = EveAgentDatasetRegistry.require(requiredString(request, "dataset"));
        QueryParts parts = buildQueryParts(principal, dataset, request, true);
        List<String> groupNames = stringList(request.get("groupBy"), "groupBy");
        if (groupNames.size() > MAX_GROUP_FIELDS) {
            throw new IllegalArgumentException("groupBy 最多允许 2 个字段");
        }
        List<Field> groups = groupNames.stream().map(name -> {
            Field field = requireField(dataset, name);
            if (!field.groupable()) {
                throw new IllegalArgumentException("字段不允许分组: " + name);
            }
            return field;
        }).toList();
        List<Map<String, Object>> metrics = mapList(request.get("metrics"), "metrics");
        if (metrics.isEmpty()) {
            throw new IllegalArgumentException("metrics 不能为空");
        }
        List<AggregateColumn> aggregateColumns = parseMetrics(dataset, metrics, groupNames);
        List<String> selects = new ArrayList<>();
        groups.forEach(field -> selects.add(field.expression() + " AS `" + field.name() + "`"));
        aggregateColumns.forEach(column -> selects.add(column.expression() + " AS `" + column.alias() + "`"));
        String groupSql = groups.isEmpty()
            ? ""
            : " GROUP BY " + groups.stream().map(Field::expression).reduce((a, b) -> a + ", " + b).orElse("");
        String base = " FROM " + dataset.from() + " WHERE " + parts.where() + groupSql;
        long total;
        if (groups.isEmpty()) {
            // 无分组聚合即使源数据为空也会固定返回一行聚合结果。
            total = 1;
        } else {
            Long totalValue = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM (SELECT 1" + base + ") agent_aggregate", parts
                    .params(), Long.class);
            total = totalValue == null ? 0 : totalValue;
        }
        int page = positiveInt(request.get("page"), 1, "page");
        int size = boundedInt(request.get("size"), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE, "size");
        parts.params().addValue("offset", (long)(page - 1) * size).addValue("limit", size);
        String orderBy = aggregateOrder(request.get("sort"), groups, aggregateColumns);
        String sql = "SELECT " + String
            .join(", ", selects) + base + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset";
        Map<String, Field> outputFields = new LinkedHashMap<>();
        groups.forEach(field -> outputFields.put(field.name(), field));
        aggregateColumns.forEach(column -> outputFields.put(column.alias(), new Field(column.alias(), column
            .expression(), column.type(), false, true, false, false, column.timeBasis())));
        List<Map<String, Object>> items = normalizeRows(jdbcTemplate.queryForList(sql, parts.params()), outputFields);
        return pageResult(dataset, page, size, total, items, hasConstraints(request));
    }

    /**
     * 从本地缓存切片读取邮件、通知或资讯正文，不访问上游且不产生已读状态写入。
     *
     * @param principal 已认证军团身份
     * @param request   正文读取请求
     * @return 正文切片
     */
    public Map<String, Object> readDocument(EveAgentPrincipal principal, Map<String, Object> request) {
        requirePrincipal(principal);
        String datasetKey = requiredString(request, "dataset");
        long id = positiveLong(request.get("id"), "id");
        int offset = boundedInt(request.get("offset"), 0, 0, Integer.MAX_VALUE, "offset");
        int limit = boundedInt(request.get("limit"), 4000, 1, MAX_DOCUMENT_LIMIT, "limit");
        Dataset dataset = EveAgentDatasetRegistry.require(datasetKey);
        DocumentSql documentSql = documentSql(dataset, principal);
        MapSqlParameterSource params = scopeParams(principal).addValue("documentId", id);
        if (List.of("mail", "notifications").contains(datasetKey)) {
            params.addValue("characterId", positiveLong(request.get("characterId"), "characterId"));
        }
        String content;
        int totalLength = 0;
        int returnedLength = 0;
        String missingReason = null;
        Map<String, Object> documentRow = null;
        try {
            params.addValue("documentStart", (long)offset + 1).addValue("documentLimit", limit);
            Map<String, Object> row = jdbcTemplate.queryForMap(documentSql.sql(), params);
            documentRow = row;
            content = row.get("content") == null ? null : row.get("content").toString();
            totalLength = row.get("totalLength") == null ? 0 : ((Number)row.get("totalLength")).intValue();
            returnedLength = row.get("returnedLength") == null ? 0 : ((Number)row.get("returnedLength")).intValue();
            if (content == null && row.get("totalLength") == null) {
                content = "";
                missingReason = "content_not_cached";
            }
        } catch (EmptyResultDataAccessException e) {
            content = "";
            missingReason = "document_not_found_or_not_authorized";
        }
        int safeOffset = Math.min(offset, totalLength);
        int end = Math.min(totalLength, safeOffset + returnedLength);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", datasetKey);
        result.put("id", String.valueOf(id));
        result.put("content", content);
        result.put("offset", safeOffset);
        result.put("limit", limit);
        result.put("returnedLength", returnedLength);
        result.put("nextOffset", end);
        result.put("totalLength", totalLength);
        result.put("hasMore", end < totalLength);
        result.put("source", "local_cache");
        result.put("contentAvailable", missingReason == null);
        result.put("missingReason", missingReason);
        if ("notifications".equals(datasetKey) && missingReason == null && documentRow != null) {
            String interpretationContent = Objects.toString(documentRow.get("interpretationContent"), "");
            String notificationType = Objects.toString(documentRow.get("notificationType"), "");
            result.put("interpretation", notificationPresentationService
                .interpret(notificationType, interpretationContent));
            result.put("interpretationComplete", totalLength <= MAX_DOCUMENT_LIMIT);
        }
        return result;
    }

    /** 为通知查询结果补充不可筛选、排序或聚合的业务展示字段。 */
    private void enrichNotificationRows(List<Map<String, Object>> rows, List<Map<String, Object>> items) {
        for (int index = 0; index < items.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String type = Objects.toString(row.getOrDefault("notificationType", row.get(HIDDEN_NOTIFICATION_TYPE)), "");
            Map<String, Object> item = items.get(index);
            item.put("title", EveGameNotificationPresentationService.titleOf(type));
            item.put("eventScope", EveGameNotificationPresentationService.eventScopeOf(type));
            item.put("eventScopeLabel", EveGameNotificationPresentationService.eventScopeLabelOf(type));
        }
    }

    /** 在服务端身份范围内组合参数化筛选、搜索、投影和排序。 */
    private QueryParts buildQueryParts(EveAgentPrincipal principal,
                                       Dataset dataset,
                                       Map<String, Object> request,
                                       boolean aggregate) {
        MapSqlParameterSource params = scopeParams(principal);
        List<String> conditions = new ArrayList<>();
        conditions.add(dataset.where());
        String keyword = optionalString(request.get("keyword"));
        if (keyword != null) {
            List<Field> searchable = dataset.fields().values().stream().filter(Field::searchable).toList();
            if (searchable.isEmpty()) {
                throw new IllegalArgumentException("该数据集不支持 keyword 搜索");
            }
            params.addValue("keyword", "%" + keyword + "%");
            conditions.add("(" + searchable.stream()
                .map(field -> searchExpression(dataset, field) + " LIKE :keyword")
                .reduce((a, b) -> a + " OR " + b)
                .orElseThrow() + ")");
        }
        List<Map<String, Object>> filters = mapList(request.get("filters"), "filters");
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("filters 最多允许 12 个条件");
        }
        for (int i = 0; i < filters.size(); i++) {
            conditions.add(filterCondition(dataset, filters.get(i), i, params));
        }
        Map<String, Field> selected = aggregate ? dataset.fields() : selectFields(dataset, request.get("fields"));
        String orderBy = aggregate ? dataset.defaultSort() : queryOrder(request.get("sort"), dataset);
        return new QueryParts(String.join(" AND ", conditions), params, selected, orderBy);
    }

    /** 校验字段及操作符后生成单项筛选，筛选值只通过 JDBC 参数传递。 */
    private String filterCondition(Dataset dataset,
                                   Map<String, Object> filter,
                                   int index,
                                   MapSqlParameterSource params) {
        Field field = requireField(dataset, requiredString(filter, "field"));
        String operator = requiredString(filter, "operator").toLowerCase(Locale.ROOT);
        String parameter = "filter" + index;
        return switch (operator) {
            case "eq", "ne", "gte", "gt", "lte", "lt" -> {
                Object value = typedValue(field, filter.get("value"));
                params.addValue(parameter, value);
                String sqlOperator = Map.of("eq", "=", "ne", "<>", "gte", ">=", "gt", ">", "lte", "<=", "lt", "<")
                    .get(operator);
                yield field.expression() + " " + sqlOperator + " :" + parameter;
            }
            case "contains" -> {
                if (field.type() != Type.STRING) {
                    throw new IllegalArgumentException("contains 仅支持字符串字段");
                }
                params.addValue(parameter, "%" + requiredValueString(filter.get("value"), "value") + "%");
                yield searchExpression(dataset, field) + " LIKE :" + parameter;
            }
            case "in" -> {
                if (!(filter.get("value") instanceof Collection<?> values) || values.isEmpty() || values.size() > 100) {
                    throw new IllegalArgumentException("in 的 value 必须是 1 到 100 个值的数组");
                }
                List<Object> typed = values.stream().map(value -> typedValue(field, value)).toList();
                params.addValue(parameter, typed);
                yield field.expression() + " IN (:" + parameter + ")";
            }
            case "is_null" -> field.expression() + (booleanValue(filter.get("value"), true)
                ? " IS NULL"
                : " IS NOT NULL");
            default -> throw new IllegalArgumentException("不支持的筛选操作符: " + operator);
        };
    }

    /** 校验聚合函数、字段能力及别名冲突，保留时间字段的存储基准。 */
    private List<AggregateColumn> parseMetrics(Dataset dataset,
                                               List<Map<String, Object>> metrics,
                                               List<String> groupNames) {
        if (metrics.size() > 12) {
            throw new IllegalArgumentException("metrics 最多允许 12 项");
        }
        List<AggregateColumn> result = new ArrayList<>();
        Set<String> usedAliases = new HashSet<>();
        groupNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).forEach(usedAliases::add);
        for (Map<String, Object> metric : metrics) {
            String function = requiredString(metric, "function").toLowerCase(Locale.ROOT);
            if (!List.of("count", "sum", "avg", "min", "max").contains(function)) {
                throw new IllegalArgumentException("不支持的聚合函数: " + function);
            }
            String alias = requiredString(metric, "alias");
            if (!ALIAS_PATTERN.matcher(alias).matches()) {
                throw new IllegalArgumentException("聚合别名不合法: " + alias);
            }
            if (!usedAliases.add(alias.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("聚合别名重复或与分组字段冲突: " + alias);
            }
            Field field = null;
            if (!"count".equals(function) || metric.get("field") != null) {
                field = requireField(dataset, requiredString(metric, "field"));
                if (!field.aggregatable()) {
                    throw new IllegalArgumentException("字段不允许聚合: " + field.name());
                }
            }
            if (List.of("sum", "avg").contains(function) && field != null && !List.of(Type.NUMBER, Type.DECIMAL)
                .contains(field.type())) {
                throw new IllegalArgumentException(function + " 仅支持数值字段");
            }
            String expression = function.toUpperCase(Locale.ROOT) + "(" + (field == null
                ? "*"
                : field.expression()) + ")";
            Type outputType = "count".equals(function) ? Type.NUMBER : field.type();
            EveAgentDatasetRegistry.TimeBasis timeBasis = field == null
                ? EveAgentDatasetRegistry.TimeBasis.UTC
                : field.timeBasis();
            result.add(new AggregateColumn(alias, expression, outputType, timeBasis));
        }
        return result;
    }

    /** 解析白名单排序并补充唯一键，保证相同排序值的稳定顺序。 */
    private String queryOrder(Object value, Dataset dataset) {
        List<Map<String, Object>> sorts = mapList(value, "sort");
        if (sorts.isEmpty()) {
            return resolveDefaultSort(dataset);
        }
        if (sorts.size() > 4) {
            throw new IllegalArgumentException("sort 最多允许 4 项");
        }
        List<String> requestedNames = sorts.stream().map(sort -> requiredString(sort, "field")).toList();
        String requestedOrder = sorts.stream().map(sort -> {
            Field field = requireField(dataset, requiredString(sort, "field"));
            String direction = direction(sort.get("direction"));
            return field.expression() + " " + direction;
        }).reduce((a, b) -> a + ", " + b).orElseThrow();
        return appendStableOrder(requestedOrder, dataset, requestedNames);
    }

    /** 正文搜索使用完整字段，不能仅在返回给客户端的预览中查找。 */
    private String searchExpression(Dataset dataset, Field field) {
        if ("mail".equals(dataset.key()) && "bodyPreview".equals(field.name())) {
            return "m.body";
        }
        if ("notifications".equals(dataset.key()) && "contentPreview".equals(field.name())) {
            return "n.content";
        }
        if ("news".equals(dataset.key()) && "contentPreview".equals(field.name())) {
            return "n.content_text";
        }
        return field.expression();
    }

    /** 聚合排序补齐全部分组字段，避免同值分组在分页时随机换位。 */
    private String aggregateOrder(Object value, List<Field> groups, List<AggregateColumn> columns) {
        List<Map<String, Object>> sorts = mapList(value, "sort");
        List<String> orders = new ArrayList<>();
        List<String> requestedNames = new ArrayList<>();
        sorts.forEach(sort -> {
            String name = requiredString(sort, "field");
            Field group = groups.stream().filter(field -> field.name().equals(name)).findFirst().orElse(null);
            AggregateColumn column = columns.stream()
                .filter(item -> item.alias().equals(name))
                .findFirst()
                .orElse(null);
            if (group == null && column == null) {
                throw new IllegalArgumentException("聚合排序字段不合法: " + name);
            }
            orders.add((group == null ? "`" + column.alias() + "`" : group.expression()) + " " + direction(sort
                .get("direction")));
            requestedNames.add(name);
        });
        groups.stream()
            .filter(group -> !requestedNames.contains(group.name()))
            .forEach(group -> orders.add(group.expression() + " ASC"));
        return orders.isEmpty() ? "1 ASC" : String.join(", ", orders);
    }

    /** 将注册表默认排序字段解析为受信 SQL 表达式。 */
    private String resolveDefaultSort(Dataset dataset) {
        String[] parts = dataset.defaultSort().split(" ");
        Field field = requireField(dataset, parts[0]);
        String order = field.expression() + (parts.length > 1 ? " " + direction(parts[1]) : " ASC");
        return appendStableOrder(order, dataset, List.of(parts[0]));
    }

    /** 为普通及复合主键数据集追加尚未出现在排序中的稳定键。 */
    private String appendStableOrder(String order, Dataset dataset, List<String> existingFields) {
        List<String> stableFields;
        if ("static_locations".equals(dataset.key())) {
            stableFields = List.of("referenceType", "referenceId");
        } else if ("permission_summary".equals(dataset.key())) {
            stableFields = List.of("roleId", "menuId");
        } else if (dataset.fields().containsKey("id")) {
            stableFields = List.of("id");
        } else {
            stableFields = List
                .of("typeId", "systemId", "stargateId", "rawTypeId", "referenceId", "mailId", "notificationId")
                .stream()
                .filter(dataset.fields()::containsKey)
                .limit(1)
                .toList();
        }
        String result = order;
        for (String fieldName : stableFields) {
            if (!existingFields.contains(fieldName)) {
                result += ", " + requireField(dataset, fieldName).expression() + " ASC";
            }
        }
        return result;
    }

    /** 只允许投影数据目录中已公开的字段。 */
    private Map<String, Field> selectFields(Dataset dataset, Object requestedFields) {
        List<String> names = stringList(requestedFields, "fields");
        if (names.isEmpty()) {
            return dataset.fields();
        }
        Map<String, Field> selected = new LinkedHashMap<>();
        names.forEach(name -> selected.put(name, requireField(dataset, name)));
        return selected;
    }

    /** 文本和 JSON 在数据库中切片，同时查询真实长度以便继续读取。 */
    private String projection(Map<String, Field> fields) {
        return fields.values().stream().map(field -> {
            if (field.type() == Type.STRING || field.type() == Type.JSON) {
                return "SUBSTRING(CAST(" + field.expression() + " AS CHAR), :textStart, :textLimit) AS `" + field
                    .name() + "`, CHAR_LENGTH(CAST(" + field.expression() + " AS CHAR)) AS `__len_" + field
                        .name() + "`, CHAR_LENGTH(SUBSTRING(CAST(" + field
                            .expression() + " AS CHAR), :textStart, :textLimit)) AS `__returned_" + field.name() + "`";
            }
            return field.expression() + " AS `" + field.name() + "`";
        }).reduce((a, b) -> a + ", " + b).orElseThrow();
    }

    /** 规范化不含文本切片元数据的聚合结果。 */
    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows, Map<String, Field> fields) {
        return normalizeRows(rows, fields, 0, 0);
    }

    /** 按公开字段转换结果，并为长文本补充字符级续读位置。 */
    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows,
                                                    Map<String, Field> fields,
                                                    int textOffset,
                                                    int textLimit) {
        return rows.stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            fields.forEach((name, field) -> normalized.put(name, normalize(row.get(name), field)));
            Map<String, Object> textFields = new LinkedHashMap<>();
            fields.values()
                .stream()
                .filter(field -> field.type() == Type.STRING || field.type() == Type.JSON)
                .forEach(field -> {
                    Object lengthValue = row.get("__len_" + field.name());
                    if (lengthValue instanceof Number number) {
                        int totalLength = number.intValue();
                        Object returnedValue = row.get("__returned_" + field.name());
                        int returnedLength = returnedValue instanceof Number returnedNumber
                            ? returnedNumber.intValue()
                            : 0;
                        textFields.put(field.name(), Map
                            .of("offset", textOffset, "limit", textLimit, "returnedLength", returnedLength, "nextOffset", (long)textOffset + returnedLength, "totalLength", totalLength, "hasMore", (long)textOffset + returnedLength < totalLength));
                    }
                });
            if (!textFields.isEmpty()) {
                normalized.put("textFields", textFields);
            }
            return normalized;
        }).toList();
    }

    /** 大整数和十进制值转字符串，时间统一输出带偏移的北京时间。 */
    private Object normalize(Object value, Field field) {
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case ID, DECIMAL, DATE -> value.toString();
            case BOOLEAN -> value instanceof Boolean bool ? bool : ((Number)value).intValue() != 0;
            case TIME -> formatTime(value, field.timeBasis());
            default -> value;
        };
    }

    /** 根据字段存储基准解释无时区数据库值，避免重复增加八小时。 */
    private String formatTime(Object value, EveAgentDatasetRegistry.TimeBasis timeBasis) {
        LocalDateTime localDateTime;
        if (value instanceof Timestamp timestamp) {
            localDateTime = timestamp.toLocalDateTime();
        } else if (value instanceof LocalDateTime dateTime) {
            localDateTime = dateTime;
        } else {
            return value.toString();
        }
        ZoneId sourceZone = timeBasis == EveAgentDatasetRegistry.TimeBasis.UTC ? ZoneOffset.UTC : BEIJING_ZONE;
        return TIME_FORMATTER.format(localDateTime.atZone(sourceZone).withZoneSameInstant(BEIJING_ZONE));
    }

    /** 按字段类型严格解析筛选值，不截断小数或溢出整数。 */
    private Object typedValue(Field field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("筛选值不能为空");
        }
        try {
            return switch (field.type()) {
                case ID, NUMBER -> new BigDecimal(value.toString()).longValueExact();
                case DECIMAL -> new BigDecimal(value.toString());
                case BOOLEAN -> booleanValue(value, false);
                case DATE -> LocalDate.parse(value.toString());
                case TIME -> parseTimeFilter(value, field.timeBasis());
                case STRING, JSON -> value.toString();
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("字段 " + field.name() + " 的筛选值类型不正确", e);
        }
    }

    /** 构造只读正文切片 SQL，沿用数据集身份与授权条件。 */
    private DocumentSql documentSql(Dataset dataset, EveAgentPrincipal principal) {
        if ("mail".equals(dataset.key())) {
            return new DocumentSql("SELECT SUBSTRING(m.body,:documentStart,:documentLimit) AS content, CHAR_LENGTH(m.body) AS totalLength, CHAR_LENGTH(SUBSTRING(m.body,:documentStart,:documentLimit)) AS returnedLength FROM " + dataset
                .from() + " WHERE " + dataset.where() + " AND m.character_id=:characterId AND m.mail_id=:documentId");
        }
        if ("notifications".equals(dataset.key())) {
            return new DocumentSql("SELECT SUBSTRING(n.content,:documentStart,:documentLimit) AS content, CHAR_LENGTH(n.content) AS totalLength, CHAR_LENGTH(SUBSTRING(n.content,:documentStart,:documentLimit)) AS returnedLength, LEFT(n.content," + MAX_DOCUMENT_LIMIT + ") AS interpretationContent, n.notification_type AS notificationType FROM " + dataset
                .from() + " WHERE " + dataset
                    .where() + " AND n.character_id=:characterId AND n.notification_id=:documentId");
        }
        if ("news".equals(dataset.key())) {
            return new DocumentSql("SELECT SUBSTRING(n.content_text,:documentStart,:documentLimit) AS content, CHAR_LENGTH(n.content_text) AS totalLength, CHAR_LENGTH(SUBSTRING(n.content_text,:documentStart,:documentLimit)) AS returnedLength FROM eve_official_news n WHERE n.id=:documentId AND n.deleted=0");
        }
        throw new IllegalArgumentException("该数据集不支持正文读取: " + dataset.key());
    }

    /** 返回分页、查询时刻和缓存来源；查询时刻不是快照刷新时间。 */
    private Map<String, Object> pageResult(Dataset dataset,
                                           int page,
                                           int size,
                                           long total,
                                           List<Map<String, Object>> items,
                                           boolean constrained) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", dataset.key());
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("items", items);
        result.put("hasMore", (long)page * size < total);
        result.put("source", "local_database_cache");
        result.put("cacheOnly", true);
        result.put("queriedAt", TIME_FORMATTER.format(OffsetDateTime.now(BEIJING_ZONE)));
        result.put("cacheMissingReason", total == 0 && !constrained ? "当前授权军团尚无本地缓存记录" : null);
        return result;
    }

    /** 区分未筛选缓存为空与带条件查询没有匹配记录。 */
    private boolean hasConstraints(Map<String, Object> request) {
        return optionalString(request.get("keyword")) != null || !mapList(request.get("filters"), "filters").isEmpty();
    }

    /** 将带时区筛选时间转换成目标字段的数据库存储基准。 */
    private LocalDateTime parseTimeFilter(Object value, EveAgentDatasetRegistry.TimeBasis timeBasis) {
        OffsetDateTime input = OffsetDateTime.parse(value.toString());
        ZoneId storageZone = timeBasis == EveAgentDatasetRegistry.TimeBasis.UTC ? ZoneOffset.UTC : BEIJING_ZONE;
        return LocalDateTime.ofInstant(input.toInstant(), storageZone);
    }

    /** 身份筛选参数仅从已认证应用取得。 */
    private MapSqlParameterSource scopeParams(EveAgentPrincipal principal) {
        return new MapSqlParameterSource().addValue("tenantId", principal.tenantId())
            .addValue("corporationRefId", principal.corporationRefId())
            .addValue("corporationId", principal.corporationId());
    }

    /** 拒绝缺失或不完整的军团身份。 */
    private void requirePrincipal(EveAgentPrincipal principal) {
        if (principal == null || principal.tenantId() == null || principal.corporationRefId() == null || principal
            .corporationId() == null) {
            throw new IllegalArgumentException("外部 Agent 军团身份无效");
        }
    }

    /** 拒绝目录之外的字段名，防止客户端注入 SQL 结构。 */
    private Field requireField(Dataset dataset, String name) {
        Field field = dataset.fields().get(name);
        if (field == null) {
            throw new IllegalArgumentException("数据集 " + dataset.key() + " 不支持字段: " + name);
        }
        return field;
    }

    /** 读取必填字符串参数。 */
    private String requiredString(Map<String, Object> values, String key) {
        return requiredValueString(values == null ? null : values.get(key), key);
    }

    /** 校验必填字符串不为空且长度受限。 */
    private String requiredValueString(Object value, String key) {
        String string = optionalString(value);
        if (string == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return string;
    }

    /** 规范化可选文本条件并限制长度。 */
    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String string = value.toString().trim();
        return string.isEmpty() ? null : string;
    }

    /** 解析正整数分页参数。 */
    private int positiveInt(Object value, int defaultValue, String name) {
        return boundedInt(value, defaultValue, 1, Integer.MAX_VALUE, name);
    }

    /** 严格解析带上下限的整数，拒绝截断和溢出。 */
    private int boundedInt(Object value, int defaultValue, int min, int max, String name) {
        int parsed = value == null ? defaultValue : Integer.parseInt(value.toString());
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return parsed;
    }

    /** 解析正整数业务 ID。 */
    private long positiveLong(Object value, String name) {
        long parsed = Long.parseLong(Objects.toString(value, ""));
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " 必须是正整数");
        }
        return parsed;
    }

    /** 只接受布尔值或明确的布尔文本。 */
    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.toString()) || "0".equals(value.toString())) {
            return false;
        }
        throw new IllegalArgumentException("布尔值格式不正确");
    }

    /** 校验筛选、排序或指标为对象数组。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value, String name) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            throw new IllegalArgumentException(name + " 必须是对象数组");
        }
        return (List<Map<String, Object>>)(List<?>)list;
    }

    /** 校验字段列表为字符串数组。 */
    private List<String> stringList(Object value, String name) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " 必须是字符串数组");
        }
        return list.stream().map(Object::toString).toList();
    }

    /** 排序方向只允许 ASC 或 DESC。 */
    private String direction(Object value) {
        String direction = value == null ? "ASC" : value.toString().toUpperCase(Locale.ROOT);
        if (!List.of("ASC", "DESC").contains(direction)) {
            throw new IllegalArgumentException("排序方向只允许 ASC 或 DESC");
        }
        return direction;
    }

    /** 保存完成校验的查询条件、参数、投影与排序。 */
    private record QueryParts(String where, MapSqlParameterSource params, Map<String, Field> fields, String orderBy) {
    }

    /** 保存单个聚合列及其输出类型和时间基准。 */
    private record AggregateColumn(String alias, String expression, Type type,
                                   EveAgentDatasetRegistry.TimeBasis timeBasis) {
    }

    /** 保存已固定身份隔离条件的正文查询。 */
    private record DocumentSql(String sql) {
    }
}
