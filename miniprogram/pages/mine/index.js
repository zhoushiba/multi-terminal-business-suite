const auth = require('../../utils/auth')
const { get } = require('../../utils/request')
const fmt = require('../../utils/format')

Page({
  data: {
    isLogin: false,
    user: null,
    rolesText: '',
    avatarText: '游',
    signups: [],
    stats: { total: 0, verified: 0 },
    loading: false,
    errorText: ''
  },

  onShow() {
    this.syncUser()
    if (auth.isLogin()) {
      this.loadSignups()
    }
  },

  syncUser() {
    const isLogin = auth.isLogin()
    const user = auth.getUser()
    const nickname = user && user.nickname ? user.nickname : '微信用户'
    const roles = (user && user.roles) || []
    this.setData({
      isLogin,
      user,
      avatarText: nickname.slice(0, 1),
      rolesText: roles.length ? roles.join(' / ') : '未分配角色',
      signups: isLogin ? this.data.signups : [],
      stats: isLogin ? this.data.stats : { total: 0, verified: 0 }
    })
  },

  async loadSignups() {
    this.setData({ loading: true, errorText: '' })
    try {
      const res = await get('/signups/mine', { page: 1, size: 20 }, { loading: false })
      const list = res && Array.isArray(res.list) ? res.list : (Array.isArray(res) ? res : [])
      const signups = list.map((item) => ({
        id: item.id,
        title: item.activityTitle || '活动',
        sessionName: item.sessionName || '',
        statusText: item.statusText || fmt.signupStatusText(item.status),
        status: item.status,
        code: item.code,
        timeText: fmt.formatDate(item.signupTime, true)
      }))
      const verified = signups.filter((item) => item.status === 2).length
      this.setData({ signups, stats: { total: signups.length, verified } })
    } catch (err) {
      this.setData({ errorText: '报名记录加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onLogin() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  onRefresh() {
    if (!auth.isLogin()) {
      this.onLogin()
      return
    }
    this.loadSignups()
    wx.showToast({ title: '已刷新', icon: 'none' })
  },

  onAbout() {
    wx.showModal({
      title: '多端业务中台套件',
      content:
        '后端：Spring Boot 3.3 + MyBatis-Plus + H2/MySQL\n' +
        '管理端：Vue 3 + Vite + Element Plus\n' +
        '小程序端：微信原生（本目录）\n' +
        '演示账号：admin / admin123',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要清除本地登录状态吗？',
      success: (res) => {
        if (!res.confirm) return
        auth.logout()
        this.setData({ signups: [], stats: { total: 0, verified: 0 } })
        this.syncUser()
        wx.showToast({ title: '已退出登录', icon: 'none' })
      }
    })
  }
})
