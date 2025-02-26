import os
import json
import wave
import struct
import io
import traceback
import speech_recognition as sr
from flask import Flask, request, jsonify

app = Flask(__name__)

def pcm_to_wav(pcm_data, sample_rate=48000): # PCM 데이터를 WAV 파일로 변환 (샘플 레이트 48000Hz)
    if not pcm_data or len(pcm_data) == 0:
        raise ValueError('[ERROR] PCM 데이터가 없습니다.')

    print(f'PCM 데이터 크기: {len(pcm_data)} bytes')

    wav_file = io.BytesIO()

    try:
        with wave.open(wav_file, 'wb') as wf:
            wf.setnchannels(1)  # 모노 채널
            wf.setsampwidth(2)  # 16비트 (2바이트)
            wf.setframerate(sample_rate)

            try:
                pcm_int16 = struct.pack('<' + ('h' * (len(pcm_data) // 2)), *struct.unpack('<' + ('h' * (len(pcm_data) // 2)), pcm_data))
            except struct.error as e:
                raise ValueError(f'[ERROR] PCM 데이터 변환 실패: {e}')

            wf.writeframes(pcm_int16)

        wav_file.seek(0)
        print('PCM → WAV 변환 성공')
        return wav_file
    except Exception as e:
        raise ValueError(f'[ERROR] WAV 변환 중 오류 발생: {e}')

@app.route('/stt', methods=['POST'])
def speech_to_text():
    try:
        pcm_data = request.data

        # 1. 빈 데이터 체크
        if not pcm_data or len(pcm_data) == 0:
            print('[ERROR] 요청된 오디오 데이터가 없습니다.')
            return jsonify({'error': '요청된 오디오 데이터가 없습니다.'}), 400

        print(f'받은 PCM 데이터 크기: {len(pcm_data)} bytes')

        # 2. PCM → WAV 변환
        try:
            wav_file = pcm_to_wav(pcm_data)
        except ValueError as e:
            print(str(e))
            return jsonify({'error': str(e)}), 400

        #save_debug_wav(wav_file)

        # 3. WAV 길이 확인
        duration = debug_wav_info(wav_file)
        if duration < 0.3:
            print(f'[ERROR] 오디오가 너무 짧음 ({duration:.2f}s). 0.3초 이상 필요!')
            return jsonify({'error': f'오디오가 너무 짧음 ({duration:.2f}s)'}), 400

        # 4. Google STT 실행 전 WAV 크기 확인
        wav_size = wav_file.getbuffer().nbytes
        print(f'Google STT 요청 전 WAV 파일 크기: {wav_size} bytes')

        recognizer = sr.Recognizer()
        wav_file.seek(0)

        with sr.AudioFile(wav_file) as source:
            audio = recognizer.record(source)

        try:
            text = recognizer.recognize_google(audio, language='ko-KR')
            print(f'변환된 텍스트: {text}')
        except sr.UnknownValueError:
            print('[ERROR] Google STT가 음성을 인식하지 못했습니다.')
            return jsonify({'error': 'Google STT가 음성을 인식하지 못함'}), 400
        except sr.RequestError as e:
            print(f'[ERROR] Google STT 서버 오류: {e}')
            return jsonify({'error': 'Google STT 서버 오류', 'details': str(e)}), 500

        response = json.dumps({'text': text}, ensure_ascii=False)
        return response, 200, {'Content-Type': 'application/json; charset=utf-8'}

    except Exception as e:
        print('[ERROR] 서버 내부 오류:', str(e))
        traceback.print_exc()
        return jsonify({'error': '서버 내부 오류', 'details': str(e)}), 500

'''
def save_debug_wav(wav_file, filename='debug_audio.wav'): # 디버그용 WAV 파일 저장
    debug_dir = 'C:/temp'
    os.makedirs(debug_dir, exist_ok=True)
    filepath = os.path.join(debug_dir, filename)

    try:
        wav_file.seek(0)
        with open(filepath, 'wb') as f:
            f.write(wav_file.getvalue())
        print(f'디버그 WAV 파일 저장됨: {filepath}')
    except PermissionError:
        print(f'[ERROR] WAV 저장 실패: 권한 문제 (PermissionError). 관리자 권한으로 실행하거나 다른 경로 사용하세요.')
    except Exception as e:
        print(f'[ERROR] WAV 저장 실패: {e}')
'''

def debug_wav_info(wav_file): # WAV 파일 정보 디버깅 출력
    wav_file.seek(0)
    with wave.open(wav_file, 'rb') as wf:
        channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        frames = wf.getnframes()
        duration = frames / float(framerate)

        print(f'WAV 파일 정보: 채널={channels}, 샘플링 레이트={framerate}Hz, 비트뎁스={sampwidth*8}bit, 길이={duration:.2f}s')
        return duration

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)