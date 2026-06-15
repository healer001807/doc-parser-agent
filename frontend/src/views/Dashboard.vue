<template>
  <div class="app-layout">
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-left">
        <el-icon :size="24" color="#409eff"><Document /></el-icon>
        <h2>智能文档解析 Agent</h2>
        <el-tag type="info" size="small" effect="plain">v1.0</el-tag>
      </div>
      <div class="header-right">
        <el-button text @click="showChat = !showChat">
          <el-icon><ChatDotRound /></el-icon>
          {{ showChat ? '返回文档' : 'AI 助手' }}
        </el-button>
        <el-dropdown trigger="click">
          <span style="cursor: pointer; display: flex; align-items: center; gap: 6px">
            <el-avatar :size="32" icon="UserFilled" />
            <span>{{ userInfo.displayName || userInfo.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="app-main">
      <!-- 聊天窗口 -->
      <div v-if="showChat" class="chat-container" style="height: 100%">
        <ChatView />
      </div>

      <!-- 文档管理 -->
      <template v-else>
        <!-- 统计卡片 -->
        <div class="document-stats">
          <div class="stat-card">
            <div class="stat-value">{{ documents.length }}</div>
            <div class="stat-label">总文档数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: #67c23a">{{ completedCount }}</div>
            <div class="stat-label">解析成功</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: #e6a23c">{{ parsingCount }}</div>
            <div class="stat-label">解析中</div>
          </div>
          <div class="stat-card">
            <div class="stat-value" style="color: #f56c6c">{{ failedCount }}</div>
            <div class="stat-label">解析失败</div>
          </div>
        </div>

        <!-- 上传区域 -->
        <div
          class="upload-area"
          @click="triggerUpload"
          @dragover.prevent
          @drop.prevent="handleDrop"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <p style="font-size: 16px; color: #606266; margin-top: 12px">
            点击或拖拽接口文档到此区域上传
          </p>
          <p style="font-size: 13px; color: #909399; margin-top: 6px">
            支持 PDF、Word、HTML、TXT、Markdown 格式，单个文件最大 50MB
          </p>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.doc,.docx,.html,.htm,.txt,.md"
          style="display: none"
          @change="handleFileSelect"
        />

        <!-- 文档列表 -->
        <el-card shadow="never" style="border: none">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600; font-size: 16px">文档列表</span>
              <el-button text @click="loadDocuments">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>
          </template>

          <el-table
            :data="documents"
            v-loading="tableLoading"
            empty-text="暂无文档，请上传接口文档"
            stripe
            style="width: 100%"
          >
            <el-table-column label="文件名" min-width="250">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-icon :size="20" :color="getFileIconColor(row.fileType)">
                    <component :is="getFileIcon(row.fileType)" />
                  </el-icon>
                  <span>{{ row.originalName }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="fileType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.fileType?.toUpperCase() }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="接口名称" min-width="180">
              <template #default="{ row }">
                <div style="font-size: 13px">{{ row.internalApiName || '-' }}</div>
                <div style="font-size: 12px; color: #909399">{{ row.externalApiName || '' }}</div>
              </template>
            </el-table-column>

            <el-table-column label="公司" width="120">
              <template #default="{ row }">
                {{ row.companyCode || '-' }}
              </template>
            </el-table-column>

            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <span :class="['status-tag', row.parseStatus?.toLowerCase()]">
                  {{ statusMap[row.parseStatus] || row.parseStatus }}
                </span>
              </template>
            </el-table-column>

            <el-table-column label="上传时间" width="170">
              <template #default="{ row }">
                <span style="font-size: 13px">{{ formatTime(row.createdAt) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  size="small"
                  type="primary"
                  @click="viewDocument(row)"
                  :disabled="row.parseStatus !== 'COMPLETED'"
                >
                  查看
                </el-button>
                <el-button
                  text
                  size="small"
                  type="success"
                  @click="handleDownload(row)"
                  :disabled="row.parseStatus !== 'COMPLETED' || !row.excelPath"
                >
                  下载Excel
                </el-button>
                <el-button
                  text
                  size="small"
                  type="warning"
                  @click="handleReparse(row)"
                  :disabled="row.parseStatus === 'PARSING'"
                  :loading="row._reparsing"
                >
                  重解析
                </el-button>
                <el-button
                  text
                  size="small"
                  type="danger"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document,
  UploadFilled,
  Refresh,
  ChatDotRound,
  ArrowDown,
  SwitchButton,
  UserFilled,
  Document as DocIcon,
  PictureFilled,
  Reading,
  Tickets,
} from '@element-plus/icons-vue'
import {
  getDocumentList,
  uploadDocument,
  reparseDocument,
  downloadExcel,
  deleteDocument,
} from '@/api/document'
import ChatView from '@/components/ChatView.vue'

const router = useRouter()
const fileInput = ref(null)
const tableLoading = ref(false)
const showChat = ref(false)
const documents = ref([])

// 从 localStorage 获取用户信息
const userInfo = JSON.parse(localStorage.getItem('user') || '{}')

// 状态映射
const statusMap = {
  PENDING: '待解析',
  PARSING: '解析中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

// 统计
const completedCount = computed(() =>
  documents.value.filter((d) => d.parseStatus === 'COMPLETED').length
)
const parsingCount = computed(() =>
  documents.value.filter((d) => d.parseStatus === 'PARSING').length
)
const failedCount = computed(() =>
  documents.value.filter((d) => d.parseStatus === 'FAILED').length
)

onMounted(() => {
  loadDocuments()
})

// 加载文档列表
const loadDocuments = async () => {
  tableLoading.value = true
  try {
    const res = await getDocumentList()
    if (res.code === 200) {
      documents.value = (res.data || []).map((d) => ({ ...d, _reparsing: false }))
    }
  } catch (e) {
    console.error('加载文档列表失败:', e)
  } finally {
    tableLoading.value = false
  }
}

// 上传
const triggerUpload = () => {
  fileInput.value.click()
}

const handleFileSelect = async (e) => {
  const file = e.target.files[0]
  if (file) {
    await doUpload(file)
  }
  fileInput.value.value = ''
}

const handleDrop = async (e) => {
  const file = e.dataTransfer.files[0]
  if (file) {
    await doUpload(file)
  }
}

const doUpload = async (file) => {
  // 验证文件大小
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return
  }

  const loading = ElMessage({
    message: `正在上传并解析: ${file.name}...`,
    duration: 0,
    icon: 'loading',
  })

  try {
    const res = await uploadDocument(file)
    if (res.code === 200) {
      ElMessage.success('上传并解析完成')
      await loadDocuments()
    }
  } catch (e) {
    console.error('上传失败:', e)
  } finally {
    loading.close()
  }
}

// 查看文档详情
const viewDocument = (row) => {
  router.push(`/document/${row.id}`)
}

// 下载Excel
const handleDownload = async (row) => {
  try {
    const fileName = row.originalName.replace(/\.[^.]+$/, '') + '_接口文档.xlsx'
    await downloadExcel(row.id, fileName)
    ElMessage.success('下载成功')
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

// 重新解析
const handleReparse = async (row) => {
  row._reparsing = true
  try {
    const res = await reparseDocument(row.id)
    if (res.code === 200) {
      ElMessage.success('重新解析完成')
      await loadDocuments()
    }
  } catch (e) {
    console.error('重解析失败:', e)
  } finally {
    row._reparsing = false
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除 "${row.originalName}" ？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    const res = await deleteDocument(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadDocuments()
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除失败:', e)
    }
  }
}

// 退出登录
const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}

// 工具函数
const formatTime = (time) => {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const getFileIcon = (type) => {
  const map = {
    pdf: 'PictureFilled',
    doc: 'Reading',
    docx: 'Reading',
    html: 'Tickets',
    htm: 'Tickets',
    txt: 'Document',
    md: 'Document',
  }
  return map[type?.toLowerCase()] || 'Document'
}

const getFileIconColor = (type) => {
  const map = {
    pdf: '#f56c6c',
    doc: '#409eff',
    docx: '#409eff',
    html: '#e6a23c',
    htm: '#e6a23c',
    txt: '#909399',
    md: '#909399',
  }
  return map[type?.toLowerCase()] || '#909399'
}
</script>
