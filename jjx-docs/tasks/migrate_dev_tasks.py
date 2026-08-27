#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
迁移 tasks.json（文档中心 175 个开发任务）→ sys_task 表
字段完整映射，不丢数据：
  id       → source_id + task_code(DEV-{id})
  title    → title
  desc     → description
  priority → priority (P0=urgent/P1=high/P2=normal/P3,P4=low)
  status   → status (done=10/todo=0/pending=1) + completed_time
  date     → create_time (补年份) + 原值保留在 remark
  tags     → remark (JSON)
"""
import json
import sys
import pymysql

TASKS_JSON = '/mnt/d/openclaw-workspace/docs/tasks/tasks.json'

def main():
    with open(TASKS_JSON, encoding='utf-8') as f:
        tasks = json.load(f)

    conn = pymysql.connect(
        host='127.0.0.1', user='root', password='123456',
        database='jjx_erp_db', charset='utf8mb4',
        cursorclass=pymysql.cursors.DictCursor,
    )
    cur = conn.cursor()

    # 幂等：先清掉已迁移的 DEV 任务
    cur.execute("DELETE FROM sys_task WHERE kanban_module='dev'")
    conn.commit()

    prio_map = {'P0': 'urgent', 'P1': 'high', 'P2': 'normal', 'P3': 'low', 'P4': 'low',
                'p1': 'high', 'p2': 'normal'}
    status_map = {'done': 10, 'todo': 0, 'pending': 1}

    inserted = 0
    for t in tasks:
        pid = t.get('id')
        title = t.get('title', '')
        desc = t.get('desc')
        priority_raw = t.get('priority', 'P2')
        priority = prio_map.get(str(priority_raw), 'normal')
        status_raw = t.get('status', 'todo')
        status = status_map.get(status_raw, 0)
        date_raw = t.get('date')  # 07-18
        tags = t.get('tags') or []

        # date → create_time（补年份）
        create_time = None
        if date_raw and '-' in str(date_raw):
            try:
                mm, dd = str(date_raw).split('-')[:2]
                create_time = f'2026-{int(mm):02d}-{int(dd):02d} 09:00:00'
            except Exception:
                create_time = None

        # completed_time
        completed_time = create_time if status == 10 else None

        # remark：保留原始 date + tags（JSON），完整不丢
        remark_parts = []
        if date_raw:
            remark_parts.append(f'原date:{date_raw}')
        if tags:
            remark_parts.append('tags:' + json.dumps(tags, ensure_ascii=False))
        remark = ' | '.join(remark_parts) if remark_parts else None

        cur.execute(
            """INSERT INTO sys_task
               (task_code, task_type, kanban_module, title, description, status, priority,
                source_id, start_time, completed_time, create_by, create_time, remark)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
            (f'DEV-{pid}', 'dev', 'dev', title, desc, status, priority,
             pid, create_time, completed_time, 'admin', create_time, remark)
        )
        inserted += 1

    conn.commit()
    cur.close()
    conn.close()
    print(f'✅ 迁移完成，插入 {inserted} 条')

if __name__ == '__main__':
    main()
