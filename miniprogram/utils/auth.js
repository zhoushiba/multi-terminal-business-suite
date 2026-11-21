/**
 * 登录态工具
 *
 * 真机/开发者工具调用 wx.login 拿到临时 code，后端 /api/wx/login 用 code 换取 openid；
 * 本项目 app.wechat.mock=true（默认）时后端直接返回演示账号，便于离线演示：
 * 第一次登录用 admin 账号数据，方便看「我的报名」等需要登录的接口。
 */
const { get, post, TOKEN_KEY, REFRESH_KEY, USER_KEY, clearToken } = require('./request')

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function getUser() {
  const user = wx.getStorageSync(USER_KEY)
  return user || null
}

function isLogin() {
  return !!getToken()
}

function setSession(data) {
  wx.setStorageSync(TOKEN_KEY, data.accessToken)
  wx.setStorageSync(REFRESH_KEY, data.refreshToken || '')
  wx.setStorageSync(USER_KEY, data.user || null)
  const app = getApp()
  if (app) {
    app.globalData.token = data.accessToken
    app.globalData.user = data.user || null
  }
  return data.user
}

function logout() {
  clearToken()
  const app = getApp()
  if (app) {
    app.globalData.token = ''
    app.globalData.user = null
  }
}

/** 走真实 wx.login → 后端换 token；失败时抛错由页面提示 */
function login() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (!res.code) {
          reject(new Error('获取微信登录 code 失败'))
          return
        }
        post('/wx/login', { code: res.code }, { auth: false, loading: true, loadingText: '登录中' })
          .then((data) => resolve(setSession(data)))
          .catch(reject)
      },
      fail(err) {
        reject(new Error(err.errMsg || 'wx.login 调用失败'))
      }
    })
  })
}

/** 需要登录的页面调用：未登录则跳转登录页，返回是否已登录 */
function ensureLogin() {
  if (isLogin()) return true
  wx.navigateTo({ url: '/pages/login/index' })
  return false
}

/** 拉取当前登录用户资料（顺便校验 token 是否仍然有效） */
function fetchProfile() {
  return get('/auth/profile', {}, { loading: false })
}

module.exports = {
  TOKEN_KEY,
  USER_KEY,
  getToken,
  getUser,
  isLogin,
  setSession,
  logout,
  login,
  ensureLogin,
  fetchProfile
}
