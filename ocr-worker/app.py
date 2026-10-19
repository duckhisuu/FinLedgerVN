import io
import logging
import cv2
import numpy as np
from fastapi import FastAPI, File, UploadFile, HTTPException
from paddleocr import PaddleOCR

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-worker")

app = FastAPI(title="FinLedger VN OCR Worker", version="1.0.0")

# Initialize PaddleOCR with CPU mode and lightweight models
ocr_engine = PaddleOCR(use_angle_cls=True, lang="vi", use_gpu=False, show_log=False)

@app.get("/health")
def health():
    return {"status": "UP", "service": "ocr-worker"}

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        if not contents:
            raise HTTPException(status_code=400, detail="Empty file uploaded")

        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            raise HTTPException(status_code=400, detail="Could not decode image")

        # Step 1: Preprocessing for faint thermal paper receipts
        # Convert to grayscale
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        # Gaussian blur to reduce high frequency noise
        blurred = cv2.GaussianBlur(gray, (3, 3), 0)
        # Adaptive thresholding for uneven thermal receipt contrast
        thresh = cv2.adaptiveThreshold(
            blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
        )

        # Step 2: Run PaddleOCR on preprocessed image
        results = ocr_engine.ocr(thresh, cls=True)

        boxes = []
        if results and results[0]:
            for line in results[0]:
                polygon = line[0]  # [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
                text_info = line[1]
                text = text_info[0]
                confidence = float(text_info[1])

                x_coords = [point[0] for point in polygon]
                y_coords = [point[1] for point in polygon]

                boxes.append({
                    "text": text,
                    "xMin": float(min(x_coords)),
                    "yMin": float(min(y_coords)),
                    "xMax": float(max(x_coords)),
                    "yMax": float(max(y_coords)),
                    "confidence": confidence
                })

        logger.info(f"Processed image '{file.filename}': detected {len(boxes)} text boxes")
        return boxes

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing OCR: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
