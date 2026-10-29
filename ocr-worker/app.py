import os

# Disable MKLDNN/oneDNN PIR instruction bug in PaddlePaddle 3.x
os.environ["PADDLE_PDX_ENABLE_MKLDNN_BYDEFAULT"] = "0"
os.environ["FLAGS_use_onednn"] = "0"
os.environ["FLAGS_enable_pir_api"] = "0"

import io
import logging
import cv2
import numpy as np
from fastapi import FastAPI, File, UploadFile, HTTPException
from paddleocr import PaddleOCR

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-worker")

app = FastAPI(title="FinLedger VN OCR Worker", version="1.0.0")

# Lazy-loaded PaddleOCR engine
ocr_engine = None

def get_ocr_engine():
    global ocr_engine
    if ocr_engine is None:
        try:
            logger.info("Initializing PaddleOCR with textline orientation...")
            ocr_engine = PaddleOCR(
                lang="vi",
                use_textline_orientation=True,
                use_doc_unwarping=False,
                use_doc_orientation_classify=False
            )
        except Exception as e:
            logger.warning("PaddleOCR fallback initialization: %s", e)
            try:
                ocr_engine = PaddleOCR(lang="vi", use_doc_unwarping=False)
            except Exception:
                ocr_engine = PaddleOCR(lang="vi")
    return ocr_engine

@app.on_event("startup")
def startup_event():
    logger.info("Pre-warming PaddleOCR engine on startup...")
    get_ocr_engine()

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

        # Ensure image has 3 color channels (BGR) for PaddleOCR deep learning models
        if len(img.shape) == 2:
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        elif len(img.shape) == 3 and img.shape[2] == 4:
            img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)

        # Smart proportional downscale for large camera photos (> 1600px)
        # Keeps text crystal clear while accelerating CPU inference by 5x-8x
        h, w = img.shape[:2]
        max_dim = max(h, w)
        scale = 1.0
        if max_dim > 1600:
            scale = 1600.0 / max_dim
            new_w = int(w * scale)
            new_h = int(h * scale)
            img = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)

        # Run PaddleOCR
        engine = get_ocr_engine()
        results = engine.ocr(img)

        boxes = []
        if results and len(results) > 0:
            first_res = results[0]
            # Format 1: PaddleOCR 3.x / PaddleX (OCRResult dict-like)
            if isinstance(first_res, dict):
                texts = first_res.get("rec_texts", [])
                scores = first_res.get("rec_scores", [])
                polys = first_res.get("rec_polys", first_res.get("dt_polys", []))
                for i in range(len(texts)):
                    text = texts[i]
                    confidence = float(scores[i]) if i < len(scores) else 1.0
                    poly = polys[i] if i < len(polys) else []

                    if len(poly) > 0:
                        x_coords = [float(point[0]) / scale for point in poly]
                        y_coords = [float(point[1]) / scale for point in poly]
                        boxes.append({
                            "text": str(text),
                            "xMin": float(min(x_coords)),
                            "yMin": float(min(y_coords)),
                            "xMax": float(max(x_coords)),
                            "yMax": float(max(y_coords)),
                            "confidence": confidence
                        })
            # Format 2: PaddleOCR 2.x (list of [polygon, (text, score)])
            elif isinstance(first_res, list):
                for line in first_res:
                    if not line or len(line) < 2:
                        continue
                    polygon = line[0]
                    text_info = line[1]
                    text = text_info[0] if isinstance(text_info, (list, tuple)) else str(text_info)
                    confidence = float(text_info[1]) if isinstance(text_info, (list, tuple)) and len(text_info) > 1 else 1.0

                    x_coords = [float(point[0]) / scale for point in polygon]
                    y_coords = [float(point[1]) / scale for point in polygon]

                    boxes.append({
                        "text": str(text),
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
