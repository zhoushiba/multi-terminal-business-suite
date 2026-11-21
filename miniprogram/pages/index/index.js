const { get } = require('../../utils/request')
const fmt = require('../../utils/format')

const QUICK_ICONS = ['🏞', '🛰', '📢', '🧭']

/** 兼容返回数组或分页对象两种形态 */
function toList(res) {
  if (!res) return []
  if (Array.isArray(res)) return res
  if (Array.isArray(res.list)) return res.list
  return []
}

Page({
  data: {
    city: '贵阳',
    keyword: '',
    categories: [],
    activities: [],
    contents: [],
    loading: true,
    errorText: ''
  },

  onLoad() {
    this.loadHome()
  },

  onShow() {
    // 从详情页返回时刷新阅读量等数据
    if (this.data.loading) return
    this.loadHome()
  },

  onPullDownRefresh() {
    this.loadHome().then(() => wx.stopPullDownRefresh())
  },

  async loadHome() {
    this.setData({ loading: true, errorText: '' })
    try {
      const [categoriesRes, activitiesRes, contentsRes] = await Promise.all([
        get('/public/categories', {}, { loading: false }),
        get('/public/activities', {}, { loading: false }),
        get('/public/contents', { page: 1, size: 5, sort: 'latest' }, { loading: false })
      ])

      const categories = toList(categoriesRes)
        .slice(0, 4)
        .map((item, index) => ({
          id: item.id,
          name: item.name,
          icon: QUICK_ICONS[index] || '📄'
        }))

      const activities = toList(activitiesRes)
        .slice(0, 3)
        .map((item) => ({
          id: item.id,
          title: item.title,
          description: item.description || '',
          timeText: fmt.formatDate(item.startTime),
          signupText: item.signupOpen ? '报名中' : '报名已结束',
          signupOpen: !!item.signupOpen,
          sessionCount: item.sessions ? item.sessions.length : 0,
          regionText: item.regionId ? '区域 ' + item.regionId : '贵州'
        }))

      const contents = toList(contentsRes).map((item) => ({
        id: item.id,
        title: item.title,
        summary: fmt.plainText(item.summary || item.body || '').slice(0, 46),
        typeText: fmt.contentTypeText(item.contentType),
        timeText: fmt.timeAgo(item.publishTime || item.createdAt),
        viewText: fmt.formatNumber(item.viewCount) + ' 阅读'
      }))

      this.setData({ categories, activities, contents })
    } catch (err) {
      this.setData({ errorText: '内容加载失败，请确认后端服务已启动' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    const keyword = (this.data.keyword || '').trim()
    wx.navigateTo({ url: '/pages/content/list?keyword=' + encodeURIComponent(keyword) })
  },

  onCategoryTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/content/list?categoryId=' + id })
  },

  onMoreContent() {
    wx.switchTab({ url: '/pages/content/list' })
  },

  onContentTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/content/detail?id=' + id })
  },

  async onActivityTap(e) {
    const id = e.currentTarget.dataset.id
    try {
      const activity = await get('/public/activities/' + id, {}, { loading: false })
      const sessions = (activity.sessions || [])
        .map((s) => s.name + ' ' + fmt.formatDate(s.startTime) + '（余 ' + s.remain + '）')
        .join('\n')
      wx.showModal({
        title: activity.title,
        content:
          '时间：' + fmt.formatDate(activity.startTime) + ' 至 ' + fmt.formatDate(activity.endTime) +
          '\n报名：' + (activity.signupOpen ? '开放中' : '已结束') +
          (sessions ? '\n场次：\n' + sessions : ''),
        showCancel: false,
        confirmText: '知道了'
      })
    } catch (err) {
      // 错误提示已由 request 统一处理
    }
  },

  onRetry() {
    this.loadHome()
  }
})
