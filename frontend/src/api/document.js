import request from './request'

/**
 * 上传并解析文档
 * @param {File} file 文档文件
 */
export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/documents/upload',
    method: 'post',
    data: formData,
    isUpload: true,
    timeout: 300000, // 5分钟超时
  })
}

/**
 * 获取文档列表
 */
export function getDocumentList() {
  return request({
    url: '/documents/list',
    method: 'get',
  })
}

/**
 * 获取文档详情
 * @param {number} id 文档ID
 */
export function getDocument(id) {
  return request({
    url: `/documents/${id}`,
    method: 'get',
  })
}

/**
 * 获取解析结果
 * @param {number} id 文档ID
 */
export function getParseResult(id) {
  return request({
    url: `/documents/${id}/parse-result`,
    method: 'get',
  })
}

/**
 * 重新解析文档
 * @param {number} id 文档ID
 */
export function reparseDocument(id) {
  return request({
    url: `/documents/${id}/reparse`,
    method: 'post',
  })
}

/**
 * 下载Excel文件
 * @param {number} id 文档ID
 * @param {string} fileName 下载文件名
 */
export async function downloadExcel(id, fileName) {
  const token = localStorage.getItem('token')
  try {
    const response = await fetch(`/api/documents/${id}/download`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName || `接口文档_${id}.xlsx`
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  } catch (e) {
    console.error('下载失败:', e)
    throw e
  }
}

/**
 * 删除文档
 * @param {number} id 文档ID
 */
export function deleteDocument(id) {
  return request({
    url: `/documents/${id}`,
    method: 'delete',
  })
}
