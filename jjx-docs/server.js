const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const mysql = require('mysql2/promise');

const PORT = 8899;
const ROOT = __dirname;
const ACCOUNTS_FILE = path.join(ROOT, 'accounts.json');

// ===== MySQL 配置（与 ERP 数据库一致）=====
const DB = {
  host: process.env.JJX_DB_HOST || '127.0.0.1',
  port: Number(process.env.JJX_DB_PORT || 3306),
  user: process.env.JJX_DB_USER || 'root',
  password: process.env.JJX_DB_PASSWORD || '123456',
  database: process.env.JJX_DB_NAME || 'jjx_erp_db',
};

// ===== 账号密码加密（AES-256-GCM，密钥走环境变量）=====
const ACCOUNTS_KEY = process.env.ACCOUNTS_KEY || 'jjx-docs-accounts-key-2026';
function encrypt(text) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', crypto.createHash('sha256').update(ACCOUNTS_KEY).digest(), iv);
  const enc = Buffer.concat([cipher.update(text, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return iv.toString('hex') + ':' + tag.toString('hex') + ':' + enc.toString('hex');
}
function decrypt(data) {
  try {
    const [ivHex, tagHex, encHex] = data.split(':');
    const decipher = crypto.createDecipheriv('aes-256-gcm', crypto.createHash('sha256').update(ACCOUNTS_KEY).digest(), Buffer.from(ivHex, 'hex'));
    decipher.setAuthTag(Buffer.from(tagHex, 'hex'));
    return Buffer.concat([decipher.update(Buffer.from(encHex, 'hex')), decipher.final()]).toString('utf8');
  } catch (e) { return '***'; }
}

// ===== 账号密码存储 =====
function loadAccounts() {
  try {
    if (fs.existsSync(ACCOUNTS_FILE)) return JSON.parse(fs.readFileSync(ACCOUNTS_FILE, 'utf-8'));
  } catch (e) { console.error('读取 accounts.json 失败:', e.message); }
  return [];
}
function saveAccounts(list) {
  fs.writeFileSync(ACCOUNTS_FILE, JSON.stringify(list, null, 2), 'utf-8');
}
if (!fs.existsSync(ACCOUNTS_FILE)) saveAccounts([]);

// ===== 看板状态映射：sys_task 数字 ↔ 看板字符串 =====
// sys_task.status: 0待开始 1进行中 2待审核 3阻塞 4废弃 10完成
const DB_STATUS_TO_BOARD = { 0: 'todo', 1: 'doing', 2: 'doing', 3: 'doing', 10: 'done' };
const BOARD_STATUS_TO_DB = { todo: 0, doing: 1, done: 10 };
// 优先级：urgent/high/normal/low ↔ p0-p3
const DB_PRIORITY_TO_BOARD = { urgent: 'p0', high: 'p1', normal: 'p2', low: 'p3' };
const BOARD_PRIORITY_TO_DB = { p0: 'urgent', p1: 'high', p2: 'normal', p3: 'low' };

function fmtDate(d) {
  if (!d) return '';
  const s = String(d);
  return s.slice(5, 10); // MM-DD
}

async function queryTasks(module) {
  const conn = await mysql.createConnection(DB);
  try {
    const where = module && module !== 'all' ? 'WHERE kanban_module = ?' : '';
    const params = module && module !== 'all' ? [module] : [];
    const [rows] = await conn.query(
      `SELECT task_id, title, description, status, priority, deadline, kanban_module, task_type
       FROM sys_task ${where} ORDER BY task_id DESC`, params);
    return rows.map(r => ({
      id: r.task_id,
      title: r.title || '',
      status: DB_STATUS_TO_BOARD[r.status] || 'todo',
      priority: DB_PRIORITY_TO_BOARD[r.priority] || 'p2',
      desc: r.description || '',
      date: fmtDate(r.deadline),
      tags: [r.kanban_module || 'dev', r.task_type || 'general'].filter(Boolean),
    }));
  } finally {
    conn.end();
  }
}

async function updateTasks(tasks) {
  const conn = await mysql.createConnection(DB);
  try {
    for (const t of tasks) {
      if (!t.id) continue;
      const status = BOARD_STATUS_TO_DB[t.status];
      const priority = BOARD_PRIORITY_TO_DB[t.priority];
      if (status === undefined) continue;
      await conn.query(
        'UPDATE sys_task SET status = ?, priority = ?, update_time = NOW() WHERE task_id = ?',
        [status, priority || 'normal', t.id]);
    }
    return { updated: tasks.filter(t => t.id).length };
  } finally {
    conn.end();
  }
}

// 测试工作台登记开发任务：插入 sys_task（与 ERP 看板同表，kanban_module=dev）
async function createTestTask(body) {
  const conn = await mysql.createConnection(DB);
  try {
    const taskCode = 'dev-' + Date.now();
    const title = String(body.title || '').slice(0, 200);
    const description = String(body.description || '').slice(0, 2000);
    const priority = ['urgent', 'high', 'normal', 'low'].includes(body.priority) ? body.priority : 'normal';
    const [r] = await conn.query(
      `INSERT INTO sys_task (task_code, task_type, kanban_module, title, description, priority, status, create_by)
       VALUES (?, 'general', 'dev', ?, ?, ?, 0, 'test-workbench')`,
      [taskCode, title, description, priority]);
    return { taskId: r.insertId, taskCode };
  } finally {
    conn.end();
  }
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.md': 'text/plain; charset=utf-8',
};

function sendJson(res, code, data, message) {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ code, data, message }));
}

function readBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => { try { resolve(JSON.parse(body)); } catch { resolve(null); } });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  let pathname = url.pathname;

  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  try {
    // ===== Task API（对接 sys_task 数据库）=====
    if (req.method === 'GET' && pathname === '/api/tasks') {
      const module = url.searchParams.get('module') || 'dev';
      const tasks = await queryTasks(module);
      sendJson(res, 0, tasks);
      return;
    }
    if ((req.method === 'POST' || req.method === 'PUT') && pathname === '/api/tasks') {
      const body = await readBody(req);
      if (!body || !Array.isArray(body)) { sendJson(res, 400, null, '数据格式错误'); return; }
      const r = await updateTasks(body);
      sendJson(res, 0, r, 'ok');
      return;
    }
    // ===== 测试工作台登记开发任务（测试失败 → sys_task） =====
    if (req.method === 'POST' && pathname === '/api/test-task') {
      const body = await readBody(req);
      if (!body || !body.title) { sendJson(res, 400, null, '标题不能为空'); return; }
      const r = await createTestTask(body);
      sendJson(res, 0, r, 'ok');
      return;
    }
    if (req.method === 'POST' && pathname === '/api/reset') {
      sendJson(res, 0, null, '数据库模式下无需重置（数据在 sys_task）');
      return;
    }

    // ===== Accounts API（密码保险箱，AES 加密存储）=====
    if (req.method === 'GET' && pathname === '/api/accounts') {
      const list = loadAccounts().map(a => ({ ...a, password: decrypt(a.password) }));
      sendJson(res, 0, list);
      return;
    }
    if (req.method === 'POST' && pathname === '/api/accounts') {
      const body = await readBody(req);
      if (!body || !body.system) { sendJson(res, 400, null, '系统名称必填'); return; }
      const list = loadAccounts();
      const item = {
        id: Date.now(),
        system: body.system,
        url: body.url || '',
        username: body.username || '',
        password: encrypt(body.password || ''),
        remark: body.remark || '',
        createTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      };
      list.push(item);
      saveAccounts(list);
      sendJson(res, 0, item, 'ok');
      return;
    }
    if (req.method === 'PUT' && pathname === '/api/accounts') {
      const body = await readBody(req);
      if (!body || !body.id) { sendJson(res, 400, null, '缺少ID'); return; }
      const list = loadAccounts();
      const idx = list.findIndex(a => a.id === body.id);
      if (idx < 0) { sendJson(res, 404, null, '记录不存在'); return; }
      const item = list[idx];
      item.system = body.system ?? item.system;
      item.url = body.url ?? item.url;
      item.username = body.username ?? item.username;
      if (body.password !== undefined && body.password !== '') item.password = encrypt(body.password);
      item.remark = body.remark ?? item.remark;
      item.updateTime = new Date().toISOString().slice(0, 19).replace('T', ' ');
      list[idx] = item;
      saveAccounts(list);
      sendJson(res, 0, item, 'ok');
      return;
    }
    if (req.method === 'DELETE' && pathname === '/api/accounts') {
      const id = Number(url.searchParams.get('id'));
      const list = loadAccounts().filter(a => a.id !== id);
      saveAccounts(list);
      sendJson(res, 0, null, 'ok');
      return;
    }
  } catch (e) {
    console.error('API 错误:', e.message);
    sendJson(res, 500, null, '数据库连接失败，请确认 MySQL 已启动（回退文件模式不可用，看板数据在 sys_task）');
    return;
  }

  // ===== Static Files =====
  if (pathname === '/' || pathname === '') pathname = '/index.html';
  let filePath = path.join(ROOT, pathname);
  if (!path.extname(pathname)) {
    const alt = path.join(filePath, 'index.html');
    if (fs.existsSync(alt)) filePath = alt;
  }

  const ext = path.extname(filePath);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 文档中心已启动: http://localhost:${PORT}`);
  console.log(`📋 任务看板(接sys_task): http://localhost:${PORT}/tasks/`);
  console.log(`🔑 账号密码: http://localhost:${PORT}/accounts/`);
  console.log(`🧪 测试工作台: http://localhost:${PORT}/test/`);
});
