FROM python:3.10-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    OMP_NUM_THREADS=2

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libglib2.0-0 \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download VieSVTR v7 ONNX model (MLCV lab UIT, CC BY-NC 4.0) before copying app.py
RUN mkdir -p /app/models && python -c "\
import os, urllib.request; \
url = 'https://huggingface.co/ngocthua830/viesvtr/resolve/main/weights/viesvtr_v7.onnx'; \
dst = '/app/models/viesvtr_v7.onnx'; \
urllib.request.urlretrieve(url, dst); \
size = os.path.getsize(dst); \
assert size > 20_000_000, f'Unexpected model size: {size}'; \
print(f'Downloaded {dst}: {size} bytes')"

COPY app.py .

# Pre-warm RapidOCR detector + VieSVTR recognizer during Docker build
RUN python -c "from app import get_det_engine, get_rec_engine; get_det_engine(); get_rec_engine()"

EXPOSE 8081

CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8081} --workers 1"]
