# ceve-industry.cn 蓝图计算逻辑调研

调研时间：2026-09-27（北京时间）。调研目标：http://www.ceve-industry.cn/。

## 结论

网站可公开访问，无需登录即可加载蓝图、市场价格、系统工业指数和计算器。蓝图数据与核心计算逻辑均会下发到浏览器，因此可以复核公开的字段和前端公式；网站结果本身不能作为 Serenity 国服客户端事实。

可以吸收的数据结构与计算思路：

- 蓝图、材料、产物、基础时间和活动类型；
- ME 后逐种材料向上取整；
- TE、角色技能、设施时间倍率的组合；
- EIV、SCI、建筑费率、市场税费的成本拆分；
- 吉他买价和卖价分开保存；
- 市场历史与生产速度共同评估销量。

项目现按该网站的公开规则实现：国服蓝图版本、角色技能倍率、建筑与 Rig 加成仍由配置项提供，网站规则作为默认计算口径。

## 公开接口

| 接口 | 用途 | 关键字段 |
| --- | --- | --- |
| http://8.210.18.81:8080/pricedata/allBluePrints | 全量蓝图 | blueprintTypeId、配方、产物、时间、blueprintType |
| http://8.210.18.81:8080/pricedata/allMarketDump | 市场快照 | regionId、systemId、stationId、price、isBuy |
| http://8.210.18.81:8080/pricedata/industryIndices | 系统工业指数 | manuCostIndex、reactCostIndex |
| http://8.210.18.81:8080/pricedata/estimatedPriceList | EIV 估价 | adjusted_price、average_price |
| http://8.210.18.81:8080/pricedata/allMarketHistory | 市场历史 | 前端用于日均成交量、日均价格 |
| http://8.210.18.81:8080/pricedata/dealPriceList | 成交价摘要 | lastDealPrice、quarterDealPrice |

页面当前市场记录中，regionId=10000002、stationId=60003760 被用于吉他价格快照。项目仍需以自身的“吉他海军第四空间站”价格采集与订单簿作为正式数据源。

## 已观察到的案例

“小型护盾扩展装置 I”接口数据：

~~~
{
  "blueprintTypeId": 1010,
  "productTypeId": 377,
  "blueprintType": "NORMAL",
  "materials": [
    {"typeId": 34, "quantity": 916},
    {"typeId": 35, "quantity": 64},
    {"typeId": 36, "quantity": 29},
    {"typeId": 38, "quantity": 4}
  ],
  "productQuantity": 1,
  "baseTimeSeconds": 900
}
~~~

网站默认 ME10 下显示材料为 825、58、27、4；即对基础数量乘 0.9 后逐项向上取整：824.4、57.6、26.1、3.6 分别成为 825、58、27、4。

## 前端公开公式

### 材料

前端可还原出：

~~~
materialMultiplier = (0.9 + 0.01 × (10 - ME)) × structureMaterialMultiplier
required = ceil(baseMaterial × ceil(runs / productQuantity) × materialMultiplier)
~~~

对于基础数量为 1 的特殊材料，网站走流程数量分支；并且使用可选的 prepareQuantity 字段计算整批材料。项目导入格式还没有 prepareQuantity，遇到 Serenity 对应数据时需要加字段或定义降级规则。

### 时间

普通制造：

~~~
duration = baseTime × runs
         × structureTimeMultiplier
         × (0.68 × customSkillRate)
         × (1 - 0.02 × TE)
~~~

反应使用候选角色技能倍率 0.80。网站设置页面显示“满技能”与自定义倍率，默认研究等级为 ME10、TE5。

例如上例的候选时间为：

~~~
900 × 0.68 × (1 - 0.02 × 5) = 550.8 秒 / run
~~~

项目已将该倍率作为默认实现；后续仍可以用 Golden Case 记录版本差异，但不会阻断计算。

### EIV 与费用

前端 EIV：

~~~
EIV = Σ(ME0 基础材料数量 × adjustedPrice) / 每流程产物数
~~~

前端费用拆分：

~~~
solarSystemFee = SCI × EIV × 流程数 × 产物数
structureFee   = 建筑费率 × EIV × 流程数 × 产物数
safetyFee      = 0.04 × EIV × 流程数 × 产物数
marketFee      = 市场费率 × 产品价格 × 流程数 × 产物数
~~~

其中反应取 reactCostIndex，普通制造取 manuCostIndex。网站还单独配置关税、销售税中介费和运输费。

项目当前的安装费公式：

~~~
EIV × (SCI × facilityCostMultiplier + facilityTax + versionedSurcharge)
~~~

项目已把网站前端的 4% 安全委员会费用纳入安装费汇总，versionedSurcharge 作为额外可配置附加费保留。

## 价格、利润与需求

网站可为材料和产品分别选择吉他自动价格、最低卖价、最高买价、买卖中间价、1 天或 20 天成交均价和折扣。页面也支持库存和“自产/收购”切换。

项目已经明确“每次无库存，材料全部市场购买”，因此应继续使用吉他海四卖单逐档采购，不能采用网站库存抵扣路径。产品结果继续分成买单立即变现与卖单挂单销售。

网站同时计算：

~~~
dailyQuantity = quantity / duration × 86400
dailyBusinessVolume = averageDailyVolume × averagePrice
loopEffect = averageDailyVolume / dailyQuantity
~~~

这可作为需求评估参考。项目的订单簿深度、目标销售天数和安全份额上限更贴合“利润高且有市场”的目标，应保留。

## 与项目的对照

| 项目主题 | 网站行为 | 项目结论 |
| --- | --- | --- |
| 蓝图来源 | 自有公开 JSON，来源与版本未明确 | `CEVE_INDUSTRY_PUBLIC` 同步批次直接用于计算；国服导出可作为更高可信度来源 |
| 材料价格 | 可用自产、历史价或单一买卖价 | 无库存，吉他海四卖单逐档采购 |
| 产品收入 | 可选单一买价或卖价 | 买单立即变现、卖单挂单两个结果 |
| ME | 逐材料 ceil | 直接采用网站规则，Golden Case 仅记录差异 |
| TE/技能 | 普通制造 0.68 与 TE 每级 2% | 固定角色技能配置，直接采用网站规则 |
| SCI | 制造/反应指数可覆盖 | 保留系统、设施与人工 SCI 覆盖 |
| SCC | 固定 4% | 按网站规则纳入安装费汇总 |
| 运输 | 可配置 | 按用户口径固定 0 |
| 设施 | 支持自动选优 | 设施全人工配置、保存、比较 |

## 未确认项

1. 网站市场价格采集的原始来源和更新保障；
2. 其蓝图数据是否完全来自当前国服客户端；
3. 固定 0.68、0.80、0.04 是否适用于当前国服；
4. BPC 剩余流程、特殊反应和订单簿不足时的所有边界规则；
5. 安装费最终取整顺序。

## 后续使用方式

项目按该网站规则计算，不再等待额外公式研究。网站蓝图 JSON 可作为导入资料；Golden Case 继续用于记录差异和回归验证，不作为计算功能的前置阻断。
