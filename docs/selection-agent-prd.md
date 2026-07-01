# AI 选品 Agent PRD

## 1. 目标

在现有 AI 项目中新增“AI 选品分析”模块，先做一个可落地的半自动选品 Agent。

第一版目标不是自动决定做什么产品，而是把销售、广告、库存、竞品和关键词数据统一成可量化评分，输出候选品优先级、风险点和测试计划，帮助业务人员更快判断“做 / 小量测试 / 暂不做”。

## 2. 第一版边界

### 做什么

- 支持手动上传候选品数据、历史销售数据、广告数据、库存数据、关键词数据。
- 支持录入 Amazon ASIN、Walmart Item ID、竞品链接或手动竞品表。
- 对每个候选品生成综合评分和推荐等级。
- 输出结构化报告，包含数据依据、风险原因、首批测试建议、广告测试建议、图片/A+方向建议。
- 保留人工确认，Agent 不直接创建上架任务、采购任务或广告任务。

### 暂不做

- 不做全网自动爬虫。
- 不做自动下单采购。
- 不做自动创建广告活动。
- 不做自动上架 Listing。
- 不做没有数据来源的“爆款预测”。

## 3. 适用业务场景

- 现有女装款式是否值得加色、加码、复刻到新平台。
- Amazon / Walmart 竞品表现是否值得跟进。
- 大码女装、睡衣、上衣、套装等细分类目的新款筛选。
- 判断某个 SKU 是否适合投入 A+ 套图、AI 模特图、场景图和广告预算。
- 从多个候选款中排出开发优先级。

## 4. 用户流程

1. 新建选品分析项目。
2. 选择平台、类目、目标价格带和分析周期。
3. 上传或录入候选品数据。
4. 上传销售、广告、库存、关键词、竞品数据。
5. 系统清洗数据并计算基础指标。
6. Agent 生成选品评分、风险判断和测试计划。
7. 用户查看候选品列表和单品详情报告。
8. 用户人工确认下一步动作：进入图片制作、Listing 优化、广告测试或暂不做。

## 5. 数据输入

### 5.1 候选品基础字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| candidateId | string | 是 | 候选品内部 ID |
| spu | string | 否 | 现有款号 |
| platform | string | 是 | AMAZON / WALMART / BOTH |
| category | string | 是 | 类目，如 Plus Size Tops |
| productType | string | 是 | 产品类型，如 tunic top / pajama set |
| targetPrice | number | 是 | 目标售价 |
| estimatedCost | number | 是 | 预估成本 |
| shippingCost | number | 否 | 头程/尾程/履约成本 |
| grossWeight | number | 否 | 重量 |
| material | string | 否 | 面料 |
| sizeRange | string | 否 | 尺码范围 |
| colorCount | integer | 否 | 计划颜色数 |
| productImageUrls | array | 否 | 白底图、参考图 |
| sellingPoints | array | 否 | 卖点 |

### 5.2 销售数据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| date | date | 日期 |
| spu | string | 款号 |
| units | integer | 销量 |
| revenue | number | 销售额 |
| orderCount | integer | 订单数 |
| refundUnits | integer | 退货件数 |
| refundRate | number | 退货率 |
| avgSellingPrice | number | 平均售价 |

### 5.3 广告数据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| date | date | 日期 |
| campaignName | string | 广告活动 |
| spu | string | 款号 |
| keyword | string | 关键词 |
| impressions | integer | 曝光 |
| clicks | integer | 点击 |
| spend | number | 花费 |
| orders | integer | 订单 |
| adSales | number | 广告销售额 |
| ctr | number | 点击率 |
| cvr | number | 转化率 |
| acos | number | ACOS |
| roas | number | ROAS |
| cpc | number | CPC |

### 5.4 库存数据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| spu | string | 款号 |
| sku | string | SKU |
| color | string | 颜色 |
| size | string | 尺码 |
| availableQty | integer | 可售库存 |
| inboundQty | integer | 在途库存 |
| dailySalesAvg | number | 日均销量 |
| daysOfSupply | number | 可售天数 |
| stockStatus | string | 库存状态 |

### 5.5 关键词数据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| keyword | string | 关键词 |
| platform | string | 平台 |
| searchVolume | number | 搜索量或热度 |
| competitionLevel | string | 竞争强度 |
| suggestedBid | number | 建议出价 |
| relevanceScore | number | 与候选品相关性 |
| historicalCtr | number | 历史 CTR |
| historicalCvr | number | 历史 CVR |

### 5.6 竞品数据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| competitorId | string | 竞品 ID |
| platform | string | 平台 |
| asinOrItemId | string | ASIN / Item ID |
| title | string | 标题 |
| price | number | 售价 |
| reviewCount | integer | 评论数 |
| rating | number | 评分 |
| estimatedSales | number | 预估销量 |
| imageQualityScore | number | 图片质量评分 |
| aplusQualityScore | number | A+质量评分 |
| colorCount | integer | 颜色数 |
| sizeRange | string | 尺码范围 |
| mainSellingPoints | array | 主要卖点 |

## 6. 核心指标

### 6.1 基础计算

- 毛利额 = `targetPrice - estimatedCost - shippingCost`
- 毛利率 = `毛利额 / targetPrice`
- CTR = `clicks / impressions`
- CVR = `orders / clicks`
- CPC = `spend / clicks`
- ACOS = `spend / adSales`
- ROAS = `adSales / spend`
- 退货率 = `refundUnits / units`
- 库存可售天数 = `(availableQty + inboundQty) / dailySalesAvg`

### 6.2 评分维度

综合评分满分 100 分：

| 维度 | 权重 | 说明 |
| --- | ---: | --- |
| 需求分 | 25 | 搜索量、历史销量、销售趋势、关键词覆盖 |
| 利润分 | 20 | 毛利率、售价带、履约成本、预估广告成本 |
| 竞争分 | 20 | 竞品数量、评论门槛、价格压力、图片/A+质量差距 |
| 广告可打分 | 15 | CPC、CTR、CVR、ACOS/ROAS 预估 |
| 供应链适配分 | 10 | 现有面料、版型、尺码、库存承接能力 |
| 风险控制分 | 10 | 退货率、季节性、尺码风险、侵权风险 |

### 6.3 评分公式

```text
totalScore =
  demandScore * 0.25 +
  profitScore * 0.20 +
  competitionScore * 0.20 +
  adsScore * 0.15 +
  supplyScore * 0.10 +
  riskScore * 0.10
```

单项评分统一归一到 0-100。

## 7. 推荐等级

| 综合分 | 等级 | 决策 |
| ---: | --- | --- |
| 80-100 | A | 推荐做，可进入图片、Listing、广告测试准备 |
| 65-79 | B | 小量测试，控制预算和首批数量 |
| 50-64 | C | 观察，补充数据或优化成本后再评估 |
| 0-49 | D | 暂不做 |

强制降级规则：

- 毛利率低于 25%，最高只能 B。
- 预估 ACOS 高于可承受 ACOS 1.3 倍，最高只能 B。
- 退货率高于类目安全线，最高只能 C。
- 库存可售天数低于 21 天且无在途，最高只能 C。
- 竞品评论门槛过高且没有差异化卖点，最高只能 C。
- 存在明确侵权风险，直接 D。

## 8. Agent 输出 JSON

```json
{
  "projectId": "SEL-20260630-001",
  "platform": "AMAZON",
  "category": "Plus Size Tops",
  "summary": {
    "candidateCount": 12,
    "recommendedCount": 3,
    "testCount": 5,
    "rejectedCount": 4
  },
  "candidates": [
    {
      "candidateId": "C001",
      "spu": "PNK000000",
      "productType": "plus size tunic top",
      "totalScore": 82.4,
      "grade": "A",
      "decision": "RECOMMEND",
      "scores": {
        "demandScore": 86,
        "profitScore": 78,
        "competitionScore": 81,
        "adsScore": 84,
        "supplyScore": 88,
        "riskScore": 76
      },
      "keyEvidence": [
        "核心关键词历史 CVR 高于账号均值",
        "目标售价带毛利率满足测试要求",
        "竞品 A+ 和场景图质量存在可突破空间"
      ],
      "risks": [
        {
          "type": "RETURN_RATE",
          "level": "MEDIUM",
          "reason": "大码上衣需重点控制尺码表和版型说明"
        }
      ],
      "testPlan": {
        "firstBatchQty": 120,
        "testDays": 21,
        "dailyAdBudget": 30,
        "targetAcos": 0.35,
        "keywordsToTest": [
          "plus size tunic tops for women",
          "womens long sleeve flowy tops"
        ],
        "imagePlan": [
          "白底主图",
          "大码模特场景图",
          "面料细节图",
          "尺码说明 A+"
        ]
      },
      "nextActions": [
        "进入 A+ 套图制作",
        "生成 AI 模特图",
        "创建广告关键词测试包"
      ]
    }
  ]
}
```

## 9. 前端页面建议

第一版页面：`selection-agent.html`

页面结构：

1. 项目基础信息区
   - 平台
   - 类目
   - 价格带
   - 分析周期
2. 数据上传区
   - 候选品表
   - 销售表
   - 广告表
   - 库存表
   - 关键词表
   - 竞品表
3. 候选品评分列表
   - 款号
   - 产品类型
   - 综合分
   - 推荐等级
   - 关键风险
   - 下一步动作
4. 单品详情抽屉
   - 六项评分雷达或条形图
   - 数据依据
   - 风险解释
   - 测试计划
   - 图片/A+建议
5. Agent 报告区
   - 总结
   - 优先级
   - 预算建议
   - 待补充数据

## 10. 后端接口建议

仅作为后续实现参考，第一版可先不开发。

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| POST | `/api/selection/projects` | 创建选品分析项目 |
| POST | `/api/selection/projects/{id}/upload` | 上传数据文件 |
| POST | `/api/selection/projects/{id}/analyze` | 执行评分和 Agent 分析 |
| GET | `/api/selection/projects/{id}` | 获取项目详情 |
| GET | `/api/selection/projects/{id}/candidates` | 获取候选品评分列表 |
| GET | `/api/selection/candidates/{id}` | 获取单品分析详情 |
| POST | `/api/selection/candidates/{id}/confirm` | 人工确认下一步动作 |

## 11. GPT 在模块中的职责

GPT 不负责直接算分，算分由后端固定公式完成。

GPT 负责：

- 解释评分原因。
- 总结风险。
- 根据数据生成选品报告。
- 生成广告测试建议。
- 生成图片和 A+制作方向。
- 指出数据缺口。

固定公式负责：

- 指标计算。
- 综合评分。
- 强制降级。
- 推荐等级。

这样可以避免模型主观臆断，保证结果可复核。

## 12. 第一版实现顺序

1. 建数据库表和 DTO。
2. 做 Excel/CSV 上传解析。
3. 做指标计算和评分服务。
4. 做结构化 GPT 报告生成。
5. 做前端评分列表和详情抽屉。
6. 接入 A+、AI 模特、广告测试计划的后续动作入口。

## 13. 验收标准

- 上传 5 类数据后能生成候选品评分。
- 每个候选品都有六项分数和综合分。
- 推荐等级必须能追溯到数据和规则。
- Agent 报告不能出现无数据依据的结论。
- 不改变现有 A+、AI 场景、AI 模特、视频、任务大盘业务。
- 所有输出报告中必须明确“数据不足”的情况。

## 14. 后续增强

- 接入 Amazon / Walmart 竞品采集。
- 接入关键词趋势数据。
- 接入广告模拟预算分配。
- 接入图片质量自动评分。
- 自动生成选品周报。
- 从推荐款一键创建 A+项目、AI 模特图任务、场景图任务。

