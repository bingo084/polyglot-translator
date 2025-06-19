# Polyglot Translator

📚 [English](./README_EN.md) | [简体中文](./README.md)

> 通用的语音转文本与多语种翻译后端，集成 Whisper 语音识别与大型语言模型（LLM）翻译，支持任务调度与高效查询。

## ✨ 功能亮点

- 🎙️ **语音识别（STT）**：基于 [Whisper](https://github.com/openai/whisper) 模型
- 🌐 **多语种翻译**：支持多种目标语言，基于 LLM（如 OpenAI、Gemini）
- 🔍 **识别文本准确性校验**：使⽤原始⽂本信息对 STT ⽣成的内容进⾏准确性校验
- 📦 **多语种文本打包**：将所有翻译结果编码压缩为一个可快速查询的文件
- 🧩 **任务调度与管理**：支持翻译任务的创建、取消、查询
- ⚙️ **快速查询接口**：通过接口可快速获取 `语言 -> 文本编号 -> 来源（文本/音频）` 的内容

---

## 🧱 项目结构

```
polyglot-translator/
├── core                # 核心模块，实体、DTO 复用
├── api-server          # 提供 REST API，管理和发布翻译任务
└── worker              # 拉取并执行任务的无状态执行器，支持水平扩展
```

---

## 🧠 设计要点

### 🎙️ Whisper 语音识别

- [x] 使用 Whisper 模型进行语音识别
- [x] 通过 [whisper-asr-webservice](https://github.com/ahmetoner/whisper-asr-webservice) 提供 HTTP
  服务调用
- [x] 识别文本会与原始文本进行准确性校验

### 🌐 多语言翻译

- [x] 支持英文、简体中文、繁体中文、日语等
- [x] 使用 LLM （如 OpenAI、Gemini）进行翻译

### 📦 编码与打包

- [ ] 所有翻译结果压缩编码为一个紧凑文件
- [ ] 可快速根据语言、文本编号、文本来源查询相应文本

### 🧩 后台任务调度

- [x] worker 为无状态服务，支持水平扩展
- [x] 监控内存使用，消费任务时会检查当前节点内存，避免 OOM
- [x] 支持任务取消、失败重试、故障转移，不丢失任务

---

## 🚀 快速开始

### 环境准备

- Java 21+
- Kotlin 2.1+
- Docker、Docker Compose
- OpenAI / Gemini API Key

### 构建项目

```bash
./gradlew clean build
```

### 启动 api-server 服务

```bash
./gradlew :api-server:bootRun
```

### 启动 worker 服务

> **注意：** 请确保环境变量中配置了 OpenAI / Gemini API Key，否则服务无法正常工作。

```bash
./gradlew :worker:bootRun
```

---

## 🧪 测试

```bash
./gradlew test
```

---

## 📁 使用示例

1. 上传音频文件和对应原始文本
2. 通过 API 创建翻译任务
3. 等待后台 worker 处理完成
4. 下载打包后的多语种翻译文件
5. 通过查询接口按语言和文本编号获取翻译内容

---

## 📄 开源协议

MIT
