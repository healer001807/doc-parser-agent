<template>
  <div class="chat-container" style="height: 100%; display: flex; flex-direction: column">
    <!-- 聊天头部 -->
    <div style="padding: 16px 20px; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; gap: 8px">
      <el-avatar :size="32" icon="MagicStick" style="background: #409eff" />
      <div>
        <div style="font-weight: 600; font-size: 14px">AI 解析助手</div>
        <div style="font-size: 12px; color: #909399">智能文档解析 Agent</div>
      </div>
    </div>

    <!-- 消息列表 -->
    <div ref="messageListRef" class="chat-messages" style="flex: 1; overflow-y: auto">
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        :class="['chat-message', msg.role]"
      >
        <div class="avatar">
          <el-icon v-if="msg.role === 'ai'"><MagicStick /></el-icon>
          <el-icon v-else><UserFilled /></el-icon>
        </div>
        <div class="bubble" v-html="renderMarkdown(msg.content)"></div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="chat-message ai">
        <div class="avatar">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div class="bubble" style="background: #f0f2f5">
          <span class="dot-pulse"></span>
        </div>
      </div>

      <div v-if="messages.length === 0 && !loading" style="text-align: center; padding: 80px 20px; color: #c0c4cc">
        <el-icon :size="48"><ChatLineSquare /></el-icon>
        <p style="margin-top: 12px; font-size: 14px">
          你好！我是 AI 解析助手，可以帮你：
        </p>
        <p style="font-size: 13px; margin-top: 8px">
          • 上传接口文档进行智能解析<br />
          • 查询文档解析状态和结果<br />
          • 下载生成的 Excel 文件<br />
          • 解答接口文档相关问题
        </p>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div v-if="recentDocuments.length > 0" style="padding: 8px 20px; border-top: 1px solid #f0f0f0">
      <div style="display: flex; gap: 6px; flex-wrap: wrap">
        <el-tag
          v-for="doc in recentDocuments"
          :key="doc.id"
          closable
          size="small"
          effect="plain"
          @click="askAboutDocument(doc)"
          @close="removeRecentDoc(doc)"
        >
          {{ doc.originalName?.substring(0, 20) }}
        </el-tag>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area" style="border-top: 1px solid #e4e7ed">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="输入您的问题，例如：如何上传文档？或输入文档名称查询..."
        :disabled="loading"
        @keyup.enter.prevent="sendMessage"
      />
      <el-button
        type="primary"
        :icon="Promotion"
        :loading="loading"
        @click="sendMessage"
        style="height: 56px"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, UserFilled, ChatLineSquare, Promotion } from '@element-plus/icons-vue'
import { getDocumentList, getParseResult } from '@/api/document'
import { marked } from 'marked'

// 配置 marked 选项
marked.setOptions({
  breaks: true,
  gfm: true,
})

const messageListRef = ref(null)
const inputText = ref('')
const loading = ref(false)
const messages = ref([
  {
    role: 'ai',
    content: `## 👋 欢迎使用智能文档解析 Agent

我是您的 AI 解析助手，可以帮助您：

**📄 文档解析**
上传第三方接口文档（PDF/Word/HTML/TXT），AI 自动提取接口信息

**📊 Excel 生成**
按项目规范自动生成三部分结构的 Excel 文件

**💬 您可以问我：**
- 「帮我解析一份接口文档」
- 「查看我的文档列表」
- 「查询文档解析状态」
- 「解释某个字段的含义」

> 请先上传文档，或选择右侧菜单中的「上传文档」开始使用吧！`,
  },
])

const recentDocuments = ref([])

onMounted(async () => {
  // 加载最近的文档列表
  try {
    const res = await getDocumentList()
    if (res.code === 200 && res.data) {
      recentDocuments.value = res.data.slice(0, 5)
    }
  } catch (e) {
    // 忽略
  }
  scrollToBottom()
})

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    // 处理用户输入
    const reply = await processUserInput(text)
    messages.value.push({ role: 'ai', content: reply })
  } catch (e) {
    messages.value.push({
      role: 'ai',
      content: `😅 抱歉，处理您的问题时遇到了一些问题：${e.message}。请稍后再试。`,
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

/**
 * 处理用户输入 - 简单的意图识别
 */
const processUserInput = async (input) => {
  const lower = input.toLowerCase()

  // 问候
  if (/^(你好|hi|hello|嗨|hey)$/i.test(lower)) {
    return `你好！👋 有什么可以帮助你的吗？

你可以：
- **上传文档** - 在左侧点击「上传文档」或拖拽文件到上传区域
- **查看文档列表** - 查看所有已上传的文档
- **查询文档解析状态** - 告诉我文档名称，我帮你查询`
  }

  // 查询文档列表
  if (/文档列表|有哪些文档|所有文档|list/i.test(lower)) {
    const res = await getDocumentList()
    if (res.code === 200 && res.data?.length > 0) {
      let reply = `## 📋 文档列表（共 ${res.data.length} 个）\n\n`
      res.data.forEach((doc, idx) => {
        const statusMap = { PENDING: '⏳', PARSING: '🔄', COMPLETED: '✅', FAILED: '❌' }
        const icon = statusMap[doc.parseStatus] || '📄'
        reply += `${idx + 1}. ${icon} **${doc.originalName}**`
        if (doc.internalApiName) reply += ` — ${doc.internalApiName}`
        reply += `\n`
      })
      reply += `\n> 输入文档名称可以查看详情，或前往「文档管理」页面操作。`
      return reply
    } else {
      return `📭 目前还没有文档，请先点击「上传文档」上传接口文档。`
    }
  }

  // 查询特定文档
  if (recentDocuments.value.length > 0) {
    const matched = recentDocuments.value.find(
      (doc) => doc.originalName?.toLowerCase().includes(lower)
    )
    if (matched) {
      return await getDocDetailReply(matched)
    }
  }

  // 关于解析 / 上传
  if (/上传|解析|parse|upload/i.test(lower)) {
    return `## 📤 如何上传和解析文档

1. 在 **文档管理** 页面，点击上传区域或拖拽文件到虚线框
2. 支持格式：**PDF、Word（.doc/.docx）、HTML、TXT、Markdown**
3. 单文件最大 **50MB**
4. 上传后系统会自动调用 AI 进行解析
5. 解析完成后会自动生成 Excel 文件

**解析流程：**
\`\`\`
上传文档 → 文本提取 → AI智能解析 → 结构化输出 → Excel生成
\`\`\`

> 💡 上传后请稍等片刻，解析大文档可能需要 1-2 分钟。`
  }

  // Excel 下载
  if (/excel|下载|export/i.test(lower)) {
    return `## 📥 如何下载 Excel

1. 在 **文档管理** 页面找到已解析完成的文档
2. 点击右侧的 **「下载Excel」** 按钮
3. 文件将自动下载到本地

生成的 Excel 包含 3 个 Sheet：
- **Sheet1** - 接口基本信息
- **Sheet2** - 请求参数（请求头 + 请求体）
- **Sheet3** - 响应参数（响应公共体 + 业务参数）`
  }

  // 默认回复
  return `## 🤔 我理解您的问题

不过我是一个专注于 **接口文档解析** 的 AI 助手，我的主要功能是：

- ✅ 解析 PDF/Word/HTML 等格式的接口文档
- ✅ 自动提取接口名称、请求参数、响应参数
- ✅ 按规范生成 Excel 文件
- ✅ 查询文档解析状态

**您可以试试：**
- 「查看我的文档列表」
- 「如何上传文档」
- 「如何下载 Excel」

或者直接在 **文档管理** 页面操作更方便哦！😊`
}

/**
 * 获取文档详情回复
 */
const getDocDetailReply = async (doc) => {
  let reply = `## 📄 ${doc.originalName}\n\n`

  const statusMap = {
    PENDING: '⏳ 待解析',
    PARSING: '🔄 解析中',
    COMPLETED: '✅ 已完成',
    FAILED: '❌ 失败',
  }

  reply += `**状态**：${statusMap[doc.parseStatus] || doc.parseStatus}\n`
  reply += `**类型**：${doc.fileType?.toUpperCase() || '-'}\n`

  if (doc.internalApiName) {
    reply += `**内部接口名**：${doc.internalApiName}\n`
  }
  if (doc.externalApiName) {
    reply += `**外部接口名**：${doc.externalApiName}\n`
  }
  if (doc.companyCode) {
    reply += `**第三方公司**：${doc.companyCode}\n`
  }

  if (doc.parseStatus === 'COMPLETED') {
    try {
      const res = await getParseResult(doc.id)
      if (res.code === 200 && res.data) {
        const pr = res.data
        const reqCount = (pr.requestParams?.length || 0) + (pr.requestHeaders?.length || 0)
        const rspCount = (pr.responseParams?.length || 0) + (pr.responseCommonBody?.length || 0)

        reply += `\n**解析结果摘要：**\n`
        reply += `- 请求参数：**${reqCount}** 个字段\n`
        reply += `- 响应参数：**${rspCount}** 个字段\n`
        reply += `- Excel：${doc.excelPath ? '✅ 已生成' : '❌ 未生成'}\n`
        reply += `\n> [点击查看详情 →](${window.location.origin}/#/document/${doc.id})`
      }
    } catch (e) {
      // 忽略
    }
  } else if (doc.parseStatus === 'FAILED') {
    reply += `\n**错误信息**：${doc.errorMessage || '未知错误'}\n`
    reply += `\n> 可以尝试重新解析该文档。`
  }

  return reply
}

const askAboutDocument = (doc) => {
  inputText.value = doc.originalName
  sendMessage()
}

const removeRecentDoc = (doc) => {
  recentDocuments.value = recentDocuments.value.filter((d) => d.id !== doc.id)
}

const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    return marked(text)
  } catch {
    return text
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}
</script>

<style scoped>
.dot-pulse {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #409eff;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}
</style>
