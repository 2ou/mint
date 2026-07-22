# AI 画布接入说明

本模块已从 Tapnow 画布切换为 `hero8152/Infinite-Canvas` 前端。

## 入口

- 系统内入口：`/ai-canvas.html`
- Infinite-Canvas 工作台：`/infinite-canvas/canvas-list.html`
- 画布模板入口：`/ai-canvas-templates.html`

系统内入口会在项目侧边栏中打开 Infinite-Canvas 工作台。工作台支持创建项目、创建普通画布、创建智能画布、回收站、重命名、移动、导出画布 JSON 等 Infinite-Canvas 原生前端能力。

## 项目集成

- Infinite-Canvas 静态资源放在 `backend/src/main/resources/static/infinite-canvas`。
- `InfiniteCanvasController` 提供 `/api/projects`、`/api/canvases`、`/api/canvas-image-tasks`、`/api/canvas-video`、`/api/canvas-llm`、`/api/ai/upload` 等兼容接口。
- 画布数据复用原项目的 `ai_canvas_project` 表，`meta_json.kind = infinite-canvas` 的记录表示 Infinite-Canvas 画布。
- 工作台项目列表保存在一条内部记录中，`project_name = __infinite_canvas_workspace__`。
- 前端补丁脚本为 `/infinite-canvas/js/project-bridge.js`，负责自动携带 `X-User-Token`、注入项目 KIE 模型配置、隐藏 API 设置入口，并提供保存为模板入口。

## 模型调用

- 文本节点：走项目已有 `TextModelService`，模型为 GPT 5.6 Sol / Terra / Luna。
- 图片节点：走项目已有 KIE 图片任务，前端轮询 `/api/canvas-image-tasks/{taskId}`。
- 视频节点：走项目已有 KIE 视频任务，当前兼容接口会等待任务完成后返回视频 URL。
- API Key 不在前端配置或暴露，统一由后端读取原项目配置。

## 模板

画布编辑器顶部会注入「保存为模板」和「模板库」按钮。

模板快照以 `snapshot.infinite_canvas` 保存完整画布 JSON。使用模板时会创建一条新的 Infinite-Canvas 画布记录，不覆盖原模板。

## 许可证

`hero8152/Infinite-Canvas` 仓库声明禁止商业用途，个人使用场景可以接入。许可证和 README 已保留在：

- `backend/src/main/resources/static/infinite-canvas/LICENSE.hero8152-Infinite-Canvas.txt`
- `backend/src/main/resources/static/infinite-canvas/README.hero8152-Infinite-Canvas.md`
