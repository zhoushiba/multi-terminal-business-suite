const { get } = require('../../utils/request')
const fmt = require('../../utils/format')

const PAGE_SIZE = 10

function toList(res) {
  if (!res) return []
  if (Array.isArray(res)) return res
  if (Array.isArray(res.list)) return res.list
  return []
}

function buildParams(state) {
  const params = { page: state.page, size: PAGE_SIZE }
  if (state.keyword) params.keyword = state.keyword
  if (state.categoryId) params.categoryId = state.categoryId
  return params
}

function decorate(list) {
  return list.map((item) => ({
    id: item.id,
    title: item.title,
    summary: fmt.plainText(item.summary || item.body || '').slice(0, 60),
    typeText: fmt.contentTypeText(item.contentType),
    author: item.authorName || '数海观澜',
    timeText: fmt.formatDate(item.publishTime || item.createdAt),
    viewText: fmt.formatNumber(item.viewCount) + ' 阅读',
    topFlag: item.topFlag === 1
  }))
}

Page({
  data: {
    keyword: '',
    categoryId: 0,
    categories: [],
    list: [],
    page: 1,
    total: 0,
    loading: true,
    loadingMore: false,
    finished: false,
    errorText: ''
  },

  onLoad(options) {
    const categoryId = options.categoryId ? Number(options.categoryId) : 0
    const keyword = options.keyword ? decodeURIComponent(options.keyword) : ''
    this.setData({ categoryId, keyword })
    this.loadCategories()
    this.loadList(true)
  },

  onPullDownRefresh() {
    this.loadList(true).then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.finished || this.data.loadingMore || this.data.loading) return
    this.loadList(false)
  },

  async loadCategories() {
    try {
      const res = await get('/public/categories', {}, { loading: false, showError: false })
      const categories = toList(res).map((item) => ({ id: item.id, name: item.name }))
      this.setData({ categories })
    } catch (err) {
      this.setData({ categories: [] })
    }
  },

  async loadList(reset) {
    const page = reset ? 1 : this.data.page + 1
    this.setData(reset ? { loading: true, errorText: '' } : { loadingMore: true })

    try {
      const res = await get(
        '/public/contents',
        buildParams({ page, keyword: this.data.keyword, categoryId: this.data.categoryId }),
        { loading: false }
      )
      const pageData = res && !Array.isArray(res) ? res : { list: toList(res), total: toList(res).length }
      const rows = decorate(pageData.list || [])
      const list = reset ? rows : this.data.list.concat(rows)
      const finished = rows.length < PAGE_SIZE || list.length >= (pageData.total || 0)
      this.setData({
        list,
        page,
        total: pageData.total || list.length,
        finished: !!finished
      })
    } catch (err) {
      this.setData({ errorText: '内容加载失败，请稍后重试' })
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.loadList(true)
  },

  onCategoryTap(e) {
    const id = Number(e.currentTarget.dataset.id) || 0
    if (id === this.data.categoryId) return
    this.setData({ categoryId: id })
    this.loadList(true)
  },

  onContentTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/content/detail?id=' + id })
  },

  onRetry() {
    this.loadList(true)
  }
})
