/**
 * wx.request 统一封装
 *
 * - 自动拼接 baseUrl，自动携带 Authorization: Bearer <token>
 * - 后端统一响应 { code, message, data, traceId, timestamp }，code === 0 视为成功，直接 resolve(data)
 * - 401：清理本地登录态并跳转登录页（小程序端不做 refresh 静默续期，保持逻辑简单可预期）
 * - 其余错误统一 toast，并把 Error 抛给调用方决定是否重试
 */
const BASE_URL = 'http://127.0.0.1:8080/api'
const TOKEN_KEY = 'suite_token'
const REFRESH_KEY = 'suite_refresh_token'
const USER_KEY = 'suite_user'

let redirecting = false

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(REFRESH_KEY)
  wx.removeStorageSync(USER_KEY)
}

function toast(message) {
  wx.showToast({ title: message, icon: 'none', duration: 2200 })
}

function handleUnauthorized() {
  clearToken()
  if (redirecting) return
  redirecting = true
  toast('登录已过期，请重新登录')
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1].route : ''
  if (current && current.indexOf('pages/login') < 0) {
    wx.navigateTo({ url: '/pages/login/index', complete: () => { redirecting = false } })
  } else {
    redirecting = false
  }
}

function request(options) {
  const {
    url,
    method = 'GET',
    data = {},
    auth = true,
    loading = true,
    loadingText = '加载中',
    showError = true
  } = options

  return new Promise((resolve, reject) => {
    if (loading) wx.showLoading({ title: loadingText, mask: true })

    const header = { 'Content-Type': 'application/json' }
    const token = getToken()
    if (auth && token) header.Authorization = 'Bearer ' + token

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      timeout: 15000,
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401) {
          handleUnauthorized()
          reject(new Error('未登录或登录已过期'))
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 0) {
          resolve(body.data)
          return
        }
        const message = body.message || '请求失败（' + res.statusCode + '）'
        if (showError) toast(message)
        reject(new Error(message))
      },
      fail(err) {
        const message = '网络异常，请检查后端是否已启动'
        if (showError) toast(message)
        reject(new Error(err.errMsg || message))
      },
      complete() {
        if (loading) wx.hideLoading()
      }
    })
  })
}

function get(url, data, options) {
  return request(Object.assign({ url, method: 'GET', data }, options))
}

function post(url, data, options) {
  return request(Object.assign({ url, method: 'POST', data }, options))
}

module.exports = {
  BASE_URL,
  TOKEN_KEY,
  REFRESH_KEY,
  USER_KEY,
  request,
  get,
  post,
  getToken,
  clearToken,
  toast
}
