/**
 * 截图脚本：用 Edge headless + CDP 抓取管理后台真实界面
 *
 * 前置（两个服务都要先起来）：
 *   1) 后端：cd backend && mvn -DskipTests package && java -jar target/business-suite-backend.jar
 *      （local profile，H2 文件库，监听 8080）
 *   2) 前端：cd frontend && npm run build && npm run preview
 *      （vite preview 监听 127.0.0.1:4173，已配置 /api 代理到 8080）
 *
 * 运行：node scripts/capture-screenshots.mjs
 * 产物：docs/screenshots/01-login.png、02-dashboard.png、03-content-list.png
 */
import { spawn } from 'node:child_process'
import { mkdirSync, writeFileSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const EDGE =
  process.env.EDGE_PATH ||
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const WEB_BASE = process.env.WEB_BASE || 'http://127.0.0.1:4173'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const CDP_PORT = Number(process.env.CDP_PORT || 9222)
const OUT_DIR = join(process.cwd(), 'docs', 'screenshots')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function login() {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const body = await res.json()
  if (body.code !== 0) throw new Error('登录失败：' + body.message)
  return body.data
}

class Cdp {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(typeof ev.data === 'string' ? ev.data : ev.data.toString())
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    })
  }
  open(url) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url)
      ws.addEventListener('open', () => resolve(new Cdp(ws)))
      ws.addEventListener('error', (e) => reject(new Error('WebSocket 连接失败: ' + e.message)))
    })
  }
  send(method, params = {}) {
    const id = ++this.seq
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 30000)
    })
  }
}

async function getPageTarget() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)
      const list = await res.json()
      const page = list.find((t) => t.type === 'page')
      if (page?.webSocketDebuggerUrl) return page
    } catch {
      /* 等待 Edge 启动 */
    }
    await sleep(500)
  }
  throw new Error('未找到 Edge 调试页面，请确认 --remote-debugging-port 已生效')
}

async function main() {
  if (!existsSync(EDGE)) throw new Error('未找到 Edge：' + EDGE)
  mkdirSync(OUT_DIR, { recursive: true })

  console.log('[1/6] 调用后端登录接口获取 token ...')
  const tokenData = await login()
  console.log('      登录成功，用户：' + tokenData.user.nickname)

  const profileDir = mkdtempSafe()
  console.log('[2/6] 启动 Edge headless（CDP 端口 ' + CDP_PORT + '）...')
  const edge = spawn(
    EDGE,
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-background-networking',
      '--window-size=1440,900',
      `--remote-debugging-port=${CDP_PORT}`,
      `--user-data-dir=${profileDir}`,
      'about:blank',
    ],
    { stdio: 'ignore', windowsHide: true },
  )

  const page = await getPageTarget()
  const cdp = await Cdp.prototype.open(page.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  console.log('[3/6] CDP 已连接：' + page.webSocketDebuggerUrl)

  async function shot(file) {
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' })
    const buf = Buffer.from(data, 'base64')
    writeFileSync(join(OUT_DIR, file), buf)
    console.log(`      已保存 docs/screenshots/${file}（${(buf.length / 1024).toFixed(0)} KB）`)
  }

  async function navigate(url, waitMs = 2500) {
    await cdp.send('Page.navigate', { url })
    await sleep(waitMs)
  }

  // ① 登录页（无需 token）
  console.log('[4/6] 抓取登录页 ...')
  await navigate(`${WEB_BASE}/#/login`, 3500)
  await shot('01-login.png')

  // ② 注入 token 后抓看板
  console.log('[5/6] 注入登录态并抓取数据看板 ...')
  const user = JSON.stringify(tokenData.user)
  const js =
    `localStorage.setItem('suite_token', ${JSON.stringify(tokenData.accessToken)});` +
    `localStorage.setItem('suite_refresh_token', ${JSON.stringify(tokenData.refreshToken)});` +
    `localStorage.setItem('suite_user', ${JSON.stringify(user)});` +
    `location.hash = '#/dashboard'; 'ok'`
  await cdp.send('Runtime.evaluate', { expression: js, returnByValue: true })
  await navigate(`${WEB_BASE}/#/dashboard`, 5000)
  await shot('02-dashboard.png')

  // ③ 内容管理列表（带真实种子数据）
  console.log('[6/6] 抓取内容管理列表 ...')
  await navigate(`${WEB_BASE}/#/content/list`, 4000)
  await shot('03-content-list.png')

  cdp.ws.close()
  edge.kill()
  console.log('\n全部截图完成 ✔  输出目录：' + OUT_DIR)
}

function mkdtempSafe() {
  const dir = join(tmpdir(), 'suite-edge-' + Date.now())
  mkdirSync(dir, { recursive: true })
  return dir
}

main().catch((err) => {
  console.error('截图失败：', err.message || err)
  process.exit(1)
})
