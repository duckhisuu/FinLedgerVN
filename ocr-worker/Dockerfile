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

COPY app.py .

# Pre-warm ONNX models into the image during build
RUN python -c "from app import get_ocr_engine; get_ocr_engine()"

EXPOSE 8081

CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-8081} --workers 1"]
