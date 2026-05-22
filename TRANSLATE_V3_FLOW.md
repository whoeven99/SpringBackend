# Translate V3 核心流程说明（给后续 AI）

本文档用于快速理解 `TranslateV3Service` 的主流程：**初始化 -> 翻译 -> 写入 Shopify -> 翻译质量评估 -> 写入结果校验**。

## 1) 总览

- 核心服务：`BogdaService/src/main/java/com/bogda/service/logic/translate/TranslateV3Service.java`
- 任务调度：`BogdaTask/src/main/java/com/bogda/task/task/TranslateTaskV3Scheduled.java`
- API 入口：`BogdaApi/src/main/java/com/bogda/api/controller/TranslateController.java` 的 `PUT /translate/clickTranslation`
- 任务主存储：Cosmos `TranslateTaskV3DO`
- 中间数据存储：Blob `tasks/{taskId}/chunks/...`
- 进度监控：Redis `translate_monitor_v3:{taskId}`

## 2) 任务状态机（status）

基于代码中的读取/写入行为，`status` 流转如下：

- `0`：初始化待处理（INIT 阶段）
- `1`：待翻译（TRANSLATE 阶段）
- `2`：待写入 Shopify（SAVE 阶段）
- `3`：因 token 上限自动停止（可续跑）
- `4`：停止（例如店铺主语言与 source 不一致）
- `6`：写入完成，待/已做写入校验（VERIFY 阶段）

主调度周期：

- init/translate/save：每 30 秒
- verify：每 60 秒

## 3) Init（初始化）

### 3.1 创建任务

`createInitialTask(request)` 负责创建任务元数据：

- 校验参数、token 配额、目标语言可用性
- 按 target 逐个创建 `taskId`
- 初始 checkpoint：
  - `phase = INIT_CREATED`
- 初始 metrics：
  - `totalCount=0, translatedCount=0, savedCount=0, usedToken=0`
- 写入 Cosmos 后，创建 Redis monitor 记录

### 3.2 读取 Shopify 并落盘 chunk

`processInitialTasksV3()` 拉取 `status=0` 的任务，调用 `initialToTranslateTaskV3(task)`：

- 设置监控 phase：`INIT_READING_SHOPIFY`
- 校验用户和 accessToken
- 读取店铺 primary locale，若与 `task.source` 不一致：
  - phase = `INIT_STOPPED_PRIMARY_LOCALE_MISMATCH`
  - status -> `4`
- 解析模块列表 `moduleList`，为空则直接 status -> `1`
- 对每个 module 执行 `dumpModuleToBlob(...)`：
  - 遍历 Shopify translatable content
  - 过滤不可翻译字段（如 URL、部分 handle 场景等）
  - 按 200 条切 chunk，写入：
    - `tasks/{taskId}/chunks/{module}/chunk-{i}.json`
  - 每行基础字段：
    - `resourceId, module, nodeKey, type, digest, sourceValue, isHtml, isJson`
- 汇总后写入：
  - `tasks/{taskId}/chunks/manifest.json`
- 更新 checkpoint/metrics，并将 status -> `1`

## 4) 翻译执行（Translate）

`processTranslateTasksV3()` 拉取 `status=1`，调用 `translateEachTaskV3(task)`。

### 4.1 主流程

- phase -> `TRANSLATE_RUNNING`
- 再次校验 primary locale（防止运行中配置变化）
- 读取 glossary、token 配额、历史 metrics
- 按 module -> chunk 顺序处理
- 每个 chunk 读出 rows 后调用 `translateChunkRows(...)`
- 翻译后直接覆盖原 chunk 文件（v3 是“同文件前后态”）
- 累计 translatedCount/usedToken，定期刷新 checkpoint

### 4.2 行级翻译策略

`translateChunkRows(...)` 对每行做分流：

- 已翻译且有 `targetValue`：跳过（支持断点续跑）
- 结构化内容（JSON/HTML/特定类型）：
  - 调 `universalTranslateService.translateStructuredContent(...)`
  - 结构校验失败则打 `translateError` 并回退为未成功
- 普通文本：
  - 聚合成 batch（估算 token，单批约束 600）
  - 调 `flushBatchRows(...)` -> `translateBatchContent(...)`
  - 写回 `targetValue, translated, translatedAt`

### 4.3 token 上限与终态

- 若 `currentUsedToken >= maxToken`：
  - checkpoint phase = `TRANSLATE_STOPPED_TOKEN_LIMIT`
  - status -> `3`
- 全部完成：
  - checkpoint phase = `TRANSLATE_DONE`
  - status -> `2`
- 翻译结束后会触发两类质量报告（见第 6 节）

## 5) 写入 Shopify（Save）

`processSaveTasksV3()` 拉取 `status=2`，调用 `saveToShopifyV3(task)`。

### 5.1 写入核心逻辑

- phase -> `SAVE_RUNNING`
- 先恢复历史保存进度 `recoverSavedCountFromProgress(...)`
- 对每个 module/chunk：
  - 读 chunk rows
  - 读 `chunk.save-progress` 里已保存行索引
  - `groupUnsavedRowsByResource(...)` 按 resourceId 分组
  - 对每个 resource 调 `saveOneResourceRows(...)`
    - 内部按推荐批次调用 `shopifyService.saveDataWithRateLimit(...)`
    - 成功则返回保存成功的 row indexes
  - 立即写回 save-progress：
    - `tasks/{taskId}/chunks/{module}/chunk-{i}.json.save-progress`
  - 更新 savedCount 与 checkpoint

### 5.2 部分失败与完成

- 任意资源写入失败：
  - phase = `SAVE_PARTIAL_FAILED`
  - 保持当前 status（仍是 `2`，下轮可重试未完成部分）
- 全部完成：
  - `markSaveDone(...)`
  - phase = `SAVE_DONE`
  - status -> `6`

## 6) 翻译质量（QA）

翻译完成后（在 `translateEachTaskV3` 末尾）自动产出两种质量结果。

### 6.1 规则质量报告（deterministic）

`generateQaReport(taskId, modules)` 产出：

- 文件：`tasks/{taskId}/chunks/qa-report.json`
- 统计项：
  - `missingTargetCount`
  - `sameAsSourceCount`
  - `invalidJsonCount`
  - `htmlStructureRiskCount`
  - `placeholderRiskCount`
- 附带最多 200 条 issue sample（含 module/chunkPath/rowIndex）

### 6.2 AI 质量评分（LLM）

`generateAiScoreReport(task, modules)`：

- 每模块抽样 translated rows（带固定 seed，可复现）
- 构造评分 prompt，要求模型返回严格 JSON
- 评分维度：accuracy/fluency/formatPreservation/terminologyConsistency
- 输出：
  - 模块级：`tasks/{taskId}/chunks/{module}/ai-score.json`
  - 汇总级：`tasks/{taskId}/qa/ai-score.json`

## 7) 检查写入数据（Verify Save）

`processVerifyTasksV3()` 拉取 `status=6` 的任务并执行 `verifySavedDataV3(task)`，但会跳过已终态校验任务：

- 若 checkpoint phase 已是 `VERIFY_DONE` 或 `VERIFY_MISMATCH_DONE`，不重复校验

### 7.1 校验方法

- phase -> `VERIFY_RUNNING`
- 仅针对 save-progress 标记为“已写入”的行进行核对
- 按 resourceId 回拉 Shopify 实际翻译 `fetchShopifyTranslationsByResourceIds(...)`
- 对比 row 的 `targetValue` 与 Shopify 实际值：
  - 一致：matched
  - 资源不存在：missing (`RESOURCE_NOT_FOUND`)
  - key 不存在：missing (`KEY_NOT_FOUND`)
  - 值不一致：mismatch (`VALUE_MISMATCH`)

### 7.2 校验输出

- 报告文件：
  - `tasks/{taskId}/qa/save-verify.json`
- metrics 新增：
  - `verifyTotal, verifyMatched, verifyMismatch, verifyMissing`
- checkpoint phase：
  - 无差异：`VERIFY_DONE`
  - 有差异/缺失：`VERIFY_MISMATCH_DONE`

## 8) 关键数据文件清单

- 模块清单：`tasks/{taskId}/chunks/manifest.json`
- 翻译 chunk：`tasks/{taskId}/chunks/{module}/chunk-{i}.json`
- 保存进度：`tasks/{taskId}/chunks/{module}/chunk-{i}.json.save-progress`
- 规则 QA：`tasks/{taskId}/chunks/qa-report.json`
- AI 评分汇总：`tasks/{taskId}/qa/ai-score.json`
- 写入校验：`tasks/{taskId}/qa/save-verify.json`

## 9) 给后续 AI 的快速判断建议

- 先看 `status` + checkpoint.phase，判断卡在哪个阶段
- 再看 metrics（`translatedCount/savedCount/verifyMismatch`）判断是否“推进中还是质量问题”
- 如果 `status=2` 且 phase=`SAVE_PARTIAL_FAILED`，优先排查 Shopify 写入返回（节流/参数）
- 如果 `status=6` 但 phase=`VERIFY_MISMATCH_DONE`，优先看 `qa/save-verify.json` 的样本
- 若出现“翻译看似完成但未写入”，同时检查 chunk 的 `translated` 标记和对应 save-progress
