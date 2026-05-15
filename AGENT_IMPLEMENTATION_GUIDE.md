# Shopify 电商建议 AI Agent 实现指南

## 概述

我们已经成功实现了 Shopify 电商建议 AI Agent 的 Phase 1 版本，该 Agent 具有自我学习能力，能够根据商家的反馈不断优化建议策略。

## 核心特性

### 1. 自我学习机制
- 使用**多臂老虎机算法**平衡探索与利用
- 根据商家反馈（接受/拒绝/评分）优化策略权重
- 策略权重实时更新，自动学习最佳实践

### 2. 技能（Skills）系统
- 第一个技能：**翻译 ROI 分析**
  - POPULAR_PRODUCTS_FIRST：优先翻译热门产品
  - HIGH_VALUE_LANGUAGES：优先翻译高价值语言
  - MIXED_STRATEGY：混合策略
- 可扩展的技能接口，方便添加新功能

### 3. 反馈闭环
```
生成建议 → 商家反馈 → 学习优化 → 更好的建议
```

## 项目结构

```
BogdaApi/src/main/java/com/bogda/api/logic/agent/
├── EcommerceSkill.java          # 技能接口
├── EcommerceAdvisorAgent.java    # 主 Agent 类
├── StrategyOptimizer.java        # 策略优化器
├── AdviceMemory.java            # 存储器
├── TranslationROISkill.java      # 翻译 ROI 分析技能
├── ShopContext.java             # 店铺上下文
└── Feedback.java                # 反馈对象
```

## API 接口

### 1. 生成建议
```
POST /api/agent/ecommerce/advice/generate
参数：
  - shopName: 店铺名称
  - accessToken: Shopify 访问令牌

返回：建议列表
```

### 2. 提交反馈
```
POST /api/agent/ecommerce/feedback
Body：
{
  "adviceId": 建议 ID,
  "shopName": 店铺名称,
  "feedbackType": "ACCEPTED" | "REJECTED",
  "rating": 评分（可选，1-5）,
  "notes": 备注（可选）
}
```

### 3. 获取历史建议
```
GET /api/agent/ecommerce/advice/history?shopName=xxx
```

### 4. 获取学习状态
```
GET /api/agent/ecommerce/learning/status
返回各策略的权重
```

## 数据库

新增表：
- `ecommerce_advice`：存储建议
- `advice_feedback`：存储反馈
- `advice_performance`：存储效果数据

## 自我学习原理

### 策略选择
使用 ε-greedy 算法：
- ε 概率：随机探索新策略
- 1-ε 概率：利用历史表现最好的策略

### 奖励计算
```
如果是评分：reward = rating / 5
如果是接受：reward = 1.0
如果是拒绝：reward = 0.0
```

### 权重更新
使用滑动平均更新策略权重：
```
new_weight = old_weight + (reward - old_weight) / (attempt_count + 1)
```

## 使用示例

### 1. 生成建议
```javascript
// 前端调用
fetch('/api/agent/ecommerce/advice/generate?shopName=xxx&accessToken=xxx', {
  method: 'POST'
})
  .then(res => res.json())
  .then(data => {
    console.log('建议：', data.data);
  });
```

### 2. 提交反馈
```javascript
fetch('/api/agent/ecommerce/feedback', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    adviceId: 123,
    shopName: 'xxx',
    feedbackType: 'ACCEPTED',
    rating: 5,
    notes: '这个建议非常有帮助！'
  })
});
```

## Phase 2 计划

1. **接入真实 Shopify 数据**：从 Shopify API 获取产品、订单、流量数据
2. **添加新技能**：
   - 产品描述优化 Skill
   - SEO 优化 Skill
   - 价格策略建议 Skill
3. **效果跟踪**：接入 Shopify Analytics 数据
4. **持久化学习状态**：将策略权重保存到数据库

## 关键代码点

- **策略优化器**：`StrategyOptimizer.java` - 多臂老虎机算法实现
- **翻译 ROI 技能**：`TranslationROISkill.java` - 第一个具体技能
- **主 Agent**：`EcommerceAdvisorAgent.java` - 协调各技能

## 总结

这个电商建议 AI Agent 系统具有完整的自我学习能力，随着商家使用和反馈的增加，建议质量会不断提升。
