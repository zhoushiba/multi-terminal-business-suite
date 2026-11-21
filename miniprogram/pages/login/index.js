const auth = require('../../utils/auth')

Page({
  data: {
    loading: false,
    isLogin: false,
    nickname: '',
    redirect: ''
  },

  onLoad(options) {
    const user = auth.getUser()
    this.setData({
      isLogin: auth.isLogin(),
      nickname: user && user.nickname ? user.nickname : '',
      redirect: options.redirect || ''
    })
  },

  async handleLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const user = await auth.login()
      this.afterLogin(user)
    } catch (err) {
      wx.showToast({ title: err.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  handleDemo() {
    this.handleLogin()
  },

  afterLogin(user) {
    wx.showToast({ title: '登录成功', icon: 'success' })
    this.setData({ isLogin: true, nickname: user && user.nickname ? user.nickname : '' })
    setTimeout(() => {
      const redirect = this.data.redirect
      if (redirect) {
        wx.redirectTo({ url: '/' + redirect.replace(/^\//, '') })
        return
      }
      const pages = getCurrentPages()
      if (pages.length > 1) {
        wx.navigateBack()
      } else {
        wx.switchTab({ url: '/pages/index/index' })
      }
    }, 600)
  },

  onBack() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  }
})
