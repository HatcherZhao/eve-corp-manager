# 工业静态数据导入格式

工业模块不把 `docs/evedata.xlsx` 当作蓝图配方来源。当前接口接收规范化 `.json` 或 `.jsonl`；来自 ceve-industry.cn 的公开同步批次按网站规则直接用于计算，手工导入批次则保留校验状态供差异核对。

## JSON

根节点可以是活动数组，也可以是包含 `activities` 数组的对象：

```json
{
  "activities": [
    {
      "blueprintTypeId": 1001,
      "blueprintName": "蓝图中文名",
      "activity": "MANUFACTURING",
      "productTypeId": 2001,
      "productName": "产物中文名",
      "productQuantity": 1,
      "baseTimeSeconds": 3600,
      "probability": 1,
      "materials": [
        {"typeId": 34, "name": "三钛合金", "quantity": 100}
      ],
      "products": [
        {"typeId": 2001, "name": "产物中文名", "quantity": 1}
      ]
    }
  ]
}
```

字段约束：

- `blueprintTypeId`、`productTypeId`、材料/产物 `typeId` 必须是正整数；
- `activity` 当前允许导入 `MANUFACTURING`、`REACTION`、`INVENTION`、`COPYING` 等，但只有制造和反应会进入正式执行范围；
- `productQuantity` 大于 0，`baseTimeSeconds` 不小于 0，`probability` 如果存在则位于 0 到 1；
- `materials` 非空，每种材料的 `quantity` 大于 0，同一活动内不得重复 `typeId`；
- 同一文件中 `blueprintTypeId + activity` 不得重复。

## JSONL

每一行都是上面的一项活动对象，空行会跳过；任一行解析或业务校验失败，整批不落库，并返回行号、错误码和错误说明。

## 来源与版本

请求参数：

- `source=CEVE_INDUSTRY_PUBLIC`：由“同步公开蓝图”从 ceve-industry.cn 的公开接口生成，按该网站规则直接用于制造和反应计算；
- `source=SERENITY_EXPORT`：必须提供国服 `serverVersion`，代表与国服版本对应的完整导出；
- `source=TRANQUILITY_SDE`：只作为候选回退，机会会带 `TQ_FALLBACK_NOT_SERENITY_VERIFIED` 风险标签；
- `buildNumber`、`schemaVersion`、文件 SHA-256 会保存到静态版本记录，用于重放和审计。

## Golden Case

Golden Case 至少记录蓝图、ME/TE、流程数、生产配置、设施配置、实际材料、实际秒数和实际安装费。安装费输入采用国服 adjusted price 与 SCI 快照；`versionedSurcharge` 不默认继承国际服 SCC，而由设施配置和国服实机样例校准。
