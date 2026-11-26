import logging
import math
import statistics
import cv2
import numpy as np
from fastapi import FastAPI, File, UploadFile, HTTPException
from rapidocr import RapidOCR, LangRec, OCRVersion, ModelType

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-worker")

app = FastAPI(title="FinLedger VN OCR Worker", version="3.0.0")

ocr_engine = None


def get_ocr_engine():
    global ocr_engine
    if ocr_engine is not None:
        return ocr_engine

    configs = []
    ppocrv5 = getattr(OCRVersion, "PPOCRV5", None)
    ppocrv4 = getattr(OCRVersion, "PPOCRV4", None)
    if ppocrv5 is not None:
        configs.append({
            "Rec.lang_type": LangRec.LATIN,
            "Rec.ocr_version": ppocrv5,
            "Rec.model_type": ModelType.MOBILE,
        })
    if ppocrv4 is not None:
        configs.append({
            "Rec.lang_type": LangRec.LATIN,
            "Rec.ocr_version": ppocrv4,
            "Rec.model_type": ModelType.MOBILE,
        })
    configs.append({
        "Rec.lang_type": LangRec.LATIN,
    })

    for idx, params in enumerate(configs):
        try:
            logger.info("Initializing RapidOCR engine (config #%d)...", idx + 1)
            ocr_engine = RapidOCR(params=params)
            return ocr_engine
        except Exception as e:
            logger.warning("RapidOCR config #%d failed: %s", idx + 1, e)

    logger.info("Initializing RapidOCR default fallback...")
    ocr_engine = RapidOCR()
    return ocr_engine


@app.on_event("startup")
def startup_event():
    get_ocr_engine()


@app.get("/health")
def health():
    return {"status": "UP", "service": "ocr-worker", "engine": "rapidocr-latin"}


def _estimate_tilt_angle(boxes) -> float:
    angles: list[float] = []
    for poly in boxes:
        if poly is None or len(poly) < 4:
            continue
        p0, p1, _, p3 = poly[0], poly[1], poly[2], poly[3]
        width = math.hypot(float(p1[0]) - float(p0[0]), float(p1[1]) - float(p0[1]))
        height = math.hypot(float(p3[0]) - float(p0[0]), float(p3[1]) - float(p0[1]))
        if height <= 0 or width <= 2.2 * height:
            continue
        angle = math.atan2(float(p1[1]) - float(p0[1]), float(p1[0]) - float(p0[0]))
        if abs(angle) <= 0.35:
            angles.append(angle)

    if len(angles) >= 3:
        return statistics.median(angles)
    return 0.0


def _rotate_point(x: float, y: float, cx: float, cy: float, angle: float) -> tuple[float, float]:
    if angle == 0.0:
        return x, y
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)
    dx = x - cx
    dy = y - cy
    return cx + dx * cos_a - dy * sin_a, cy + dx * sin_a + dy * cos_a


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
        if max_dim > 1800:
            scale = 1800.0 / max_dim
            img = cv2.resize(
                img,
                (max(1, int(w * scale)), max(1, int(h * scale))),
                interpolation=cv2.INTER_AREA,
            )

        engine = get_ocr_engine()
        result = engine(img)

        raw_boxes = getattr(result, "boxes", None)
        raw_txts = getattr(result, "txts", None)
        raw_scores = getattr(result, "scores", None)

        if raw_boxes is None or raw_txts is None or len(raw_txts) == 0:
            logger.info("Processed image '%s': no text boxes detected", file.filename)
            return []

        tilt = _estimate_tilt_angle(raw_boxes)
        deskew_angle = -tilt
        cx = img.shape[1] / 2.0
        cy = img.shape[0] / 2.0

        output: list[dict] = []
        for i, txt in enumerate(raw_txts):
            text_str = str(txt or "").strip()
            if not text_str:
                continue

            conf = float(raw_scores[i]) if raw_scores is not None and i < len(raw_scores) else 1.0
            if conf < 0.30:
                continue

            poly = raw_boxes[i]
            if poly is None or len(poly) == 0:
                continue

            xs: list[float] = []
            ys: list[float] = []
            for pt in poly:
                rx, ry = _rotate_point(float(pt[0]), float(pt[1]), cx, cy, deskew_angle)
                xs.append(rx / scale)
                ys.append(ry / scale)

            output.append({
                "text": text_str,
                "xMin": min(xs),
                "yMin": min(ys),
                "xMax": max(xs),
                "yMax": max(ys),
                "confidence": max(0.0, min(1.0, conf)),
            })

        logger.info("Processed image '%s': detected %d text boxes (tilt=%.4f rad)", file.filename, len(output), tilt)
        return output

    except HTTPException:
        raise
    except Exception as e:
        logger.error("Error processing OCR: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
