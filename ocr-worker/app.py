import logging
import cv2
import numpy as np
import pytesseract
from pytesseract import Output
from fastapi import FastAPI, File, UploadFile, HTTPException

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-worker")

app = FastAPI(title="FinLedger VN OCR Worker", version="2.0.0")


def _extract_boxes_from_image(processed_img: np.ndarray, scale: float, psm: int) -> tuple[list[dict], float]:
    config = f"--oem 3 --psm {psm}"
    data = pytesseract.image_to_data(
        processed_img,
        lang="vie+eng",
        config=config,
        output_type=Output.DICT,
    )

    boxes: list[dict] = []
    conf_sum = 0.0
    n_items = len(data.get("text", []))

    for i in range(n_items):
        raw_text = (data["text"][i] or "").strip()
        if not raw_text:
            continue

        try:
            conf_raw = float(data["conf"][i])
        except (TypeError, ValueError):
            continue

        if conf_raw < 20.0:
            continue

        left = float(data["left"][i]) / scale
        top = float(data["top"][i]) / scale
        width = float(data["width"][i]) / scale
        height = float(data["height"][i]) / scale

        if width <= 0 or height <= 0:
            continue

        conf_norm = max(0.0, min(1.0, conf_raw / 100.0))
        conf_sum += conf_norm
        boxes.append({
            "text": raw_text,
            "xMin": left,
            "yMin": top,
            "xMax": left + width,
            "yMax": top + height,
            "confidence": conf_norm,
        })

    return boxes, conf_sum


@app.get("/health")
def health():
    return {"status": "UP", "service": "ocr-worker", "engine": "tesseract-vie"}


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

        if len(img.shape) == 2:
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        elif len(img.shape) == 3 and img.shape[2] == 4:
            img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)

        h, w = img.shape[:2]
        max_dim = max(h, w)
        scale = 1.0
        if max_dim < 1000:
            scale = 1400.0 / max_dim
            img = cv2.resize(
                img,
                (max(1, int(w * scale)), max(1, int(h * scale))),
                interpolation=cv2.INTER_CUBIC,
            )
        elif max_dim > 2200:
            scale = 2000.0 / max_dim
            img = cv2.resize(
                img,
                (max(1, int(w * scale)), max(1, int(h * scale))),
                interpolation=cv2.INTER_AREA,
            )

        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        # Pass 1: CLAHE local contrast enhancement (--psm 6: uniform block of text)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        gray_clahe = clahe.apply(gray)
        boxes, score = _extract_boxes_from_image(gray_clahe, scale, psm=6)

        # Pass 2 fallback if Pass 1 extracted too few tokens
        if len(boxes) < 5:
            blurred = cv2.GaussianBlur(gray, (3, 3), 0)
            _, otsu = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            alt_boxes, alt_score = _extract_boxes_from_image(otsu, scale, psm=4)
            if len(alt_boxes) > len(boxes) or (len(alt_boxes) == len(boxes) and alt_score > score):
                boxes = alt_boxes

        logger.info("Processed image '%s': detected %d text boxes", file.filename, len(boxes))
        return boxes

    except HTTPException:
        raise
    except Exception as e:
        logger.error("Error processing OCR: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
