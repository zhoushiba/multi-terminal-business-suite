/**
 * 小程序端展示格式化工具
 */
const CONTENT_TYPE_TEXT = {
  article: '图文',
  gallery: '图集',
  video: '视频'
}

const CONTENT_STATUS = {
  0: '草稿',
  1: '待审核',
  2: '已发布',
  3: '已下架'
}

const SIGNUP_STATUS = {
  1: '待核销',
  2: '已核销',
  3: '已取消'
}

/** 后端返回 'yyyy-MM-dd HH:mm:ss'，小程序 iOS 只认 'yyyy/MM/dd'，这里统一替换分隔符 */
function parseDate(value) {
  if (!value) return null
  if (typeof value !== 'string') return null
  return new Date(value.replace(/-/g, '/'))
}

function pad(num) {
  return num < 10 ? '0' + num : '' + num
}

function formatDate(value, withTime) {
  const date = parseDate(value)
  if (!date) return ''
  const y = date.getFullYear()
  const m = pad(date.getMonth() + 1)
  const d = pad(date.getDate())
  if (!withTime) return y + '-' + m + '-' + d
  return y + '-' + m + '-' + d + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes())
}

/** 转成「x 分钟前 / x 小时前 / x 天前」，超过 7 天显示日期 */
function timeAgo(value) {
  const date = parseDate(value)
  if (!date) return ''
  const diff = Date.now() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return minutes + ' 分钟前'
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return hours + ' 小时前'
  const days = Math.floor(hours / 24)
  if (days < 7) return days + ' 天前'
  return formatDate(value)
}

function contentTypeText(type) {
  return CONTENT_TYPE_TEXT[type] || '内容'
}

function contentStatusText(status) {
  return CONTENT_STATUS[status] || '未知'
}

function signupStatusText(status) {
  return SIGNUP_STATUS[status] || '未知'
}

function formatNumber(value) {
  const num = Number(value || 0)
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  return '' + num
}

/** 富文本正文：去掉 HTML 标签，用于列表页摘要 */
function plainText(html) {
  if (!html) return ''
  return String(html)
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
}

module.exports = {
  formatDate,
  timeAgo,
  contentTypeText,
  contentStatusText,
  signupStatusText,
  formatNumber,
  plainText
}
