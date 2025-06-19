# Polyglot Translator

📚 [English](./README_EN.md) | [简体中文](./README.md)

> A universal backend for speech-to-text and multilingual translation, powered by Whisper and large
> language models (LLMs), with support for task scheduling and efficient querying.

## ✨ Features

- 🎙️ **Speech Recognition (STT)**: Based on [Whisper](https://github.com/openai/whisper)
- 🌐 **Multilingual Translation**: Supports multiple target languages using LLMs (e.g., OpenAI,
  Gemini)
- 🔍 **STT Accuracy Verification**: Validates transcribed text using original reference input
- 📦 **Multilingual Packaging**: Encodes and compresses all translations into a compact, queryable
  file
- 🧩 **Task Scheduling & Management**: Supports creation, cancellation, and querying of translation
  tasks
- ⚙️ **Fast Lookup API**: Quickly retrieve `language → text ID → source (text/audio)` content via
  API

---

## 🧱 Project Structure

```

polyglot-translator/
├── core                # Core module, shared entities and DTOs
├── api-server          # Provides REST API, manages and publishes translation tasks
└── worker              # Stateless executor that pulls and runs tasks, horizontally scalable

````

---

## 🧠 Features

### 🎙️ Whisper Speech Recognition

- [x] Use Whisper model for speech recognition
- [x] Provide HTTP interface
  via [whisper-asr-webservice](https://github.com/ahmetoner/whisper-asr-webservice)
- [x] Validate recognized text against original content

### 🌐 Multilingual Translation

- [x] Supports English, Simplified Chinese, Traditional Chinese, Japanese, etc.
- [x] Translation powered by LLMs (e.g., OpenAI, Gemini)

### 📦 Encoding & Packaging

- [x] Compress all translation results into a compact file
- [x] Fast lookup by language, text ID, and content source

### 🧩 Background Task Scheduling

- [x] Workers are stateless and support horizontal scaling
- [x] Monitor memory usage to avoid OOM during task processing
- [x] Support task cancellation, retries, failover — ensuring tasks aren't lost

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Kotlin 2.1+
- Docker, Docker Compose
- OpenAI / Gemini API Key

### Build the Project

```bash
./gradlew clean build
````

### Start the API Server

```bash
./gradlew :api-server:bootRun
```

### Start the Worker

> **Note:** Make sure the OpenAI / Gemini API Key is configured in environment variables, or the
> service won't function properly.

```bash
./gradlew :worker:bootRun
```

---

## 🧪 Testing

```bash
./gradlew test
```

---

## 📁 Example Usage

1. Upload audio file and corresponding original text
2. Create a translation task via API
3. Wait for the background worker to complete processing
4. Download the packaged multilingual result
5. Query translations by language and text ID via API

---

## 📄 License

MIT