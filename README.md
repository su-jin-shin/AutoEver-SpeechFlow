# 🚗 AutoEver-SpeechFlow 🚗

## 🗣 Solace 기반 실시간 STT 변환 시스템 

### 📝 프로젝트 개요
본 프로젝트는 **Solace PubSub+ 메시징 시스템**을 활용하여 **실시간 STT 변환 및 데이터 전송을 수행하는 WebSocket + gRPC 기반 시스템**입니다.
고객센터의 STT 데이터를 실시간으로 처리하고(본 프로젝트에서는 브라우저에서 녹음한 음성을 활용), 이를 CRM 시스템과 연계하여 **상담 요약**, **실시간 상담 가이드** 등을 제공하는 것을 목표로 합니다.

### 🛠️ 기술 스택
- **Backend**: Java (Spring Boot) - gRPC 서버/클라이언트, WebSocket 서버
- **STT Engine**: Google Speech-to-Text API (Python - Flask 또는 FastAPI)
- **Frontend**: HTML, JavaScript (브라우저에서 사용자의 음성 녹음 처리)
- **Messaging**: Solace PubSub+
- **Communication**: WebSocket, gRPC

### 주요 기능
- ✅ **WebSocket을 통한 실시간 데이터 전송** (완료)
- ✅ **gRPC 기반 STT 변환 처리** (완료)
- ⏳ **Solace를 활용한 메시지 송수신** (일부 완료)
- ⏳ **STT 데이터를 CRM 시스템과 연계** (진행 중)
- ⏳ **상담 요약 또는 실시간 상담 가이드 제공 로직 추가** (예정)

### 참고사항
본 프로젝트는 **Hyundai AutoEver 지원을 위한 포트폴리오용 프로젝트**로 개발 중이며, **현재 기능 추가 및 최적화가 진행 중**입니다.
