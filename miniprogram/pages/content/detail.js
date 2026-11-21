const { get, post } = require('../../utils/request')
const fmt = require('../../utils/format')

Page({
  data: {
    id: null,
    content: null,
    tags: [],
    metaText: '',
    viewText: '',
    loading: true,
    errorText: ''
  },

  onLoad(options) {
    if (!options.id) {
      this.setData({ loading: false, errorText: '缺少内容 ID，无法打开详情' })
      return
    }
    this.setData({ id: options.id })
    this.loadDetail()
  },

  async loadDetail() {
    this.setData({ loading: true, errorText: '' })
    try {
      const content = await get('/public/contents/' + this.data.id, {}, { loading: false })
      const tags = content.tags || []
      this.setData({
        content,
        tags,
        metaText:
          (content.authorName || '数海观澜') +
          ' · ' +
          fmt.formatDate(content.publishTime || content.createdAt, true),
        viewText: fmt.formatNumber(content.viewCount) + ' 阅读'
      })
      wx.setNavigationBarTitle({ title: content.title || '内容详情' })
      this.recordView()
    } catch (err) {
      this.setData({ errorText: '内容加载失败，可能已被下架或删除' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 阅读量 +1：失败也不影响正文展示 */
  recordView() {
    const content = this.data.content
    if (!content) return
    post('/public/contents/' + this.data.id + '/view', {}, { loading: false, showError: false })
      .then((res) => {
        const count = typeof res === 'number' ? res : (content.viewCount || 0) + 1
        this.setData({ viewText: fmt.formatNumber(count) + ' 阅读' })
      })
      .catch(() => {
        const count = (content.viewCount || 0) + 1
        this.setData({ viewText: fmt.formatNumber(count) + ' 阅读' })
      })
  },

  onRetry() {
    this.loadDetail()
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  onShareAppMessage() {
    const content = this.data.content || {}
    return {
      title: content.title || '数海观澜 · 文旅资讯',
      path: '/pages/content/detail?id=' + this.data.id
    }
  }
})
