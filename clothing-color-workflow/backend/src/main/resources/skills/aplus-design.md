# Amazon A+ 套图生成模块 - 系统架构设计文档

> 版本：v1.0 | 作者：胡辰（Hu）· 架构师 | 日期：2026-05-23

---

## 1. 系统架构图

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端层 (Frontend)                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  aplus.html (Vue 3 + Element Plus + Axios)                          │    │
│  │  - 项目创建表单                                                      │    │
│  │  - 模块选择卡片                                                      │    │
│  │  - 生成结果展示                                                      │    │
│  │  - 历史项目列表                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼ HTTP REST API
┌─────────────────────────────────────────────────────────────────────────────┐
│                              控制器层 (Controller)                           │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  AplusController (/api/aplus/*)                                     │    │
│  │  - POST /projects              创建项目                              │    │
│  │  - GET  /projects/{id}         获取项目详情                           │    │
│  │  - GET  /projects              获取项目列表                           │    │
│  │  - POST /projects/{id}/generate-copy    触发文案生成                  │    │
│  │  - POST /projects/{id}/generate-images  触发图片生成                  │    │
│  │  - POST /projects/{id}/modules/{code}/regenerate  重新生成单模块      │    │
│  │  - GET  /projects/{id}/modules  获取模块任务状态                      │    │
│  │  - POST /projects/{id}/download-zip  批量下载                        │    │
│  │  - GET  /proxy-download        单张代理下载                          │    │
│  │  - DELETE /projects/{id}       删除项目                              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              服务层 (Service)                                │
│  ┌───────────────────────┐  ┌───────────────────────┐  ┌────────────────┐  │
│  │  AplusProjectService  │  │  AplusCopyService     │  │  AplusImage    │  │
│  │  - 项目 CRUD          │  │  - 文案生成           │  │  Service       │  │
│  │  - 状态管理           │  │  - MD 解析            │  │  - 图片生成    │  │
│  │  - 批量下载           │  │  - Prompt 拼接        │  │  - 任务轮询    │  │
│  └───────────────────────┘  └───────────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            外部服务集成层                                     │
│  ┌───────────────────┐  ┌───────────────────┐  ┌─────────────────────────┐  │
│  │  TextModelService │  │  KieClientService │  │  OssService             │  │
│  │  (Claude/GPT)     │  │  (图片生成 API)    │  │  (阿里云 OSS)           │  │
│  └───────────────────┘  └───────────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据持久层 (Repository)                            │
│  ┌───────────────────────┐  ┌───────────────────────┐                       │
│  │  AplusProjectRepo     │  │  AplusImageTaskRepo   │                       │
│  │  (JpaRepository)      │  │  (JpaRepository)      │                       │
│  └───────────────────────┘  └───────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据库 (MySQL)                                    │
│  ┌───────────────────────┐  ┌───────────────────────┐                       │
│  │  aplus_project        │  │  aplus_image_task     │                       │
│  └───────────────────────┘  └───────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心数据流

```
用户上传参考图 + 输入卖点 + 选择模块
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: 创建项目 (AplusProjectService.createProject)            │
│   - 上传参考图到 OSS 临时桶                                      │
│   - 上传各模块补充照片到 OSS 临时桶                               │
│   - 创建 AplusProject 记录 (status=CREATED)                     │
│   - 创建 AplusImageTask 记录 (status=PENDING)                   │
│     含 supplementaryImageUrl + supplementaryText                 │
└─────────────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: 生成文案 (AplusCopyService.generateCopy)                │
│   - 读取 amazon-listing-generator Skill 模板                    │
│   - 拼接系统 Prompt + 用户卖点 + 参考图 URL                      │
│   - 调用 TextModelService.generatePrompt()                      │
│   - 解析返回的 MD 文档，拆分为 7 个模块文案                       │
│   - 更新 AplusProject.aplusMarkdown                             │
│   - 更新 AplusImageTask.moduleCopy                              │
│   - 状态流转: CREATED → GENERATING_COPY → COPY_DONE             │
└─────────────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: 生成图片 (AplusImageService.generateImages)             │
│   - 为每个模块生成独立 Prompt (拼接风格锚点 + 模块文案)           │
│   - 调用 KieClientService.createTask() 创建图片生成任务          │
│   - 记录 kieTaskId 到 AplusImageTask                            │
│   - 状态流转: COPY_DONE → GENERATING_IMAGES                     │
└─────────────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: 轮询结果 (AplusImageService.pollTaskStatus)             │
│   - @Scheduled 每 30 秒轮询                                     │
│   - 调用 KieClientService.getFullResult() 查询任务状态           │
│   - 成功: 下载临时 URL → 转存 OSS 永久桶 → 更新 resultOssUrl    │
│   - 失败: 记录 errorMessage                                     │
│   - 状态流转: PROCESSING → SUCCESS / FAILED                     │
└─────────────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: 前端展示                                                │
│   - 前端每 5 秒轮询 GET /api/aplus/projects/{id}                │
│   - 展示 7 个模块卡片 (图片 + 状态)                              │
│   - 支持单张下载 / 批量 ZIP 下载                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 包结构设计

### 2.1 新增包结构

```
com.ai.aplus/
├── controller/
│   └── AplusController.java              # REST API 控制器
├── service/
│   ├── AplusProjectService.java          # 项目服务接口
│   ├── AplusCopyService.java             # 文案生成服务接口
│   ├── AplusImageService.java            # 图片生成服务接口
│   └── impl/
│       ├── AplusProjectServiceImpl.java  # 项目服务实现
│       ├── AplusCopyServiceImpl.java     # 文案生成服务实现
│       └── AplusImageServiceImpl.java    # 图片生成服务实现
├── entity/
│   ├── AplusProject.java                 # 项目实体
│   └── AplusImageTask.java               # 模块图片任务实体
├── repository/
│   ├── AplusProjectRepository.java       # 项目 Repository
│   └── AplusImageTaskRepository.java     # 模块任务 Repository
├── dto/
│   ├── AplusProjectCreateRequest.java    # 创建项目请求（含 moduleExtras）
│   ├── AplusModuleExtra.java             # 模块补充信息 DTO
│   ├── AplusProjectResponse.java         # 项目响应
│   ├── AplusImageTaskResponse.java       # 模块任务响应
│   └── AplusModuleDefinition.java        # 模块定义常量
└── enums/
    ├── AplusProjectStatus.java           # 项目状态枚举
    └── AplusTaskStatus.java              # 模块任务状态枚举
```

### 2.2 复用的现有服务

```
com.ai.service/
├── TextModelService.java          # 复用：调用 Claude/GPT 生成文案
├── KieClientService.java          # 复用：调用 KIE 图片生成 API
└── OssService.java                # 复用：阿里云 OSS 双桶架构
```

### 2.3 前端文件

```
backend/src/main/resources/static/
├── aplus.html                     # 新增：A+ 套图生成页面
├── js/sidebar.js                  # 修改：添加 A+ 套图入口
└── css/
    ├── variables.css              # 复用：CSS 变量
    └── shared.css                 # 复用：共享样式
```

---

## 3. 任务拆解表

### 任务依赖关系图

```
[T1] 实体与枚举定义
  │
  ├──▶ [T2] Repository 层
  │      │
  │      ├──▶ [T3] 项目服务 (AplusProjectService)
  │      │      │
  │      │      ├──▶ [T4] 文案生成服务 (AplusCopyService)
  │      │      │      │
  │      │      │      └──▶ [T5] 图片生成服务 (AplusImageService)
  │      │      │             │
  │      │      │             └──▶ [T6] 定时轮询任务
  │      │      │
  │      │      └──▶ [T7] 控制器层 (AplusController)
  │      │
  │      └──▶ [T8] 前端页面 (aplus.html)
  │
  └──▶ [T9] 侧边栏集成
```

---

### T1: 实体与枚举定义

| 项目 | 内容 |
|------|------|
| **任务名称** | 定义 A+ 模块的实体类和枚举类 |
| **任务描述** | 创建 AplusProject、AplusImageTask 实体类，以及 AplusProjectStatus、AplusTaskStatus 枚举类 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/entity/AplusProject.java`<br>`backend/src/main/java/com/ai/aplus/entity/AplusImageTask.java`<br>`backend/src/main/java/com/ai/aplus/enums/AplusProjectStatus.java`<br>`backend/src/main/java/com/ai/aplus/enums/AplusTaskStatus.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusModuleDefinition.java` |
| **实现要点** | 1. 实体类使用 `@Entity` + `@Table` + `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`<br>2. 时间字段使用 `@CreationTimestamp` + `@UpdateTimestamp`<br>3. 关联关系使用 `@ManyToOne` / `@OneToMany`<br>4. 枚举类定义状态常量和中文标签映射<br>5. AplusModuleDefinition 定义 7 个模块的 code、name、description |
| **预估工作量** | 小 |
| **前置依赖** | 无 |

**关键代码结构**:

```java
// AplusProjectStatus.java
public enum AplusProjectStatus {
    CREATED("已创建"),
    GENERATING_COPY("生成文案中"),
    COPY_DONE("文案完成"),
    GENERATING_IMAGES("生成图片中"),
    COMPLETED("已完成"),
    FAILED("失败");
    
    private final String label;
    // constructor, getter
}

// AplusModuleDefinition.java
public class AplusModuleDefinition {
    public static final Map<String, String> MODULES = Map.of(
        "AD-01", "品牌英雄图",
        "AD-02", "印花/面料故事图",
        "AD-03", "设计细节图",
        "AD-04", "多场景穿搭图",
        "AD-05", "舒适体验图",
        "AD-06", "尺码/版型指南",
        "AD-07", "护理/品牌收尾图"
    );
    
    // 风格锚点常量
    public static final String STYLE_ANCHOR = "同一个产品、同一个印花、同一个面料、同一个品牌调性、同一个摄影风格、同一个排版体系、同一个色彩体系";
    
    // 各模块补充信息提示（前端 placeholder 用）
    public static final Map<String, String> EXTRA_HINTS = Map.of(
        "AD-01", "品牌调性、目标人群描述",
        "AD-02", "面料成分、工艺说明",
        "AD-03", "设计亮点（口袋/拉链/缝线）",
        "AD-04", "场景描述（海滩/brunch/晚宴）",
        "AD-05", "穿着感受描述",
        "AD-06", "具体尺码数据（S/M/L/XL）",
        "AD-07", "护理说明（洗涤方式、注意事项）"
    );
}

// AplusModuleExtra.java - 模块补充信息 DTO
@Data
public class AplusModuleExtra {
    /** 模块补充参考图 URL（前端上传 OSS 后传入） */
    private String supplementaryImageUrl;
    /** 模块补充文字说明 */
    private String supplementaryText;
}

// AplusProjectCreateRequest.java - 创建项目请求
@Data
public class AplusProjectCreateRequest {
    private String projectName;
    private String spu;
    private String referenceImageUrl;
    private String sellingPoints;
    private List<String> selectedModules;
    /** 各模块补充信息，key 为模块编号如 "AD-02" */
    private Map<String, AplusModuleExtra> moduleExtras;
}
```

---

### T2: Repository 层

| 项目 | 内容 |
|------|------|
| **任务名称** | 创建 AplusProjectRepository 和 AplusImageTaskRepository |
| **任务描述** | 继承 JpaRepository，定义基础查询方法和自定义查询 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/repository/AplusProjectRepository.java`<br>`backend/src/main/java/com/ai/aplus/repository/AplusImageTaskRepository.java` |
| **实现要点** | 1. 继承 `JpaRepository<T, Long>`<br>2. AplusProjectRepository: 按 status、spu 查询，分页支持<br>3. AplusImageTaskRepository: 按 projectId、status 查询，支持批量更新 |
| **预估工作量** | 小 |
| **前置依赖** | T1 |

**关键代码结构**:

```java
// AplusProjectRepository.java
@Repository
public interface AplusProjectRepository extends JpaRepository<AplusProject, Long> {
    Page<AplusProject> findByStatusIn(List<String> statuses, Pageable pageable);
    Page<AplusProject> findBySpuContaining(String spu, Pageable pageable);
    List<AplusProject> findByStatus(String status);
}

// AplusImageTaskRepository.java
@Repository
public interface AplusImageTaskRepository extends JpaRepository<AplusImageTask, Long> {
    List<AplusImageTask> findByProjectId(Long projectId);
    List<AplusImageTask> findByStatus(String status);
    List<AplusImageTask> findByProjectIdAndModuleCode(Long projectId, String moduleCode);
    
    @Modifying
    @Query("UPDATE AplusImageTask t SET t.status = :status WHERE t.projectId = :projectId")
    int updateStatusByProjectId(@Param("projectId") Long projectId, @Param("status") String status);
}
```

---

### T3: 项目服务 (AplusProjectService)

| 项目 | 内容 |
|------|------|
| **任务名称** | 实现项目 CRUD 和状态管理 |
| **任务描述** | 创建项目、获取项目详情、获取项目列表、删除项目、更新项目状态 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/service/AplusProjectService.java`<br>`backend/src/main/java/com/ai/aplus/service/impl/AplusProjectServiceImpl.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusProjectCreateRequest.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusProjectResponse.java` |
| **实现要点** | 1. 创建项目时上传参考图到 OSS 临时桶<br>2. 创建 AplusProject 记录<br>3. 根据 selectedModules 创建 AplusImageTask 记录<br>4. 为有补充数据的模块写入 supplementaryImageUrl 和 supplementaryText<br>5. 支持分页查询（按 spu、status 筛选）<br>6. 删除项目时级联删除关联的 imageTask |
| **预估工作量** | 中 |
| **前置依赖** | T1, T2 |

**关键代码结构**:

```java
// AplusProjectService.java
public interface AplusProjectService {
    AplusProjectResponse createProject(AplusProjectCreateRequest request, String operator, String shopName);
    AplusProjectResponse getProjectById(Long id);
    Page<AplusProjectResponse> getProjectPage(int page, int size, String spu, String status);
    void deleteProject(Long id);
    void updateProjectStatus(Long id, String status, String errorMessage);
}

// AplusProjectServiceImpl.java 核心逻辑
@Service
public class AplusProjectServiceImpl implements AplusProjectService {
    
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    private final OssService ossService;
    
    @Override
    public AplusProjectResponse createProject(AplusProjectCreateRequest request, String operator, String shopName) {
        // 1. 上传参考图到 OSS
        // 2. 创建 AplusProject
        // 3. 根据 selectedModules 创建 AplusImageTask 列表
        // 4. 遍历 request.moduleExtras，为有补充数据的模块:
        //    - 上传 supplementaryImage 到 OSS → supplementaryImageUrl
        //    - 写入 supplementaryText
        // 5. 返回项目信息
    }
}
```

---

### T4: 文案生成服务 (AplusCopyService)

| 项目 | 内容 |
|------|------|
| **任务名称** | 实现文案生成和 MD 文档解析 |
| **任务描述** | 调用 TextModelService 生成 A+ MD 文档，解析为 7 个模块文案 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/service/AplusCopyService.java`<br>`backend/src/main/java/com/ai/aplus/service/impl/AplusCopyServiceImpl.java` |
| **实现要点** | 1. 读取 amazon-listing-generator Skill 模板文件<br>2. 拼接系统 Prompt（包含 Skill 模板 + 模块定义 + 风格锚点）<br>3. 拼接用户 Prompt（参考图 URL + 产品卖点 + 各模块补充文字）<br>4. 补充文字以 "Module-specific instructions" 形式追加到 Prompt 中，引导 AI 针对性生成文案<br>5. 调用 TextModelService.generatePrompt() 生成 MD 文档<br>6. 使用正则解析 MD 文档，提取 AD-01 ~ AD-07 各模块内容<br>7. 更新 AplusProject.aplusMarkdown 和 AplusImageTask.moduleCopy<br>8. 状态流转: CREATED → GENERATING_COPY → COPY_DONE |
| **预估工作量** | 大 |
| **前置依赖** | T3 |

**关键代码结构**:

```java
// AplusCopyService.java
public interface AplusCopyService {
    void generateCopy(Long projectId);
}

// AplusCopyServiceImpl.java 核心逻辑
@Service
public class AplusCopyServiceImpl implements AplusCopyService {
    
    private final TextModelService textModelService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    
    @Value("${aplus.skill.path:}")
    private String skillTemplatePath;
    
    @Override
    public void generateCopy(Long projectId) {
        AplusProject project = projectRepository.findById(projectId).orElseThrow();
        
        // 1. 更新状态为 GENERATING_COPY
        project.setStatus(AplusProjectStatus.GENERATING_COPY.name());
        projectRepository.save(project);
        
        try {
            // 2. 读取 Skill 模板
            String skillTemplate = readSkillTemplate();
            
            // 3. 构建系统 Prompt
            String systemPrompt = buildSystemPrompt(skillTemplate, project.getSelectedModules());
            
            // 4. 构建用户 Prompt
            String userPrompt = buildUserPrompt(project.getReferenceImageUrl(), project.getSellingPoints());
            
            // 5. 调用文本模型
            String markdown = textModelService.generatePrompt(
                new ModelGenerateRequest(systemPrompt, userPrompt), "claude");
            
            // 6. 解析 MD 文档
            Map<String, String> moduleContents = parseMarkdown(markdown);
            
            // 7. 更新数据库
            project.setAplusMarkdown(markdown);
            project.setStatus(AplusProjectStatus.COPY_DONE.name());
            projectRepository.save(project);
            
            // 8. 更新每个模块的文案
            List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
            for (AplusImageTask task : tasks) {
                String content = moduleContents.get(task.getModuleCode());
                if (content != null) {
                    task.setModuleCopy(content);
                    imageTaskRepository.save(task);
                }
            }
        } catch (Exception e) {
            project.setStatus(AplusProjectStatus.FAILED.name());
            project.setErrorMessage("文案生成失败: " + e.getMessage());
            projectRepository.save(project);
        }
    }
    
    // 解析 MD 文档，提取各模块内容
    private Map<String, String> parseMarkdown(String markdown) {
        Map<String, String> result = new HashMap<>();
        // 使用正则匹配 ## AD-01 ~ ## AD-07
        Pattern pattern = Pattern.compile("## (AD-\\d{2})[\\s\\S]*?(?=## AD-\\d{2}|$)");
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            String moduleCode = matcher.group(1);
            String content = matcher.group(0);
            result.put(moduleCode, content);
        }
        return result;
    }
}
```

---

### T5: 图片生成服务 (AplusImageService)

| 项目 | 内容 |
|------|------|
| **任务名称** | 实现图片生成和 Prompt 拼接 |
| **任务描述** | 为每个模块生成独立 Prompt，调用 KieClientService 创建图片生成任务 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/service/AplusImageService.java`<br>`backend/src/main/java/com/ai/aplus/service/impl/AplusImageServiceImpl.java` |
| **实现要点** | 1. 为每个模块生成独立 Prompt（拼接风格锚点 + 模块文案 + 参考图 URL + 补充照片/文字）<br>2. 若模块有 supplementaryImageUrl，将其作为额外参考图传入 KIE<br>3. 若模块有 supplementaryText，追加到 Prompt 的 Module Content 部分<br>4. 调用 KieClientService.createTask() 创建图片生成任务<br>5. 记录 kieTaskId 到 AplusImageTask<br>6. 支持单模块重新生成<br>7. 状态流转: COPY_DONE → GENERATING_IMAGES |
| **预估工作量** | 中 |
| **前置依赖** | T4 |

**关键代码结构**:

```java
// AplusImageService.java
public interface AplusImageService {
    void generateImages(Long projectId);
    void regenerateModule(Long projectId, String moduleCode);
}

// AplusImageServiceImpl.java 核心逻辑
@Service
public class AplusImageServiceImpl implements AplusImageService {
    
    private final KieClientService kieClientService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    
    @Value("${aplus.image.model:nano-banana-pro}")
    private String defaultModel;
    
    @Override
    public void generateImages(Long projectId) {
        AplusProject project = projectRepository.findById(projectId).orElseThrow();
        
        // 1. 更新项目状态
        project.setStatus(AplusProjectStatus.GENERATING_IMAGES.name());
        projectRepository.save(project);
        
        // 2. 获取待生成的模块任务
        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
        
        for (AplusImageTask task : tasks) {
            if (!AplusTaskStatus.PENDING.name().equals(task.getStatus())) {
                continue;
            }
            
            try {
                // 3. 生成 Prompt
                String prompt = buildModulePrompt(task, project);
                task.setPrompt(prompt);
                
                // 4. 调用 KIE 创建任务（如有补充参考图，作为额外参考图传入）
                String kieTaskId = kieClientService.createTask(
                    project.getSpu(),
                    prompt,
                    "2K",
                    "16:9",
                    defaultModel,
                    project.getReferenceImageUrl(),
                    task.getSupplementaryImageUrl()  // 补充参考图，可为 null
                );
                
                // 5. 更新任务状态
                task.setKieTaskId(kieTaskId);
                task.setStatus(AplusTaskStatus.PROCESSING.name());
                task.setModel(defaultModel);
                imageTaskRepository.save(task);
                
            } catch (Exception e) {
                task.setStatus(AplusTaskStatus.FAILED.name());
                task.setErrorMessage(e.getMessage());
                imageTaskRepository.save(task);
            }
        }
    }
    
    // 构建模块 Prompt
    private String buildModulePrompt(AplusImageTask task, AplusProject project) {
        StringBuilder sb = new StringBuilder();
        
        // 1. 风格锚点
        sb.append("Style Anchor: ").append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");
        
        // 2. 模块文案
        sb.append("Module Content:\n").append(task.getModuleCopy()).append("\n\n");
        
        // 3. 模块补充文字（如有）
        if (task.getSupplementaryText() != null && !task.getSupplementaryText().isBlank()) {
            sb.append("Additional Instructions:\n").append(task.getSupplementaryText()).append("\n\n");
        }
        
        // 4. 参考图（主参考图）
        sb.append("Reference Image: ").append(project.getReferenceImageUrl()).append("\n\n");
        
        // 5. 模块补充参考图（如有）
        if (task.getSupplementaryImageUrl() != null && !task.getSupplementaryImageUrl().isBlank()) {
            sb.append("Supplementary Reference Image: ").append(task.getSupplementaryImageUrl()).append("\n\n");
        }
        
        // 6. 图片规格
        sb.append("Image Specification: 16:9 aspect ratio, 2K resolution\n");
        
        return sb.toString();
    }
}
```

---

### T6: 定时轮询任务

| 项目 | 内容 |
|------|------|
| **任务名称** | 实现定时轮询 KIE 任务状态 |
| **任务描述** | 使用 @Scheduled 定时轮询处理中的任务，更新结果 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/service/impl/AplusImageServiceImpl.java`（在 T5 中实现） |
| **实现要点** | 1. 使用 `@Scheduled(fixedRate = 30000)` 每 30 秒执行<br>2. 查询所有 status=PROCESSING 的 AplusImageTask<br>3. 调用 KieClientService.getFullResult() 查询任务状态<br>4. 成功时：下载临时 URL → 转存 OSS 永久桶 → 更新 resultOssUrl<br>5. 失败时：记录 errorMessage<br>6. 当所有模块完成时，更新项目状态为 COMPLETED<br>7. 使用 CompletableFuture 并发处理多个任务 |
| **预估工作量** | 中 |
| **前置依赖** | T5 |

**关键代码结构**:

```java
// 在 AplusImageServiceImpl.java 中添加

@Scheduled(fixedRate = 30000) // 30 秒轮询
public void pollTaskStatus() {
    List<AplusImageTask> processingTasks = imageTaskRepository.findByStatus(AplusTaskStatus.PROCESSING.name());
    
    if (processingTasks.isEmpty()) {
        return;
    }
    
    log.info("【A+ 轮询】发现 {} 个处理中的任务", processingTasks.size());
    
    // 并发处理
    List<CompletableFuture<Void>> futures = processingTasks.stream()
        .map(task -> CompletableFuture.runAsync(() -> refreshTask(task), pollingPool))
        .collect(Collectors.toList());
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}

private void refreshTask(AplusImageTask task) {
    try {
        KieTaskResult result = kieClientService.getFullResult(task.getKieTaskId());
        
        if ("SUCCESS".equalsIgnoreCase(result.getStatus())) {
            // 1. 保存临时 URL
            task.setResultTempUrl(result.getResultUrl());
            task.setStatus(AplusTaskStatus.SUCCESS.name());
            task.setCompletedAt(LocalDateTime.now());
            imageTaskRepository.save(task);
            
            // 2. 转存到 OSS 永久桶
            try {
                String ossUrl = ossService.uploadResultToOss(
                    task.getProject().getSpu(),
                    result.getResultUrl(),
                    task.getId(),
                    true // 永久桶
                );
                task.setResultOssUrl(ossUrl);
                imageTaskRepository.save(task);
            } catch (Exception e) {
                log.error("【A+ 轮询】转存 OSS 失败: {}", e.getMessage());
            }
            
            // 3. 检查是否所有模块都完成
            checkProjectCompletion(task.getProject().getId());
            
        } else if ("FAILED".equalsIgnoreCase(result.getStatus())) {
            task.setStatus(AplusTaskStatus.FAILED.name());
            task.setErrorMessage(result.getErrorMessage());
            imageTaskRepository.save(task);
        }
        
    } catch (Exception e) {
        log.error("【A+ 轮询】刷新任务失败: {}", e.getMessage());
    }
}

private void checkProjectCompletion(Long projectId) {
    List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
    boolean allCompleted = tasks.stream()
        .allMatch(t -> AplusTaskStatus.SUCCESS.name().equals(t.getStatus()) 
                    || AplusTaskStatus.FAILED.name().equals(t.getStatus()));
    
    if (allCompleted) {
        AplusProject project = projectRepository.findById(projectId).orElseThrow();
        project.setStatus(AplusProjectStatus.COMPLETED.name());
        project.setCompletedAt(LocalDateTime.now());
        projectRepository.save(project);
    }
}
```

---

### T7: 控制器层 (AplusController)

| 项目 | 内容 |
|------|------|
| **任务名称** | 实现 REST API 控制器 |
| **任务描述** | 创建 AplusController，实现所有 API 接口 |
| **涉及文件** | `backend/src/main/java/com/ai/aplus/controller/AplusController.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusProjectCreateRequest.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusProjectResponse.java`<br>`backend/src/main/java/com/ai/aplus/dto/AplusImageTaskResponse.java` |
| **实现要点** | 1. 使用 `@RestController` + `@RequestMapping("/api/aplus")`<br>2. 使用 `@RequiredArgsConstructor` 注入服务<br>3. 使用 `@CrossOrigin` 允许跨域<br>4. 从 HttpServletRequest 获取 operator 和 shopName<br>5. 所有接口返回 `ApiResponse<T>`<br>6. 异步调用文案生成和图片生成（先返回项目 ID，后台异步执行） |
| **预估工作量** | 中 |
| **前置依赖** | T3, T4, T5 |

**关键代码结构**:

```java
@RestController
@RequestMapping("/api/aplus")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
public class AplusController {
    
    private final AplusProjectService projectService;
    private final AplusCopyService copyService;
    private final AplusImageService imageService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    
    // 创建项目
    @PostMapping("/projects")
    public ApiResponse<AplusProjectResponse> createProject(
            @RequestBody AplusProjectCreateRequest request,
            HttpServletRequest httpRequest) {
        String operator = (String) httpRequest.getAttribute("operator");
        String shopName = (String) httpRequest.getAttribute("shopName");
        AplusProjectResponse response = projectService.createProject(request, operator, shopName);
        return ApiResponse.ok("A+ 项目创建成功", response);
    }
    
    // 获取项目详情
    @GetMapping("/projects/{id}")
    public ApiResponse<AplusProjectResponse> getProject(@PathVariable Long id) {
        AplusProjectResponse response = projectService.getProjectById(id);
        return ApiResponse.ok("ok", response);
    }
    
    // 获取项目列表
    @GetMapping("/projects")
    public ApiResponse<Page<AplusProjectResponse>> getProjectPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String spu,
            @RequestParam(required = false) String status) {
        Page<AplusProjectResponse> result = projectService.getProjectPage(page, size, spu, status);
        return ApiResponse.ok("ok", result);
    }
    
    // 触发文案生成（异步）
    @PostMapping("/projects/{id}/generate-copy")
    public ApiResponse<String> generateCopy(@PathVariable Long id) {
        CompletableFuture.runAsync(() -> copyService.generateCopy(id));
        return ApiResponse.ok("文案生成任务已提交", null);
    }
    
    // 触发图片生成（异步）
    @PostMapping("/projects/{id}/generate-images")
    public ApiResponse<String> generateImages(@PathVariable Long id) {
        CompletableFuture.runAsync(() -> imageService.generateImages(id));
        return ApiResponse.ok("图片生成任务已提交", null);
    }
    
    // 重新生成单个模块
    @PostMapping("/projects/{id}/modules/{moduleCode}/regenerate")
    public ApiResponse<String> regenerateModule(@PathVariable Long id, @PathVariable String moduleCode) {
        CompletableFuture.runAsync(() -> imageService.regenerateModule(id, moduleCode));
        return ApiResponse.ok(moduleCode + " 重新生成任务已提交", null);
    }
    
    // 获取模块任务状态
    @GetMapping("/projects/{id}/modules")
    public ApiResponse<List<AplusImageTaskResponse>> getModuleTasks(@PathVariable Long id) {
        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(id);
        List<AplusImageTaskResponse> responses = tasks.stream()
            .map(AplusImageTaskResponse::new)
            .collect(Collectors.toList());
        return ApiResponse.ok("ok", responses);
    }
    
    // 批量下载 ZIP
    @PostMapping("/projects/{id}/download-zip")
    public void downloadZip(@PathVariable Long id, HttpServletResponse response) {
        // 实现 ZIP 打包下载
    }
    
    // 单张代理下载
    @GetMapping("/proxy-download")
    public void proxyDownload(@RequestParam String url, @RequestParam String filename,
                             HttpServletResponse response) {
        // 实现代理下载
    }
    
    // 删除项目
    @DeleteMapping("/projects/{id}")
    public ApiResponse<String> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.ok("项目删除成功", null);
    }
}
```

---

### T8: 前端页面 (aplus.html)

| 项目 | 内容 |
|------|------|
| **任务名称** | 创建 A+ 套图生成前端页面 |
| **任务描述** | 使用 Vue 3 + Element Plus 创建独立的 A+ 套图生成页面 |
| **涉及文件** | `backend/src/main/resources/static/aplus.html` |
| **实现要点** | 1. 复用现有项目的 CSS 变量和共享样式<br>2. 使用 Vue 3 Composition API<br>3. 使用 Element Plus 组件库<br>4. 使用 Axios 发送 HTTP 请求<br>5. 实现项目创建表单（项目名称、SPU、参考图上传、卖点、模块选择）<br>6. 实现模块卡片展示（图片、状态、操作按钮）<br>7. 实现历史项目列表<br>8. 实现轮询机制（每 5 秒刷新项目状态）<br>9. 实现单张下载和批量下载 |
| **预估工作量** | 大 |
| **前置依赖** | T7 |

**关键代码结构**:

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>A+ 套图生成 — AI 电商视觉平台</title>
    <link rel="stylesheet" href="css/variables.css">
    <link rel="stylesheet" href="css/shared.css">
    <script src="https://unpkg.com/vue@3/dist/vue.global.js"></script>
    <link rel="stylesheet" href="https://unpkg.com/element-plus/dist/index.css"/>
    <script src="https://unpkg.com/element-plus"></script>
    <script src="https://unpkg.com/axios/dist/axios.min.js"></script>
    <script src="auth.js"></script>
    <script src="js/sidebar.js"></script>
</head>
<body>
<div class="app-layout">
    <div class="app-main">
        <div id="app">
            <!-- 项目创建表单 -->
            <!-- 模块选择卡片 -->
            <!-- 生成结果展示 -->
            <!-- 历史项目列表 -->
        </div>
    </div>
</div>

<script>
const { createApp, ref, reactive, computed, onUnmounted } = Vue;

createApp({
    setup() {
        // 状态定义
        const projectName = ref('');
        const spu = ref('');
        const referenceImage = ref(null);
        const sellingPoints = ref('');
        const selectedModules = ref(['AD-01', 'AD-02', 'AD-03', 'AD-04', 'AD-05', 'AD-06', 'AD-07']);
        const currentProject = ref(null);
        const projectHistory = ref([]);
        
        // 模块定义（含补充信息提示）
        const modules = [
            { code: 'AD-01', name: '品牌英雄图', desc: '宽幅生活方式横幅', extraHint: '品牌调性、目标人群' },
            { code: 'AD-02', name: '印花/面料故事图', desc: '左右分栏', extraHint: '面料成分、工艺说明' },
            { code: 'AD-03', name: '设计细节图', desc: '中心+四周', extraHint: '设计亮点（口袋/拉链/缝线）' },
            { code: 'AD-04', name: '多场景穿搭图', desc: '三宫格', extraHint: '场景描述（海滩/brunch/晚宴）' },
            { code: 'AD-05', name: '舒适体验图', desc: '主角+角落', extraHint: '穿着感受描述' },
            { code: 'AD-06', name: '尺码/版型指南', desc: '技术图表', extraHint: '具体尺码数据（S/M/L/XL）' },
            { code: 'AD-07', name: '护理/品牌收尾图', desc: '静物+说明', extraHint: '护理说明（洗涤方式）' }
        ];
        
        // 模块补充信息（每模块独立的照片和文字）
        const moduleExtras = reactive({});
        // 结构: { 'AD-02': { imageFile: null, imageUrl: '', text: '' }, ... }
        
        const expandedModules = ref([]); // 展开补充信息的模块列表
        
        const toggleModuleExpand = (code) => {
            const idx = expandedModules.value.indexOf(code);
            if (idx >= 0) expandedModules.value.splice(idx, 1);
            else expandedModules.value.push(code);
        };
        
        // 创建项目
        const createProject = async () => {
            // 1. 上传参考图到 OSS → referenceImageUrl
            // 2. 上传各模块补充照片到 OSS → moduleExtras[code].imageUrl
            // 3. 组装请求体（含 moduleExtras）
            // 4. 调用 POST /api/aplus/projects
            // 5. 触发文案生成
            // 6. 开始轮询
        };
        
        // 轮询项目状态
        let pollTimer = null;
        const startPolling = () => {
            pollTimer = setInterval(async () => {
                if (!currentProject.value) return;
                const res = await axios.get(`/api/aplus/projects/${currentProject.value.id}`);
                if (res.data?.success) {
                    currentProject.value = res.data.data;
                    // 检查是否完成
                    if (currentProject.value.status === 'COMPLETED') {
                        stopPolling();
                    }
                }
            }, 5000);
        };
        
        const stopPolling = () => {
            if (pollTimer) {
                clearInterval(pollTimer);
                pollTimer = null;
            }
        };
        
        // 下载相关
        const downloadSingle = (task) => {
            const url = task.resultOssUrl || task.resultTempUrl;
            window.open(`/api/aplus/proxy-download?url=${encodeURIComponent(url)}&filename=${task.moduleCode}.jpg`);
        };
        
        const downloadZip = async () => {
            const res = await axios.post(`/api/aplus/projects/${currentProject.value.id}/download-zip`, {}, {
                responseType: 'blob'
            });
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const a = document.createElement('a');
            a.href = url;
            a.download = `A+_${currentProject.value.projectName}.zip`;
            a.click();
        };
        
        onUnmounted(() => stopPolling());
        
        return {
            projectName, spu, referenceImage, sellingPoints, selectedModules,
            currentProject, projectHistory, modules,
            createProject, downloadSingle, downloadZip
        };
    }
}).use(ElementPlus).mount('#app');
</script>
</body>
</html>
```

---

### T9: 侧边栏集成

| 项目 | 内容 |
|------|------|
| **任务名称** | 在侧边栏添加 A+ 套图入口 |
| **任务描述** | 修改 sidebar.js，添加 A+ 套图菜单项 |
| **涉及文件** | `backend/src/main/resources/static/js/sidebar.js` |
| **实现要点** | 1. 在侧边栏导航中添加 A+ 套图链接<br>2. 使用合适的图标（如 📦 或 🎨）<br>3. 添加 active 状态判断 |
| **预估工作量** | 小 |
| **前置依赖** | 无 |

**关键代码结构**:

```javascript
// 在 sidebar.js 的 sidebar.innerHTML 中添加
'  <a href="aplus.html" class="sidebar-link' + (isActive('aplus.html') ? ' active' : '') + '">',
'    <span class="sidebar-link-icon">📦</span><span>A+ 套图</span>',
'  </a>',
```

---

## 4. 关键设计决策

### 4.1 Q1: Skill 模板文件路径

**问题**: Skill 模板文件路径需要确认

**建议方案**: 
- 使用配置化方式，在 `application.yml` 中添加配置项：
  ```yaml
  aplus:
    skill:
      path: ${APLUS_SKILL_PATH:C:/Users/Administrator/.workbuddy/skills/amazon-listing-generator/SKILL.md}
  ```
- 在 AplusCopyServiceImpl 中通过 `@Value("${aplus.skill.path}")` 注入
- 支持环境变量覆盖，便于部署

### 4.2 Q2: 文案生成是否异步

**问题**: 调用 TextModelService 生成 MD 文档可能耗时较长

**建议方案**: **异步处理**
- 创建项目接口立即返回项目 ID
- 文案生成通过 `CompletableFuture.runAsync()` 异步执行
- 前端通过轮询获取状态更新
- 项目状态流转: CREATED → GENERATING_COPY → COPY_DONE

**优势**:
- 用户体验好，不需要等待长时间的文案生成
- 支持并发处理多个项目
- 便于错误处理和重试

### 4.3 Q3: 是否支持用户编辑 Prompt

**问题**: 是否需要支持用户编辑 Prompt 后再生成图片

**建议方案**: **第一期不支持，P2 阶段实现**
- 第一期专注于核心流程：上传 → 生成文案 → 生成图片
- P2 阶段可以添加 Prompt 预览和编辑功能
- 可以在前端添加"查看 Prompt"按钮，但不支持编辑

### 4.4 Q4: 图片生成使用的具体模型

**问题**: A+ 图片生成使用的具体模型

**建议方案**: 
- 默认使用 `nano-banana-pro` 模型（与场景生成一致）
- 支持用户在前端选择其他模型（如 gpt-image-2-image-to-image）
- 在 AplusImageServiceImpl 中通过配置项设置默认模型：
  ```yaml
  aplus:
    image:
      model: nano-banana-pro
      resolution: 2K
      aspect-ratio: 16:9
  ```

### 4.5 Q5: 是否需要支持其他站点

**问题**: 是否需要支持 Amazon 其他站点的 A+ 规范

**建议方案**: **第一期仅支持 US 站**
- 第一期专注于 US 站的 A+ 规范
- 后续可以通过配置化方式扩展其他站点
- 模块定义可以抽象为配置文件，便于扩展

### 4.6 Q6: 费用计算方式

**问题**: 费用计算方式

**建议方案**: **按模块计费，汇总到项目**
- 每个 AplusImageTask 记录独立的 cost 字段
- 项目级别的 totalCost 通过查询汇总计算
- 支持按项目、按时间段统计费用

### 4.7 Q7: 是否需要支持视频模块

**问题**: 是否需要支持视频模块（AV-01）

**建议方案**: **第一期仅支持图片**
- 第一期专注于 7 个图片模块（AD-01 ~ AD-07）
- P2 阶段可以扩展视频模块
- 数据库设计预留扩展性（moduleCode 字段支持 AV-xx）

### 4.8 Q8: 历史项目数据保留策略

**问题**: 历史项目数据保留策略

**建议方案**: **保留 90 天，超期自动归档**
- 添加定时任务，每天清理 90 天前的项目
- 清理时同时删除 OSS 临时桶中的文件
- 永久桶中的文件保留（用户可能需要下载）
- 可以通过配置项调整保留天数：
  ```yaml
  aplus:
    retention:
      days: 90
  ```

---

## 5. 数据库表设计（最终版）

### 5.1 aplus_project 表

```sql
CREATE TABLE aplus_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    spu VARCHAR(100) NOT NULL COMMENT '产品 SPU 编号',
    reference_image_url VARCHAR(500) NOT NULL COMMENT '产品参考图 OSS URL',
    selling_points TEXT COMMENT '产品卖点',
    aplus_markdown TEXT COMMENT 'AI 生成的 A+ MD 文档',
    selected_modules TEXT COMMENT '选择的模块列表 JSON',
    status VARCHAR(30) NOT NULL COMMENT '项目状态',
    error_message TEXT COMMENT '错误信息',
    operator VARCHAR(50) COMMENT '操作人',
    shop_name VARCHAR(100) COMMENT '所属店铺',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME COMMENT '完成时间',
    INDEX idx_status (status),
    INDEX idx_spu (spu),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A+ 套图项目表';
```

### 5.2 aplus_image_task 表

```sql
CREATE TABLE aplus_image_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目 ID',
    module_code VARCHAR(10) NOT NULL COMMENT '模块编号 AD-01 ~ AD-07',
    module_name VARCHAR(50) NOT NULL COMMENT '模块名称',
    module_copy TEXT COMMENT '模块文案',
    supplementary_image_url VARCHAR(500) COMMENT '模块补充参考图 URL',
    supplementary_text TEXT COMMENT '模块补充文字说明',
    prompt TEXT COMMENT '生成的 Prompt',
    aspect_ratio VARCHAR(20) COMMENT '图片比例 16:9',
    kie_task_id VARCHAR(128) COMMENT 'KIE 平台任务 ID',
    model VARCHAR(64) COMMENT '使用的模型',
    status VARCHAR(20) NOT NULL COMMENT '任务状态',
    result_temp_url VARCHAR(500) COMMENT 'KIE 临时结果 URL',
    result_oss_url VARCHAR(500) COMMENT 'OSS 永久 URL',
    error_message TEXT COMMENT '错误信息',
    cost DECIMAL(10,2) COMMENT '预估费用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME COMMENT '完成时间',
    FOREIGN KEY (project_id) REFERENCES aplus_project(id) ON DELETE CASCADE,
    INDEX idx_project_id (project_id),
    INDEX idx_status (status),
    INDEX idx_kie_task_id (kie_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A+ 模块图片生成任务表';
```

---

## 6. 配置项汇总

```yaml
# application.yml 新增配置

aplus:
  # Skill 模板路径
  skill:
    path: ${APLUS_SKILL_PATH:C:/Users/Administrator/.workbuddy/skills/amazon-listing-generator/SKILL.md}
  
  # 图片生成配置
  image:
    model: nano-banana-pro
    resolution: 2K
    aspect-ratio: 16:9
  
  # 轮询配置
  polling:
    fixed-rate: 30000  # 30 秒
  
  # 数据保留配置
  retention:
    days: 90
```

---

## 7. 总结

### 7.1 开发顺序建议

1. **第一阶段（基础框架）**: T1 → T2 → T9
2. **第二阶段（核心服务）**: T3 → T4 → T5 → T6
3. **第三阶段（接口与前端）**: T7 → T8

### 7.2 风险点

1. **Skill 模板解析**: 需要确认 Skill 模板的实际格式，可能需要调整正则表达式
2. **MD 文档解析**: AI 生成的 MD 文档格式可能不一致，需要健壮的解析逻辑
3. **KIE 任务轮询**: 需要处理网络异常和超时情况
4. **OSS 转存**: 需要处理大文件下载和上传的超时问题

### 7.3 扩展性考虑

1. **模块扩展**: 通过 AplusModuleDefinition 配置化，便于添加新模块
2. **模型扩展**: 通过配置项支持多种图片生成模型
3. **站点扩展**: 通过配置文件支持不同 Amazon 站点的 A+ 规范
4. **视频模块**: 数据库设计预留扩展性，便于后续添加视频模块

---

*文档结束*
