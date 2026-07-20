# AI 画布接入说明

本模块基于 `chapterv/Tapnow-Studio-PP` 的前端构建产物接入，入口为：

- 系统内入口：`/ai-canvas.html`
- 画布模板入口：`/ai-canvas-templates.html`
- Tapnow 直接入口：`/ai-canvas/index.html`

建议优先使用系统内入口。系统内入口会先注入项目模型、恢复最近一次画布记录，再加载 Tapnow 画布。

## 产品闭环

- 顶部「保存」会把当前画布快照写入后端。
- 顶部「保存为模板」会把当前画布快照写入独立模板库。
- 顶部「保存记录」会读取当前登录用户、当前店铺下的画布记录。
- 顶部「新建」会先自动保存当前画布，再清空 Tapnow 本地状态并创建空白画布。
- 外层桥接脚本每 8 秒自动保存一次 `tapnow_*` 本地状态。
- 侧边栏「AI 画布」下包含「画布工作台」和「画布模板」两个入口。
- 「画布模板」读取独立模板表，支持分类、标签、备注、使用模板、复制为新画布和删除模板。

保存记录落库表：

`ai_canvas_project`

画布模板落库表：

`ai_canvas_template`

生成任务状态落库表：

`ai_canvas_task`

主要字段：

- `project_name`：画布名称。
- `operator` / `shop_name`：从项目登录 token 解析出的操作人与店铺。
- `snapshot_json`：Tapnow 本地画布快照。
- `meta_json`：保存来源、保存时间、浏览器信息。

模板表额外保存：

- `template_name`：模板名称。
- `category`：模板分类。
- `tags_json`：模板标签。
- `cover_image_url`：模板封面 URL。
- `description`：模板备注。

## 模型接入

系统入口会自动注入三个项目模型：

- `项目文本模型`：走 `/api/canvas/kie/v1/chat/completions`，后端使用项目当前 KIE 配置调用文本模型。
- `项目图片模型`：走 `/api/canvas/kie/v1/images/generations`，后端创建 KIE 图片任务，前端轮询 `/api/canvas/kie/v1/images/tasks/{taskId}`。
- `项目视频模型`：走 `/api/canvas/kie/v1/videos/generations`，后端创建 KIE 视频任务，前端按 Tapnow 原生视频任务轮询路径读取结果。

浏览器端只保存 `project-kie-local` 占位 key，用于通过 Tapnow 自身的已配置检查；真实 KIE API Key 仍然只在后端配置中读取。所有 `/api/canvas/**` 请求都会携带项目登录态 `X-User-Token`。

系统内入口会隐藏 Tapnow 右上角的模型接口/API 配置入口，避免前端暴露或要求用户手动配置 API。模型调用统一走项目后端代理。

本地 `dev` 环境将 `app.kie.callback-url` 置空，图片/视频任务结果由后端轮询 KIE；线上环境保留公网 callback 地址，KIE 回调写入 `ai_canvas_task`，前端仍统一轮询项目后端状态接口。

## 本地服务

Tapnow 的素材保存、本地缓存、HTTP 代理、ComfyUI 中转等能力依赖本地服务，代码放在：

`tools/tapnow-localserver`

启动方式：

```powershell
powershell -ExecutionPolicy Bypass -File tools/start-ai-canvas-localserver.ps1
```

默认服务地址：

`http://127.0.0.1:9527`

## 第三方代码

Tapnow-Studio-PP 使用 GPLv3 许可证。本项目当前按个人自用方式接入，许可证文本已保留在：

`backend/src/main/resources/static/ai-canvas/LICENSE.Tapnow-Studio-PP.txt`

`tools/tapnow-localserver/LICENSE.Tapnow-Studio-PP.txt`
