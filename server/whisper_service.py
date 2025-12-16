#!/usr/bin/env python3
"""
Whisper Speech-to-Text Service
Принимает аудио файл и возвращает распознанный текст
"""
import sys
import json
import os
from pathlib import Path

def transcribe_audio(audio_file_path: str, language: str = "ru") -> dict:
    """
    Распознает речь из аудио файла

    Args:
        audio_file_path: Путь к аудио файлу
        language: Язык распознавания (по умолчанию русский)

    Returns:
        dict: {"text": "распознанный текст", "language": "ru"}
    """
    try:
        from faster_whisper import WhisperModel

        # Используем tiny модель для быстрой работы (можно изменить на base/small/medium)
        # tiny - ~75MB, самая быстрая
        # base - ~145MB
        # small - ~466MB
        # medium - ~1.5GB
        model_size = os.environ.get("WHISPER_MODEL", "tiny")

        # Загружаем модель (при первом запуске скачается автоматически)
        model = WhisperModel(model_size, device="cpu", compute_type="int8")

        # Распознаем речь
        segments, info = model.transcribe(
            audio_file_path,
            language=language,
            beam_size=5,
            vad_filter=True  # Voice Activity Detection - убирает тишину
        )

        # Собираем текст из сегментов
        text = " ".join([segment.text for segment in segments]).strip()

        return {
            "text": text,
            "language": info.language,
            "language_probability": info.language_probability
        }

    except ImportError:
        return {
            "error": "faster-whisper not installed. Run: pip3 install faster-whisper"
        }
    except Exception as e:
        return {
            "error": str(e)
        }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: whisper_service.py <audio_file>"}))
        sys.exit(1)

    audio_file = sys.argv[1]
    language = sys.argv[2] if len(sys.argv) > 2 else "ru"

    if not os.path.exists(audio_file):
        print(json.dumps({"error": f"File not found: {audio_file}"}))
        sys.exit(1)

    result = transcribe_audio(audio_file, language)
    print(json.dumps(result, ensure_ascii=False))
