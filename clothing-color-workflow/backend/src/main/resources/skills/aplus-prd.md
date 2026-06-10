# Amazon A+ 套图生成模块 PRD

> 版本：v1.0 | 作者：许清楚（Xu）· 产品经理 | 日期：2026-05-23

---

## 1. 产品目标

**一句话描述**：基于产品参考图和卖点，通过 AI 自动生成符合 Amazon A+ Content 规范的 7 模块套图（AD-01 ~ AD-07），支持单模块或批量生成，帮助卖家快速完成详情页视觉内容制作。

---

## 2. 用户故事

| ID | 角色 | 故事 | 验收标准 |
|----|------|------|----------|
| US-01 | 卖家 | 我想上传产品参考图并输入卖点，系统自动为我生成 7 张 A+ 模块图 | 上传图片 + 输入卖点 → 生成 7 张 16:9 图片 |
| US-02 | 卖家 | 我想选择部分模块生成，而不是每次都生成全部 7 张 | 勾选 AD-01/AD-03/AD-05 → 只生成这 3 张 |
| US-03 | 卖家 | 我想查看每个模块的生成进度和状态 | 任务列表显示每张图的处理状态（待生成/生成中/已完成/失败） |
| US-04 | 卖家 | 我想下载生成好的 A+ 套图 | 单张下载 + 批量打包下载 ZIP |
| US-05 | 卖家 | 我想重新生成某个不满意的模块 | 点击"重新生成" → 仅重新生成该模块 |
| US-06 | 卖家 | 我想为特定模块补充专属参考图（如 AD-02 补充面料微距图、AD-04 补充场景参考图） | 每个模块卡片支持独立上传补充照片 |
| US-07 | 卖家 | 我想为特定模块补充文字说明（如 AD-06 补充具体尺码数据、AD-07 补充护理说明） | 每个模块卡片支持独立填写补充文字 |
| US-08 | 卖家 | 我想在卖点中补充衣服类型、适用季节等基础信息，帮助 AI 更好理解产品 | 卖点字段支持多维度信息输入 |

---

## 3. 需求池

### P0（核心功能 - 第一期必须实现）

| 需求 | 说明 |
|------|------|
| 项目创建 | 上传参考图 + 输入产品卖点（含衣服类型等基础信息）+ 选择模块 + 为各模块补充专属照片/文字 → 创建 A+ 项目 |
| 文案生成 | 调用 TextModelService 读取 Skill 模板，生成 A+ MD 文档并解析为 7 个模块的 Prompt |
| 图片生成 | 调用 KieClientService 为每个模块生成 16:9 图片 |
| 任务轮询 | @Scheduled 定时轮询 KIE 任务状态，更新结果 |
| 结果展示 | 前端展示 7 个模块的生成结果（图片 + 状态） |
| 单张下载 | 支持单张图片下载 |
| 批量下载 | 支持打包 ZIP 下载 |

### P1（增强功能 - 第二期实现）

| 需求 | 说明 |
|------|------|
| 部分模块选择 | 用户可勾选需要的模块，跳过不需要的 |
| 单模块重新生成 | 对已生成的模块重新生成 |
| 参考图预览 | 上传后即时预览参考图 |
| 生成历史 | 查看历史项目列表和详情 |

### P2（优化功能 - 后续迭代）

| 需求 | 说明 |
|------|------|
| Prompt 编辑 | 允许用户在生成前查看和微调每个模块的 Prompt |
| 风格预设 | 提供多种视觉风格预设（暖色家居、科技感、极简等） |
| 模板管理 | 支持自定义 A+ 模块模板 |
| 费用统计 | 按项目/时间段统计生成费用 |

---

## 4. 核心业务流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           A+ 套图生成流程                                    │
└─────────────────────────────────────────────────────────────────────────────┘

[用户操作]                    [系统处理]                      [输出结果]
    │                            │                              │
    ▼                            ▼                              ▼
┌──────────┐              ┌──────────────┐                ┌──────────┐
│ 1. 上传   │              │ 2. 文案生成   │                │ 7. 结果   │
│ 参考图    │─────────────▶│ (TextModel)  │                │ 展示     │
│ + 卖点    │              │              │                │          │
└──────────┘              └──────┬───────┘                └────▲─────┘
                                 │                             │
                                 ▼                             │
                          ┌──────────────┐                     │
                          │ 3. 解析 MD   │                     │
                          │ 拆分 7 模块   │                     │
                          └──────┬───────┘                     │
                                 │                             │
                                 ▼                             │
                          ┌──────────────┐                     │
                          │ 4. 生成 Prompt│                     │
                          │ (每模块独立)  │                     │
                          └──────┬───────┘                     │
                                 │                             │
                                 ▼                             │
                          ┌──────────────┐                     │
                          │ 5. 调用 KIE  │                     │
                          │ 图片生成 API  │                     │
                          └──────┬───────┘                     │
                                 │                             │
                                 ▼                             │
                          ┌──────────────┐                     │
                          │ 6. 轮询状态   │─────────────────────┘
                          │ @Scheduled   │
                          └──────────────┘
```

### 详细步骤说明

| 步骤 | 操作 | 技术实现 | 输出 |
|------|------|----------|------|
| Step 1 | 用户上传参考图 + 输入卖点（含衣服类型等基础信息）+ 选择模块 + 为各模块补充专属照片/文字 | 前端表单 + OSS 上传 | 参考图 OSS URL + 各模块补充数据 |
| Step 2 | 系统调用文本模型生成 A+ MD 文档 | TextModelService + Skill 模板 | Markdown 文档 |
| Step 3 | 解析 MD 文档，拆分为 AD-01 ~ AD-07 | 正则解析 / JSON 结构化 | 7 个模块文案 |
| Step 4 | 为每个模块生成独立 Prompt | 拼接风格锚点 + 模块文案 | 7 个 Prompt |
| Step 5 | 调用 KIE 图片生成 API | KieClientService.createTask() | 7 个 KIE taskId |
| Step 6 | 定时轮询 KIE 任务状态 | @Scheduled 每 30 秒 | 更新任务状态 |
| Step 7 | 前端展示结果 | 轮询 / WebSocket | 7 张 16:9 图片 |

---

## 5. 数据库表设计

### 5.1 aplus_project（A+ 项目表）

```java
package com.ai.aplus.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A+ 套图项目
 */
@Data
@Entity
@Table(name = "aplus_project")
public class AplusProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目名称（用户自定义或自动生成） */
    @Column(nullable = false, length = 200)
    private String projectName;

    /** 产品 SPU 编号 */
    @Column(nullable = false, length = 100)
    private String spu;

    /** 产品参考图 OSS URL */
    @Column(nullable = false, length = 500)
    private String referenceImageUrl;

    /** 产品卖点（用户输入） */
    @Column(columnDefinition = "text")
    private String sellingPoints;

    /** AI 生成的 A+ MD 文档 */
    @Column(columnDefinition = "text")
    private String aplusMarkdown;

    /** 选择的模块列表，JSON 数组 ["AD-01","AD-03","AD-05"] */
    @Column(columnDefinition = "text")
    private String selectedModules;

    /** 项目状态：CREATED / GENERATING_COPY / COPY_DONE / GENERATING_IMAGES / COMPLETED / FAILED */
    @Column(nullable = false, length = 30)
    private String status;

    /** 错误信息 */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /** 操作人 */
    @Column(length = 50)
    private String operator;

    /** 所属店铺 */
    @Column(length = 100)
    private String shopName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 关联的模块任务列表 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AplusImageTask> imageTasks;
}
```

### 5.2 aplus_image_task（A+ 模块图片任务表）

```java
package com.ai.aplus.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A+ 模块图片生成任务
 */
@Data
@Entity
@Table(name = "aplus_image_task")
public class AplusImageTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联项目 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private AplusProject project;

    /** 模块编号：AD-01 ~ AD-07 */
    @Column(nullable = false, length = 10)
    private String moduleCode;

    /** 模块名称 */
    @Column(nullable = false, length = 50)
    private String moduleName;

    /** 模块文案（从 MD 文档解析） */
    @Column(columnDefinition = "text")
    private String moduleCopy;

    /** 模块补充参考图 URL（用户为该模块单独上传的补充照片） */
    @Column(length = 500)
    private String supplementaryImageUrl;

    /** 模块补充文字说明（用户为该模块单独填写的补充信息） */
    @Column(columnDefinition = "text")
    private String supplementaryText;

    /** 生成的 Prompt */
    @Column(columnDefinition = "text")
    private String prompt;

    /** 图片比例：16:9 */
    @Column(length = 20)
    private String aspectRatio;

    /** KIE 平台任务 ID */
    @Column(length = 128)
    private String kieTaskId;

    /** 使用的模型 */
    @Column(length = 64)
    private String model;

    /** 任务状态：PENDING / PROCESSING / SUCCESS / FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    /** KIE 临时结果 URL */
    @Column(length = 500)
    private String resultTempUrl;

    /** OSS 永久 URL */
    @Column(length = 500)
    private String resultOssUrl;

    /** 错误信息 */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /** 预估费用 */
    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
```

### 5.3 aplus_task_result（A+ 任务结果汇总表 - 可选）

> **说明**：此表用于记录最终交付结果，可与 `aplus_project` 合并。如需独立记录下载历史，可使用此表。

```java
package com.ai.aplus.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A+ 任务结果汇总（可选，用于记录下载历史）
 */
@Data
@Entity
@Table(name = "aplus_task_result")
public class AplusTaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联项目 ID */
    @Column(nullable = false)
    private Long projectId;

    /** 成功生成的模块数量 */
    @Column(nullable = false)
    private Integer successCount;

    /** 失败的模块数量 */
    @Column(nullable = false)
    private Integer failedCount;

    /** 总费用 */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    /** ZIP 打包下载 URL */
    @Column(length = 500)
    private String zipDownloadUrl;

    /** 下载次数 */
    @Column(nullable = false)
    private Integer downloadCount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 5.4 数据库 ER 图

```
┌─────────────────────┐       ┌──────────────────────────┐       ┌─────────────────────┐
│    aplus_project    │       │    aplus_image_task      │       │  aplus_task_result  │
├─────────────────────┤       ├──────────────────────────┤       ├─────────────────────┤
│ id (PK)            │───┐   │ id (PK)                 │       │ id (PK)            │
│ project_name       │   │   │ project_id (FK)         │       │ project_id (FK)    │
│ spu                │   │   │ module_code             │       │ success_count      │
│ reference_image_url│   └──▶│ module_name             │       │ failed_count       │
│ selling_points     │       │ module_copy             │       │ total_cost         │
│ aplus_markdown     │       │ supplementary_image_url │       │ zip_download_url   │
│ selected_modules   │       │ supplementary_text      │       │ download_count     │
│ status             │       │ prompt                  │       │ created_at         │
│ error_message      │       │ aspect_ratio            │       └─────────────────────┘
│ operator           │       │ kie_task_id             │
│ shop_name          │       │ model                   │
│ created_at         │       │ status                  │
│ updated_at         │       │ result_temp_url         │
│ completed_at       │       │ result_oss_url          │
└─────────────────────┘       │ error_message           │
                              │ cost                    │
                              │ created_at              │
                              │ updated_at              │
                              │ completed_at            │
                              └──────────────────────────┘
```

### 5.5 项目状态流转

```
CREATED → GENERATING_COPY → COPY_DONE → GENERATING_IMAGES → COMPLETED
   │            │                │              │                │
   │            │                │              │                │
   ▼            ▼                ▼              ▼                ▼
 FAILED       FAILED           FAILED         FAILED        [终态]
```

| 状态 | 说明 |
|------|------|
| CREATED | 项目已创建，等待开始生成文案 |
| GENERATING_COPY | 正在调用 TextModelService 生成 A+ MD 文档 |
| COPY_DONE | 文案生成完成，等待生成图片 |
| GENERATING_IMAGES | 正在调用 KIE 生成图片 |
| COMPLETED | 所有模块图片生成完成 |
| FAILED | 生成失败（含失败原因） |

### 5.6 模块任务状态流转

```
PENDING → PROCESSING → SUCCESS
   │           │           │
   │           │           │
   ▼           ▼           ▼
 FAILED      FAILED    [终态]
```

---

## 6. API 接口列表

### 6.1 创建 A+ 项目

```
POST /api/aplus/projects
```

**请求体**：
```json
{
    "projectName": "2026夏季连衣裙A+",
    "spu": "SPU-20260523-001",
    "referenceImageUrl": "https://oss.xxx.com/temp/xxx.jpg",
    "sellingPoints": "衣服类型: 连衣裙\n季节: 夏季\n面料: 透气冰丝\n卖点:\n1. 透气冰丝面料\n2. 显瘦A字版型\n3. 多色可选\n4. 适合度假/日常穿搭",
    "selectedModules": ["AD-01", "AD-02", "AD-03", "AD-04", "AD-05", "AD-06", "AD-07"],
    "moduleExtras": {
        "AD-02": {
            "supplementaryImageUrl": "https://oss.xxx.com/temp/fabric-detail.jpg",
            "supplementaryText": "面料特写：冰丝材质，透气孔细节"
        },
        "AD-04": {
            "supplementaryImageUrl": "https://oss.xxx.com/temp/beach-scene.jpg",
            "supplementaryText": "希望海滩场景参考此图风格"
        },
        "AD-06": {
            "supplementaryImageUrl": null,
            "supplementaryText": "尺码: S(4-6) M(8-10) L(12-14) XL(16-18) 2XL(20-22)\n胸围: 36-38 / 38-40 / 40-42 / 42-44 / 44-46 英寸\n裙长: 40英寸（全尺码统一）"
        },
        "AD-07": {
            "supplementaryImageUrl": null,
            "supplementaryText": "护理说明: 冷水手洗，不可漂白，悬挂晾干"
        }
    }
}
```

**响应体**：
```json
{
    "success": true,
    "message": "A+ 项目创建成功，正在生成文案",
    "data": {
        "id": 1,
        "projectName": "2026夏季连衣裙A+",
        "status": "CREATED",
        "createdAt": "2026-05-23 15:30:00"
    }
}
```

### 6.2 获取项目详情

```
GET /api/aplus/projects/{id}
```

**响应体**：
```json
{
    "success": true,
    "message": "ok",
    "data": {
        "id": 1,
        "projectName": "2026夏季连衣裙A+",
        "spu": "SPU-20260523-001",
        "referenceImageUrl": "https://oss.xxx.com/temp/xxx.jpg",
        "sellingPoints": "...",
        "aplusMarkdown": "...",
        "selectedModules": ["AD-01", "AD-02", "AD-03", "AD-04", "AD-05", "AD-06", "AD-07"],
        "status": "COMPLETED",
        "operator": "张三",
        "shopName": "PINKSIR",
        "createdAt": "2026-05-23 15:30:00",
        "completedAt": "2026-05-23 15:35:00",
        "imageTasks": [
            {
                "id": 1,
                "moduleCode": "AD-01",
                "moduleName": "品牌英雄图",
                "status": "SUCCESS",
                "resultOssUrl": "https://oss.xxx.com/aplus/xxx.jpg"
            },
            ...
        ]
    }
}
```

### 6.3 获取项目列表（分页）

```
GET /api/aplus/projects?page=1&size=20&spu=xxx&status=COMPLETED
```

**响应体**：
```json
{
    "success": true,
    "message": "ok",
    "data": {
        "content": [...],
        "totalElements": 100,
        "totalPages": 5,
        "number": 1,
        "size": 20
    }
}
```

### 6.4 触发文案生成

```
POST /api/aplus/projects/{id}/generate-copy
```

**响应体**：
```json
{
    "success": true,
    "message": "文案生成任务已提交",
    "data": null
}
```

### 6.5 触发图片生成

```
POST /api/aplus/projects/{id}/generate-images
```

**响应体**：
```json
{
    "success": true,
    "message": "图片生成任务已提交，共 7 个模块",
    "data": null
}
```

### 6.6 重新生成单个模块

```
POST /api/aplus/projects/{id}/modules/{moduleCode}/regenerate
```

**响应体**：
```json
{
    "success": true,
    "message": "AD-03 重新生成任务已提交",
    "data": null
}
```

### 6.7 获取模块任务状态

```
GET /api/aplus/projects/{id}/modules
```

**响应体**：
```json
{
    "success": true,
    "message": "ok",
    "data": [
        {
            "id": 1,
            "moduleCode": "AD-01",
            "moduleName": "品牌英雄图",
            "status": "SUCCESS",
            "resultOssUrl": "https://oss.xxx.com/aplus/xxx.jpg",
            "cost": 0.05
        },
        {
            "id": 2,
            "moduleCode": "AD-02",
            "moduleName": "印花/面料故事图",
            "status": "PROCESSING",
            "resultOssUrl": null,
            "cost": null
        }
    ]
}
```

### 6.8 批量下载 ZIP

```
POST /api/aplus/projects/{id}/download-zip
```

**响应体**：二进制文件流（application/zip）

### 6.9 单张图片代理下载

```
GET /api/aplus/proxy-download?url=xxx&filename=AD-01.jpg
```

**响应体**：二进制文件流

### 6.10 删除项目

```
DELETE /api/aplus/projects/{id}
```

**响应体**：
```json
{
    "success": true,
    "message": "项目删除成功",
    "data": null
}
```

---

## 7. 前端页面设计

### 7.1 页面入口

- **独立页面**：`aplus.html`
- **侧边栏入口**：在现有侧边栏添加「A+ 套图」菜单项
- **图标**：使用详情页/A+ 相关图标

### 7.2 页面布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  顶部导航栏                                                    [用户信息]  │
├──────────┬──────────────────────────────────────────────────────────────────┤
│          │                                                                  │
│  侧边栏   │  ┌─────────────────────────────────────────────────────────────┐│
│          │  │  A+ 套图生成                                                ││
│ ───────  │  ├─────────────────────────────────────────────────────────────┤│
│ 换色     │  │                                                             ││
│ 场景     │  │  ┌─────────────────────────────────────────────────────┐   ││
│ 模特库   │  │  │  创建新项目                                          │   ││
│ 买家秀   │  │  ├─────────────────────────────────────────────────────┤   ││
│ A+ 套图  │  │  │  项目名称: [________________]  SPU: [________]       │   ││
│ ───────  │  │  │                                                     │   ││
│ 统计     │  │  │  参考图:  [选择文件]  ┌──────────┐                   │   ││
│ 管理     │  │  │                     │  预览图   │                   │   ││
│          │  │  │                     └──────────┘                   │   ││
│          │  │  │  卖点:   [textarea________________]                │   ││
│          │  │  │                                                     │   ││
│          │  │  │  模块选择（可展开补充信息）:                          │   ││
│          │  │  │  ┌──────────────────────────────────────────────┐   │   ││
│          │  │  │  │ ☑ AD-01 品牌英雄图      [展开补充信息 ▼]      │   │   ││
│          │  │  │  │ ☑ AD-02 印花/面料故事图  [展开补充信息 ▼]      │   │   ││
│          │  │  │  │   补充照片: [选择文件]                        │   │   ││
│          │  │  │  │   补充文字: [面料特写，透气孔细节____]         │   │   ││
│          │  │  │  │ ☑ AD-03 设计细节图      [展开补充信息 ▼]      │   │   ││
│          │  │  │  │ ☑ AD-04 多场景穿搭图    [展开补充信息 ▼]      │   │   ││
│          │  │  │  │   补充照片: [选择文件]  预览: [海滩参考图]     │   │   ││
│          │  │  │  │   补充文字: [海滩/brunch/晚宴三场景________]   │   │   ││
│          │  │  │  │ ☑ AD-05 舒适体验图      [展开补充信息 ▼]      │   │   ││
│          │  │  │  │ ☑ AD-06 尺码/版型指南   [展开补充信息 ▼]      │   │   ││
│          │  │  │  │   补充文字: [S(4-6) M(8-10) L(12-14) ...]    │   │   ││
│          │  │  │  │ ☑ AD-07 护理/品牌收尾图 [展开补充信息 ▼]      │   │   ││
│          │  │  │  │   补充文字: [冷水手洗，不可漂白_______]        │   │   ││
│          │  │  │  └──────────────────────────────────────────────┘   │   ││
│          │  │  │                                                     │   ││
│          │  │  │              [生成 A+ 套图]                         │   ││
│          │  │  └─────────────────────────────────────────────────────┘   ││
│          │  │                                                             ││
│          │  │  ┌─────────────────────────────────────────────────────┐   ││
│          │  │  │  生成结果                                          │   ││
│          │  │  ├─────────────────────────────────────────────────────┤   ││
│          │  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐            │   ││
│          │  │  │  │  AD-01  │  │  AD-02  │  │  AD-03  │            │   ││
│          │  │  │  │ 品牌英雄 │  │ 印花故事 │  │ 设计细节 │            │   ││
│          │  │  │  │ [图片]  │  │ [图片]  │  │ [图片]  │            │   ││
│          │  │  │  │ ✅完成  │  │ ⏳生成中 │  │ ❌失败  │            │   ││
│          │  │  │  │ [下载]  │  │         │  │ [重试]  │            │   ││
│          │  │  │  └─────────┘  └─────────┘  └─────────┘            │   ││
│          │  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐ ┌────────┐│   ││
│          │  │  │  │  AD-04  │  │  AD-05  │  │  AD-06  │ │ AD-07  ││   ││
│          │  │  │  │ 多场景   │  │ 舒适体验 │  │ 尺码指南 │ │护理收尾 ││   ││
│          │  │  │  │ [图片]  │  │ [图片]  │  │ [图片]  │ │[图片]  ││   ││
│          │  │  │  │ ✅完成  │  │ ✅完成  │  │ ✅完成  │ │✅完成  ││   ││
│          │  │  │  │ [下载]  │  │ [下载]  │  │ [下载]  │ │[下载]  ││   ││
│          │  │  │  └─────────┘  └─────────┘  └─────────┘ └────────┘│   ││
│          │  │  │                                                     │   ││
│          │  │  │  [批量下载 ZIP]  [全部重新生成]                      │   ││
│          │  │  └─────────────────────────────────────────────────────┘   ││
│          │  │                                                             ││
│          │  │  ┌─────────────────────────────────────────────────────┐   ││
│          │  │  │  历史项目                                          │   ││
│          │  │  ├─────────────────────────────────────────────────────┤   ││
│          │  │  │  项目名称    SPU        状态      创建时间    操作  │   ││
│          │  │  │  ─────────────────────────────────────────────────  │   ││
│          │  │  │  夏季连衣裙  SPU-001    ✅完成   05-23 15:30  [查看]│   ││
│          │  │  │  春季外套    SPU-002    ⏳生成中 05-23 14:20  [查看]│   ││
│          │  │  └─────────────────────────────────────────────────────┘   ││
│          │  └─────────────────────────────────────────────────────────────┘│
└──────────┴──────────────────────────────────────────────────────────────────┘
```

### 7.3 字段说明

#### 创建项目表单

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 项目名称 | text | 否 | 默认自动生成：`{SPU}-A+-{日期}` |
| SPU | text | 是 | 产品 SPU 编号 |
| 参考图 | file | 是 | 支持 JPG/PNG，上传至 OSS 临时桶 |
| 卖点 | textarea | 是 | 支持多维度输入：衣服类型、适用季节、面料、核心卖点等，每行一个维度 |
| 模块选择 | checkbox | 是 | 默认全选 AD-01 ~ AD-07 |
| 模块补充照片 | file（每模块独立） | 否 | 针对特定模块上传补充参考图（如面料微距图、场景参考图） |
| 模块补充文字 | textarea（每模块独立） | 否 | 针对特定模块填写补充说明（如尺码数据、护理说明） |

#### 模块卡片

| 字段 | 说明 |
|------|------|
| 模块编号 | AD-01 ~ AD-07 |
| 模块名称 | 品牌英雄图 / 印花故事图 / ... |
| 模块选择 | checkbox，选中后展开补充信息区域 |
| 补充照片 | 选中后可选上传，支持拖拽，上传后即时预览 |
| 补充文字 | 选中后可选填写，placeholder 提示该模块建议补充的内容 |
| 生成图片 | 16:9 比例的结果图 |
| 状态标签 | 待生成 / 生成中 / 已完成 / 失败 |
| 操作按钮 | 下载 / 重新生成 / 查看 Prompt |

#### 历史项目表格

| 字段 | 说明 |
|------|------|
| 项目名称 | 用户输入或自动生成 |
| SPU | 产品 SPU 编号 |
| 状态 | 项目整体状态 |
| 创建时间 | 格式：MM-DD HH:mm |
| 操作 | 查看详情 / 删除 |

### 7.4 交互说明

| 交互 | 说明 |
|------|------|
| 上传参考图 | 支持拖拽上传，上传后即时预览 |
| 模块选择 | 点击 checkbox 切换选中状态，至少选择 1 个 |
| 展开补充信息 | 点击"展开补充信息"按钮，展开该模块的照片上传和文字输入区域 |
| 模块补充照片 | 展开后可选上传，支持拖拽，上传后即时预览缩略图 |
| 模块补充文字 | 展开后可选填写，placeholder 显示该模块建议补充的内容类型 |
| 卖点输入 | textarea，placeholder 提示"衣服类型、季节、面料、核心卖点等，每行一个" |
| 生成按钮 | 点击后禁用，显示"生成中..." |
| 状态轮询 | 每 5 秒轮询一次项目状态 |
| 图片预览 | 点击图片放大查看 |
| 批量下载 | 仅当有 1+ 张成功图片时可用 |
| 重新生成 | 仅对已完成/失败的模块可用 |

---

## 8. 待确认问题

| # | 问题 | 影响范围 | 建议方案 |
|---|------|----------|----------|
| Q1 | Skill 模板文件路径？需要确认 `amazon-listing-generator` Skill 在服务器上的实际路径 | 后端 Prompt 生成 | 确认后硬编码或配置化 |
| Q2 | 文案生成是否异步？调用 TextModelService 生成 MD 文档可能耗时较长 | 接口设计 | 建议异步：先返回项目 ID，后台异步生成文案 |
| Q3 | 是否需要支持用户编辑 Prompt 后再生成图片？ | 功能范围 | P2 阶段实现，第一期不支持 |
| Q4 | A+ 图片生成使用的具体模型？是否与现有换色/场景共用同一模型 | 模型配置 | 建议使用 KIE 平台的通用图像生成模型 |
| Q5 | 是否需要支持 Amazon 其他站点（如 .co.uk、.de）的 A+ 规范？ | 模块定义 | 第一期仅支持 US 站，后续扩展 |
| Q6 | 费用计算方式？按模块计费还是按项目计费 | 费用统计 | 建议按模块计费，汇总到项目 |
| Q7 | 是否需要支持视频模块（AV-01）？Skill 中提到了视频 slot | 功能范围 | P2 阶段考虑，第一期仅图片 |
| Q8 | 历史项目数据保留策略？是否需要定期清理 | 数据管理 | 建议保留 90 天，超期自动归档 |

---

## 9. 技术约束

| 约束 | 说明 |
|------|------|
| ORM | JPA/Hibernate，不使用 MyBatis |
| API 响应格式 | `ApiResponse<T>`（success/message/data） |
| 实体注解 | `@Entity` + `@Table` + `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| Repository | `JpaRepository<T, Long>` |
| HTTP 客户端 | OkHttpClient |
| 认证方式 | Token 拦截器 `X-User-Token` |
| 包结构 | 新模块放 `com.ai.aplus.*` 子包 |
| 图片比例 | A+ 默认 16:9 |
| OSS | 阿里云双桶架构（临时桶 5 天删除 + 永久桶） |
| 定时轮询 | `@Scheduled` 模式 |

---

## 10. 模块定义对照表

| 模块编号 | 模块名称 | 视觉定位 | 核心内容 |
|----------|----------|----------|----------|
| AD-01 | 品牌英雄图 | 宽幅生活方式横幅 | 产品在目标环境中的整体气质 |
| AD-02 | 印花/面料故事图 | 左右分栏 | 左侧平铺产品/面料图案，右侧面料微距细节 |
| AD-03 | 设计细节图 | 中心+四周 | 中心产品平铺图 + 四周圆形细节放大图和连接线 |
| AD-04 | 多场景穿搭图 | 三宫格 | Beach Day / Brunch Ready / Resort Evening |
| AD-05 | 舒适体验图 | 主角+角落 | 主画面模特放松场景 + 角落面料微距小图 |
| AD-06 | 尺码/版型指南 | 技术图表 | 正反面平铺服装 + 量体箭头 + 尺码表 |
| AD-07 | 护理/品牌收尾图 | 静物+说明 | 折叠产品静物图 + 护理说明区域 |

### 统一风格锚点（每个模块 Prompt 必须包含）

```
同一个产品、同一个印花、同一个面料、同一个品牌调性、同一个摄影风格、同一个排版体系、同一个色彩体系
```

### 各模块补充信息建议

| 模块编号 | 模块名称 | 建议补充照片 | 建议补充文字 |
|----------|----------|-------------|-------------|
| AD-01 | 品牌英雄图 | 生活场景参考图 | 品牌调性、目标人群描述 |
| AD-02 | 印花/面料故事图 | 面料微距图、印花细节图 | 面料成分、工艺说明 |
| AD-03 | 设计细节图 | 设计细节特写图 | 设计亮点说明（如口袋、拉链、缝线） |
| AD-04 | 多场景穿搭图 | 期望的场景风格参考图 | 场景描述（海滩/brunch/晚宴） |
| AD-05 | 舒适体验图 | 面料质感特写图 | 穿着感受描述 |
| AD-06 | 尺码/版型指南 | 尺码表截图 | 具体尺码数据（S/M/L/XL 各尺码的胸围/腰围/裙长） |
| AD-07 | 护理/品牌收尾图 | 品牌 logo 图 | 护理说明（洗涤方式、注意事项） |

---

## 附录 A：Skill 模板调用流程

```
1. 读取 Skill 文件：amazon-listing-generator/SKILL.md
2. 提取 STEP 3B 部分的 A+ 模块定义
3. 拼接 Prompt 模板：
   - 用户参考图 URL
   - 用户卖点
   - 风格锚点
   - 各模块定义
4. 调用 TextModelService 生成 MD 文档
5. 解析 MD 文档，提取 AD-01 ~ AD-07 各模块内容
6. 为每个模块生成独立的图片生成 Prompt
7. 调用 KieClientService 生成图片
```

---

## 附录 B：A+ MD 文档输出格式

```markdown
# A+ Content - {产品名称}

## AD-01 品牌英雄图
**核心卖点**: {一句话卖点}
**视觉描述**: {场景描述}
**文案**: {英文/中文文案}

## AD-02 印花/面料故事图
**核心卖点**: {面料卖点}
**视觉描述**: {左右分栏描述}
**文案**: {英文/中文文案}

## AD-03 设计细节图
**核心卖点**: {设计卖点}
**视觉描述**: {中心+四周描述}
**文案**: {英文/中文文案}

## AD-04 多场景穿搭图
**核心卖点**: {场景卖点}
**视觉描述**: {三宫格描述}
**文案**: {英文/中文文案}

## AD-05 舒适体验图
**核心卖点**: {舒适卖点}
**视觉描述**: {模特场景描述}
**文案**: {英文/中文文案}

## AD-06 尺码/版型指南
**核心卖点**: {版型卖点}
**视觉描述**: {技术图表描述}
**文案**: {英文/中文文案}

## AD-07 护理/品牌收尾图
**核心卖点**: {护理卖点}
**视觉描述**: {静物描述}
**文案**: {英文/中文文案}
```

---

*文档结束*
