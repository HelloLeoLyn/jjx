# Redis 方案

## 缓存
- Spring Cache + Redis，@Cacheable / @CacheEvict 注解
- 缓存高频读取、低频变更数据（字典、配置、菜单）
- 配置：localhost:6379, database 0

## 序列号生成
- Redis INCR + 业务前缀 + 日期
- Key 设 48h TTL 防内存泄漏
- 示例：`serial:sales:order:20260720` → `SO202607200001`

## 备份
- RDB 持久化，每日凌晨备份
- 恢复：替换 dump.rdb 重启
