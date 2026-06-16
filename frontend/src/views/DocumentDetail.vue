<template>
  <div class="app-layout">
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <el-divider direction="vertical" />
        <h2>文档详情</h2>
        <el-tag v-if="document.originalName" type="info" effect="plain">
          {{ document.originalName }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleDownload" :disabled="!document.excelPath">
          <el-icon><Download /></el-icon> 下载 Excel
        </el-button>
      </div>
    </header>

    <main class="app-main">
      <div v-loading="loading">
        <!-- 未找到 -->
        <el-empty v-if="!document" description="文档不存在" />

        <template v-else>
          <!-- 第一部分：接口基本信息 -->
          <div class="detail-section">
            <div class="section-title">
              <el-icon><InfoFilled /></el-icon> 接口基本信息
            </div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="内部接口名">
                {{ parseResult?.interfaceInfo?.internalApiName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="外部接口名">
                {{ parseResult?.interfaceInfo?.externalApiName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="第三方公司简称">
                {{ parseResult?.interfaceInfo?.companyCode || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="数据来源">
                {{ parseResult?.interfaceInfo?.dataSource || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="请求方式">
                <el-tag size="small" effect="dark" color="#409eff">
                  {{ parseResult?.interfaceInfo?.httpMethod || '-' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="数据格式">
                {{ parseResult?.interfaceInfo?.dataFormat || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="请求URL" :span="2">
                <code style="font-size: 13px; color: #409eff">
                  {{ parseResult?.interfaceInfo?.requestUrl || '-' }}
                </code>
              </el-descriptions-item>
              <el-descriptions-item label="接口描述" :span="2">
                {{ parseResult?.interfaceInfo?.apiDescription || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 第二部分：请求参数 -->
          <div class="detail-section">
            <div class="section-title">
              <el-icon><Upload /></el-icon> 请求参数
            </div>

            <!-- 请求头 -->
            <h4 style="margin: 12px 0 8px; color: #606266">请求头</h4>
            <table class="param-table" v-if="parseResult?.requestHeaders?.length">
              <thead>
                <tr>
                  <th>字段英文(文档提供)</th>
                  <th>字段描述</th>
                  <th>类型</th>
                  <th>长度</th>
                  <th>是否必传</th>
                  <th>字段英文(文档提供)</th>
                  <th>字段英文(文档提供)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(field, idx) in parseResult.requestHeaders" :key="'rh-' + idx">
                  <td><code>{{ field.fieldNameEn }}</code></td>
                  <td>{{ field.fieldDescription }}</td>
                  <td>{{ field.fieldType }}</td>
                  <td>{{ field.length || '-' }}</td>
                  <td>
                    <el-tag :type="field.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                      {{ field.required === 'Y' ? '必传' : '非必传' }}
                    </el-tag>
                  </td>
                  <td><code>{{ field.fieldNameEn2 }}</code></td>
                  <td><code>{{ field.fieldNameEn3 }}</code></td>
                </tr>
              </tbody>
            </table>
            <el-empty v-else description="暂无请求头数据" :image-size="60" />

            <!-- 请求体参数 -->
            <h4 style="margin: 20px 0 8px; color: #606266">请求体参数</h4>
            <table class="param-table" v-if="parseResult?.requestParams?.length">
              <thead>
                <tr>
                  <th>字段英文(文档提供)</th>
                  <th>字段描述</th>
                  <th>类型</th>
                  <th>长度</th>
                  <th>是否必传</th>
                  <th>字段英文(文档提供)</th>
                  <th>字段英文(文档提供)</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="(field, idx) in parseResult.requestParams" :key="'rp-' + idx">
                  <tr>
                    <td><code>{{ field.fieldNameEn }}</code></td>
                    <td>{{ field.fieldDescription }}</td>
                    <td>{{ field.fieldType }}</td>
                    <td>{{ field.length || '-' }}</td>
                    <td>
                      <el-tag :type="field.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                        {{ field.required === 'Y' ? '必传' : '非必传' }}
                      </el-tag>
                    </td>
                    <td><code>{{ field.fieldNameEn2 }}</code></td>
                    <td><code>{{ field.fieldNameEn3 }}</code></td>
                  </tr>
                  <tr v-for="(child, cidx) in field.children" :key="'rp-' + idx + '-' + cidx" class="child-row">
                    <td><code>└─ {{ child.fieldNameEn }}</code></td>
                    <td>{{ child.fieldDescription }}</td>
                    <td>{{ child.fieldType }}</td>
                    <td>{{ child.length || '-' }}</td>
                    <td>
                      <el-tag :type="child.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                        {{ child.required === 'Y' ? '必传' : '非必传' }}
                      </el-tag>
                    </td>
                    <td><code>{{ child.fieldNameEn2 }}</code></td>
                    <td><code>{{ child.fieldNameEn3 }}</code></td>
                  </tr>
                </template>
              </tbody>
            </table>
            <el-empty v-else description="暂无请求体参数数据" :image-size="60" />
          </div>

          <!-- 第三部分：响应参数 -->
          <div class="detail-section">
            <div class="section-title">
              <el-icon><Download /></el-icon> 响应参数
            </div>

            <!-- 响应公共体 -->
            <h4 style="margin: 12px 0 8px; color: #606266">响应公共体（统一响应体）</h4>
            <table class="param-table" v-if="parseResult?.responseCommonBody?.length">
              <thead>
                <tr>
                  <th>字段英文(文档提供)</th>
                  <th>字段描述</th>
                  <th>类型</th>
                  <th>长度</th>
                  <th>是否必传</th>
                  <th>字段英文(文档提供)</th>
                  <th>字段英文(文档提供)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(field, idx) in parseResult.responseCommonBody" :key="'rcb-' + idx">
                  <td><code>{{ field.fieldNameEn }}</code></td>
                  <td>{{ field.fieldDescription }}</td>
                  <td>{{ field.fieldType }}</td>
                  <td>{{ field.length || '-' }}</td>
                  <td>
                    <el-tag :type="field.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                      {{ field.required === 'Y' ? '必传' : '非必传' }}
                    </el-tag>
                  </td>
                  <td><code>{{ field.fieldNameEn2 }}</code></td>
                  <td><code>{{ field.fieldNameEn3 }}</code></td>
                </tr>
              </tbody>
            </table>
            <el-empty v-else description="暂无响应公共体数据" :image-size="60" />

            <!-- 响应业务参数 -->
            <h4 style="margin: 20px 0 8px; color: #606266">响应业务参数</h4>
            <table class="param-table" v-if="parseResult?.responseParams?.length">
              <thead>
                <tr>
                  <th>字段英文(文档提供)</th>
                  <th>字段描述</th>
                  <th>类型</th>
                  <th>长度</th>
                  <th>是否必传</th>
                  <th>字段英文(文档提供)</th>
                  <th>字段英文(文档提供)</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="(field, idx) in parseResult.responseParams" :key="'rsp-' + idx">
                  <tr>
                    <td><code>{{ field.fieldNameEn }}</code></td>
                    <td>{{ field.fieldDescription }}</td>
                    <td>{{ field.fieldType }}</td>
                    <td>{{ field.length || '-' }}</td>
                    <td>
                      <el-tag :type="field.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                        {{ field.required === 'Y' ? '必传' : '非必传' }}
                      </el-tag>
                    </td>
                    <td><code>{{ field.fieldNameEn2 }}</code></td>
                    <td><code>{{ field.fieldNameEn3 }}</code></td>
                  </tr>
                  <tr v-for="(child, cidx) in field.children" :key="'rsp-' + idx + '-' + cidx" class="child-row">
                    <td><code>└─ {{ child.fieldNameEn }}</code></td>
                    <td>{{ child.fieldDescription }}</td>
                    <td>{{ child.fieldType }}</td>
                    <td>{{ child.length || '-' }}</td>
                    <td>
                      <el-tag :type="child.required === 'Y' ? 'danger' : 'info'" size="small" effect="plain">
                        {{ child.required === 'Y' ? '必传' : '非必传' }}
                      </el-tag>
                    </td>
                    <td><code>{{ child.fieldNameEn2 }}</code></td>
                    <td><code>{{ child.fieldNameEn3 }}</code></td>
                  </tr>
                </template>
              </tbody>
            </table>
            <el-empty v-else description="暂无响应业务参数数据" :image-size="60" />
          </div>

          <!-- 原始文档信息 -->
          <div class="detail-section">
            <div class="section-title">
              <el-icon><Document /></el-icon> 文档信息
            </div>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="文件名">{{ document.originalName }}</el-descriptions-item>
              <el-descriptions-item label="文件类型">
                <el-tag size="small">{{ document.fileType?.toUpperCase() }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="文件大小">
                {{ formatFileSize(document.fileSize) }}
              </el-descriptions-item>
              <el-descriptions-item label="解析状态">
                <span :class="['status-tag', document.parseStatus?.toLowerCase()]">
                  {{ statusMap[document.parseStatus] }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="上传时间">{{ formatTime(document.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatTime(document.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="document.errorMessage" style="margin-top: 12px">
              <el-alert :title="document.errorMessage" type="error" show-icon :closable="false" />
            </div>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Download,
  InfoFilled,
  Upload,
  Document,
} from '@element-plus/icons-vue'
import { getDocument, getParseResult, downloadExcel } from '@/api/document'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const document = ref(null)
const parseResult = ref(null)

const statusMap = {
  PENDING: '待解析',
  PARSING: '解析中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

onMounted(async () => {
  const id = route.params.id
  try {
    const docRes = await getDocument(id)
    if (docRes.code === 200) {
      document.value = docRes.data
      // 如果解析完成，获取解析结果
      if (docRes.data.parseStatus === 'COMPLETED') {
        const resultRes = await getParseResult(id)
        if (resultRes.code === 200) {
          parseResult.value = resultRes.data
        }
      }
    }
  } catch (e) {
    console.error('获取文档详情失败:', e)
  } finally {
    loading.value = false
  }
})

const goBack = () => {
  router.push('/dashboard')
}

const handleDownload = async () => {
  if (!document.value?.excelPath) {
    ElMessage.warning('Excel 文件尚未生成')
    return
  }
  try {
    const fileName = document.value.originalName.replace(/\.[^.]+$/, '') + '_接口文档.xlsx'
    await downloadExcel(document.value.id, fileName)
    ElMessage.success('下载成功')
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>
