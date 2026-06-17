# 智能第三方接口文档解析 Agent (doc-parser-agent)

## 项目概述

本项目是一个基于 **Spring Boot 3 + Spring AI + LangChain4j** 的智能文档解析 Agent，
专门用于解析第三方公司提供的接口文档（PDF、Word、HTML、TXT等格式），
并按照项目规范自动生成标准格式的 Excel 文件。

## 核心功能

| 功能 | 描述 |
|------|------|
| ✅ 用户注册/登录 | JWT 认证，支持多用户 |
| ✅ 文档上传 | 支持 PDF、Word、HTML、TXT、Markdown 等格式 |
| ✅ AI 智能解析 | 基于 LangChain4j + GPT-4o 理解文档结构 |
| ✅ Excel 生成 | 按规范生成三部分结构 Excel |
| ✅ Excel 下载 | 一键下载生成的 Excel 文件 |
| ✅ 重新解析 | 失败后可重新解析 |
| ✅ 文档管理 | 列表查看、删除等 |

## 技术栈

- **JDK 17** - 最新 LTS 版本
- **Spring Boot 3.2.4** - 现代化应用框架
- **Spring AI 0.8.1** - Spring 官方 AI 集成
- **LangChain4j 0.31.0** - Java 版 LangChain 框架
- **OpenAI GPT-4o** - 大语言模型驱动解析
- **Apache Tika / PDFBox / POI** - 多格式文档文本提取
- **Spring Security + JWT** - 安全认证
- **H2 / MySQL** - 数据库支持
- **Apache POI** - Excel 生成

## 项目结构

```
doc-parser-agent/
├── frontend/                        # Vue 3 + Vite 前端
│   └── ...
├── backend/                         # Spring Boot 后端
│   ├── pom.xml                      # Maven 依赖配置
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/docparser/
│   │   │   │   ├── DocParserApplication.java        # Spring Boot 启动类
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.java          # Spring Security 配置
│   │   │   │   │   ├── GlobalExceptionHandler.java  # 全局异常处理
│   │   │   │   │   └── WebConfig.java              # CORS 及资源映射
│   │   │   │   ├── controller/
│   │   │   │   │   ├── AuthController.java          # 注册/登录接口
│   │   │   │   │   └── DocumentController.java      # 文档上传/解析/下载
│   │   │   │   ├── model/
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── User.java                # 用户实体
│   │   │   │   │   │   └── Document.java            # 文档实体
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── RegisterRequest.java     # 注册请求
│   │   │   │   │   │   ├── LoginRequest.java        # 登录请求
│   │   │   │   │   │   ├── AuthResponse.java        # 认证响应
│   │   │   │   │   │   ├── InterfaceInfo.java       # 接口基本信息
│   │   │   │   │   │   ├── FieldDefinition.java     # 字段定义
│   │   │   │   │   │   └── ParseResult.java         # 解析结果结构
│   │   │   │   │   └── vo/
│   │   │   │   │       └── ApiResponse.java         # 统一响应体
│   │   │   │   ├── repository/
│   │   │   │   │   ├── UserRepository.java          # 用户仓储
│   │   │   │   │   └── DocumentRepository.java      # 文档仓储
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtAuthenticationFilter.java # JWT 过滤器
│   │   │   │   │   └── JwtAuthenticationEntryPoint.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java             # 认证服务
│   │   │   │   │   ├── AiParserService.java         # AI 解析接口
│   │   │   │   │   ├── AiParserServiceImpl.java     # AI 解析实现（核心）
│   │   │   │   │   ├── DocumentParserService.java   # 文档解析服务接口
│   │   │   │   │   ├── DocumentParserServiceImpl.java # 文档解析实现
│   │   │   │   │   └── ExcelGeneratorService.java   # Excel 生成服务
│   │   │   │   └── util/
│   │   │   │       └── JwtUtil.java                 # JWT 工具类
│   │   │   └── resources/
│   │   │       └── application.yml                  # 配置文件
│   │   └── test/java/com/example/docparser/
│   │       └── DocParserApplicationTests.java
│   ├── uploads/                      # 上传文件存储
│   └── exports/                      # Excel 导出文件
├── README.md
├── AGENTS.md
└── .gitignore
```

## Excel 输出格式

生成的 Excel 包含 **3 个 Sheet**：

### Sheet 1: 接口信息
| 字段 | 说明 |
|------|------|
| 内部接口名 | 项目内部使用的接口名称 |
| 外部接口名 | 第三方文档提供的接口名称 |
| 第三方公司简称 | 提供接口的公司缩写 |
| 数据来源 | 数据来源说明 |
| 请求方式 | GET/POST/PUT/DELETE |
| 请求URL | 接口请求路径 |
| 协议 | HTTP/HTTPS |
| 数据格式 | JSON/XML/Form |
| 接口描述 | 接口功能说明 |
| 备注 | 补充说明 |

### Sheet 2: 请求参数
| 字段英文(文档提供) | 字段描述 | 类型 | 长度 | 是否必传 | 字段英文(文档提供) | 字段英文(文档提供) |
|-------------------|---------|------|------|---------|-----------------|-----------------|

分为两部分：
- **请求头**：调用系统(callSystem)、查询标识(queryId)
- **请求体参数**：根据文档实际字段解析

### Sheet 3: 响应参数
| 字段英文(文档提供) | 字段描述 | 类型 | 长度 | 是否必传 | 字段英文(文档提供) | 字段英文(文档提供) |
|-------------------|---------|------|------|---------|-----------------|-----------------|

分为两部分：
- **响应公共体**：code、message、data（Java统一响应体）
- **响应业务参数**：根据文档实际字段解析

## API 接口文档

### 认证接口

#### 用户注册
```
POST /api/auth/register
Content-Type: application/json

{
    "username": "admin",
    "password": "123456",
    "displayName": "管理员",
    "email": "admin@example.com",
    "companyName": "某科技有限公司"
}
```

#### 用户登录
```
POST /api/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "123456"
}
```

### 文档管理接口

#### 上传并解析文档
```
POST /api/documents/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: @接口文档.pdf
```

#### 获取文档列表
```
GET /api/documents/list
Authorization: Bearer <token>
```

#### 获取文档详情
```
GET /api/documents/{id}
Authorization: Bearer <token>
```

#### 获取解析结果
```
GET /api/documents/{id}/parse-result
Authorization: Bearer <token>
```

#### 重新解析文档
```
POST /api/documents/{id}/reparse
Authorization: Bearer <token>
```

#### 下载 Excel
```
GET /api/documents/{id}/download
Authorization: Bearer <token>
```

#### 删除文档
```
DELETE /api/documents/{id}
Authorization: Bearer <token>
```

## 快速启动

### 环境要求
- JDK 17+
- Maven 3.8+
- OpenAI API Key（或兼容的 API）

### 配置修改

编辑 `backend/src/main/resources/application.yml`，设置你的 OpenAI API Key：

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: sk-your-openai-api-key  # 替换为你的 Key
```

或者通过环境变量设置：
```bash
set OPENAI_API_KEY=sk-your-openai-api-key
```

### 前端构建（必须先构建前端）

```bash
# 安装依赖
cd frontend
npm install

# 开发模式（热更新，需要同时启动后端）
npm run dev
# 访问 http://localhost:3000

# 构建生产版本（输出到后端 static 目录）
npm run build
cd ..
```

### 启动

```bash
# 先构建前端
cd frontend && npm install && npm run build && cd ..

# 进入后端目录编译并启动
cd backend && mvn clean spring-boot:run

# 或打包后启动（前端已构建的情况下）
cd backend && mvn clean package -DskipTests
java -jar target/doc-parser-agent-1.0.0.jar
```

### 访问

- **前端页面**: `http://localhost:8080/api/`
- **API 基础路径**: `http://localhost:8080/api`
- **H2 数据库控制台**: `http://localhost:8080/api/h2-console`

### 默认账号

首次使用请先注册账号，然后登录使用。

## AI 解析原理

1. **文档文本提取** - 使用 Apache Tika、PDFBox、POI 等工具从各种格式文件中提取纯文本
2. **Prompt Engineering** - 构建专业的系统提示词，引导 AI 理解接口文档结构
3. **LangChain4j 调用** - 通过 LangChain4j 调用 OpenAI GPT-4o 模型
4. **结构化提取** - AI 按照预定义的 JSON Schema 提取接口信息、请求参数、响应参数
5. **降级策略** - AI 解析失败时，使用正则表达式进行基础字段提取作为兜底
6. **Excel 生成** - 使用 Apache POI 按照规范格式生成美观的 Excel 文件

## 后续可扩展

- [ ] 多模型支持（Claude、Gemini、通义千问等）
- [ ] WebSocket 实时推送解析进度
- [ ] 前端界面优化
- [ ] 批量化文档处理
- [ ] 接口字段映射关系自动生成
- [ ] 文档版本管理
- [ ] 自定义 Prompt 模板
- [ ] 字段映射校验
- [ ] 项目必要字段扩展（后续提供）
