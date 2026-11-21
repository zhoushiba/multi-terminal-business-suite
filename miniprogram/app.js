/**
 * 多端业务中台套件 · 微信小程序端
 *
 * 与后台管理系统共用同一套 RESTful 接口：
 *  - 公开内容 / 活动 / 点位：/api/public/**（无需登录）
 *  - 微信登录：POST /api/wx/login（后端开启 app.wechat.mock=true 时用 code 兑换演示账号）
 *  - 个人报名：GET /api/signups/mine（需要 Bearer token）
 */
const { getToken, getUser } = require('./utils/auth')

App({
  globalData: {
    /** 本地联调地址：微信开发者工具里勾选「不校验合法域名」即可；真机需换成 HTTPS 域名 */
    baseUrl: 'http://127.0.0.1:8080',
    token: '',
    user: null,
    city: '贵阳'
  },

  onLaunch() {
    this.globalData.token = getToken()
    this.globalData.user = getUser()
  },

  onShow() {
    // 从后台切回时同步一次登录态，避免 storage 被清理后页面仍显示已登录
    this.globalData.token = getToken()
    this.globalData.user = getUser()
  }
})
