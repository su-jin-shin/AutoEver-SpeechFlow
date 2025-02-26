class AudioProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.audioChunks = [];
        this.silenceThreshold = 0.02;
        this.silenceDuration = 0.8;
        this.stopDuration = 10;
        this.silenceStart = null;
        this.sttLock = false; // STT 요청 중복 방지
        this.sttCooldown = 0; // STT 요청 간격을 조정하는 변수

        // 앞뒤 무음 길이 조절
        this.trailingSilenceBuffer = [];
        this.trailingSilenceLimit = sampleRate * 0.4;

        this.postSilenceBuffer = [];
        this.postSilenceLimit = sampleRate * 0.3;
    }

    process(inputs, outputs, parameters) {
        const input = inputs[0];
        if (input.length > 0) {
            const buffer = input[0];

            let volume = 0;
            for (let i = 0; i < buffer.length; i++) {
                volume += Math.abs(buffer[i]);
            }
            volume /= buffer.length;

            if (volume > this.silenceThreshold) {
                if (this.silenceStart !== null) {
                    console.log('새로운 발화 감지! STT 재시작 가능');
                    this.silenceStart = null;
                    this.sttLock = false; // 새로운 발화 감지 → STT 요청 가능
                }

                this.audioChunks.push(...this.trailingSilenceBuffer);
                this.audioChunks.push(new Float32Array(buffer));

                this.trailingSilenceBuffer = [];
                this.postSilenceBuffer = [];
            } else {
                if (this.silenceStart === null) {
                    this.silenceStart = currentTime;
                }

                if (this.trailingSilenceBuffer.length < this.trailingSilenceLimit) {
                    this.trailingSilenceBuffer.push(new Float32Array(buffer));
                }

                if (this.postSilenceBuffer.length < this.postSilenceLimit) {
                    this.postSilenceBuffer.push(new Float32Array(buffer));
                }

                // 0.8초 이상 무음이면 STT 요청
                if (!this.sttLock && currentTime - this.silenceStart > this.silenceDuration) {
                    if (this.audioChunks.length > 0) {
                        this.audioChunks.push(...this.postSilenceBuffer);

                        // 최소 전송 데이터 길이 조정 (0.2초 → 0.8초)
                        let audioLength = this.audioChunks.reduce((sum, chunk) => sum + chunk.length, 0);
                        let minRequiredSamples = sampleRate * 0.8; // 최소 0.8초 분량 (0.2초 → 0.8초로 증가)

                        // 빈 데이터 요청 차단 (완전히 비어 있는 경우)
                        if (audioLength === 0) {
                            console.warn('⚠️ STT 요청 스킵됨 (오디오 데이터가 없음)');
                            return;
                        }

                        if (audioLength >= minRequiredSamples) {
                            if (currentTime - this.sttCooldown > 1) {
                                this.sttCooldown = currentTime;
                                console.log(`📡 STT 요청 보냄: ${audioLength / sampleRate} 초`);

                                // WebSocket 데이터 크기 제한 (16KB씩 전송)
                                let maxChunkSize = 16000;
                                let totalChunks = Math.ceil(audioLength / maxChunkSize);

                                for (let i = 0; i < totalChunks; i++) {
                                    let chunkStart = i * maxChunkSize;
                                    let chunkEnd = Math.min(chunkStart + maxChunkSize, audioLength);
                                    let audioChunk = this.audioChunks.slice(chunkStart, chunkEnd);

                                    this.port.postMessage(audioChunk); // 청크 단위로 WebSocket에 전송
                                }

                                this.sttLock = true; // STT 요청 후 추가 요청 방지 (잠금)
                            } else {
                                console.warn('⚠️ STT 요청 스킵됨');
                            }
                        } else {
                            console.warn(`⚠️ STT 요청 스킵됨 (오디오 길이가 너무 짧음: ${audioLength / sampleRate} 초)`);
                        }

                        // STT 요청 후 무음 상태에서는 요청 금지 (잠금)
                        this.audioChunks = [];
                        this.postSilenceBuffer = [];
                    }
                }

                // 10초 이상 무음이면 녹음 종료
                if (this.silenceStart !== null && currentTime - this.silenceStart > this.stopDuration) {
                    console.log('🛑 10초 무음 감지 → 녹음 종료');
                    this.port.postMessage({ type: "stop" }); // 녹음 종료 신호 전송
                    this.silenceStart = null; // 녹음 종료 후 silenceStart 리셋 (연속 녹음 가능)
                }
            }
        }

        return true;
    }
}

registerProcessor("audio-processor", AudioProcessor);
