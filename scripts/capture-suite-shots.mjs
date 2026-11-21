/**
 * 多端业务中台 —— 真实截图脚本（Edge headless + CDP）
 *
 * 用法：
 *   1) 后端已启动： java -jar backend/target/business-suite-backend.jar --server.port=8080
 *   2) 前端已构建并预览：npm run build && npm run preview -- --port 4174
 *   3) node scripts/capture-suite-shots.mjs
 *
 * 输出：docs/screenshots/{01-login,02-dashboard,03-content-list,04-user-manage}.png
 */
import { spawn } from 'node:child_process'
import { writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, '..')
const OUT_DIR = join(REPO, 'docs', 'screenshots')

const EDGE = process.env.EDGE_PATH || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const WEB_BASE = process.env.WEB_BASE || 'http://127.0.0.1:4174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const CDP_PORT = Number(process.env.CDP_PORT || 9333)
const WIDTH = 1440
const HEIGHT = 900

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 极简 CDP 客户端（Node 24 自带 WebSocket） */
class Cdp {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(typeof ev.data === 'string' ? ev.data : '')
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result)
      }
    })
  }

  static async open(url) {
    const ws = new WebSocket(url)
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true })
      ws.addEventListener('error', () => reject(new Error('CDP 连接失败')), { once: true })
    })
    return new Cdp(ws)
  }

  send(method, params = {}) {
    const id = ++this.seq
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error('CDP 超时: ' + method))
        }
      }, 30000)
    })
  }
}

async function login() {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error('登录失败: ' + JSON.stringify(json))
  return json.data
}

async function findTarget() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)
      const list = await res.json()
      const page = list.find((p) => p.type === 'page' && p.webSocketDebuggerUrl)
      if (page) return page
    } catch {
      /* Edge 还未就绪 */
    }
    await sleep(400)
  }
  throw new Error('未找到 Edge 调试目标')
}

async function main() {
  if (!existsSync(EDGE)) throw new Error('未找到 Edge：' + EDGE)
  mkdirSync(OUT_DIR, { recursive: true })

  console.log('[1/5] 登录后端获取令牌 ...')
  const tokenData = await login()
  console.log('      OK 用户：' + tokenData.user.nickname)

  const profile = join(tmpdir(), 'suite-shot-' + Date.now())
  mkdirSync(profile, { recursive: true })

  console.log('[2/5] 启动 Edge headless ...')
  const edge = spawn(
    EDGE,
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-background-networking',
      `--window-size=${WIDTH},${HEIGHT}`,
      `--remote-debugging-port=${CDP_PORT}`,
      `--user-data-dir=${profile}`,
      'about:blank',
    ],
    { stdio: 'ignore', windowsHide: true },
  )

  const target = await findTarget()
  const cdp = await Cdp.open(target.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width: WIDTH,
    height: HEIGHT,
    deviceScaleFactor: 1,
    mobile: false,
  })
  console.log('[3/5] CDP 已连接并锁定视口 ' + WIDTH + 'x' + HEIGHT)

  const evaluate = async (expression) => {
    const r = await cdp.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    return r.result?.value
  }

  const shot = async (file) => {
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    const buf = Buffer.from(data, 'base64')
    writeFileSync(join(OUT_DIR, file), buf)
    console.log(`      ✔ docs/screenshots/${file}（${(buf.length / 1024).toFixed(0)} KB）`)
  }

  /** 打开页面并等待 vue 渲染出真实内容（轮询 DOM 文本长度） */
  let navSeq = 0
  const open = async (hash, expectText) => {
    // 每次都用不同的 query 强制整页重新加载；否则仅有 hash 变化属于同文档导航，
    // Vue 应用不会重启，注入到 localStorage 的令牌不会被 auth store 重新读取。
    await cdp.send('Page.navigate', { url: `${WEB_BASE}/?shot=${++navSeq}${hash}` })
    for (let i = 0; i < 40; i++) {
      await sleep(400)
      const info = await evaluate(
        `(() => { const app = document.querySelector('#app'); return { hash: location.hash, len: app ? app.innerText.trim().length : 0, text: app ? app.innerText.slice(0, 2000) : '' } })()`,
      )
      if (info && info.hash === hash && info.len > 120 && (!expectText || info.text.includes(expectText))) {
        await sleep(900)
        return info
      }
    }
    const fallback = await evaluate(`document.querySelector('#app') ? document.querySelector('#app').innerText.slice(0,300) : ''`)
    throw new Error(`页面 ${hash} 未渲染出预期内容，当前：${fallback}`)
  }

  console.log('[4/5] 抓取登录页 ...')
  await open('#/login', '登录')
  await shot('01-login.png')

  console.log('[5/5] 注入登录态并依次抓取内页 ...')
  await evaluate(
    `localStorage.setItem('suite_token', ${JSON.stringify(tokenData.accessToken)});` +
      `localStorage.setItem('suite_refresh_token', ${JSON.stringify(tokenData.refreshToken)});` +
      `localStorage.setItem('suite_user', ${JSON.stringify(JSON.stringify(tokenData.user))});'ok'`,
  )

  const pages = [
    ['#/dashboard', '看板', '02-dashboard.png'],
    ['#/content/list', '内容', '03-content-list.png'],
    ['#/system/user', '用户', '04-user-manage.png'],
  ]
  for (const [hash, expect, file] of pages) {
    const info = await open(hash, expect)
    console.log(`      ${hash} 渲染 ${info.len} 字符`)
    await shot(file)
  }

  cdp.ws.close()
  edge.kill()
  console.log('\n全部截图完成 ✔ ' + OUT_DIR)
}

main().catch((err) => {
  console.error('截图失败：', err.message || err)
  process.exit(1)
})
