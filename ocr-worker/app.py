"""FinLedger VN OCR Worker v4.0.0

Hybrid Vietnamese receipt OCR pipeline:
- Text line detection: RapidOCR (DBNet ONNX, detection-only)
- Text line recognition: VieSVTR v7 ONNX (MLCV lab, UIT — https://huggingface.co/ngocthua830/viesvtr, CC BY-NC 4.0)
"""
import logging
import math
import os
import statistics
import urllib.request
import cv2
import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, File, UploadFile, HTTPException
from rapidocr import RapidOCR

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-worker")

app = FastAPI(title="FinLedger VN OCR Worker", version="4.0.0")

# Exact 229-character VieSVTR / VietOCR vocabulary (1-based index; index 0 is CTC blank)
VIETOCR_VOCAB = (
    "aAàÀảẢãÃáÁạẠăĂằẰẳẲẵẴắẮặẶâÂầẦẩẨẫẪấẤậẬbBcCdDđĐeEèÈẻẺẽẼéÉẹẸêÊềỀểỂễỄếẾệỆ"
    "fFgGhHiIìÌỉỈĩĨíÍịỊjJkKlLmMnNoOòÒỏỎõÕóÓọỌôÔồỒổỔỗỖốỐộỘơƠờỜởỞỡỠớỚợỢpPqQrR"
    "sStTuUùÙủỦũŨúÚụỤưƯừỪửỬữỮứỨựỰvVwWxXyYỳỲỷỶỹỸýÝỵỴzZ0123456789"
    "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ "
)
IDX_TO_CHAR = {i + 1: ch for i, ch in enumerate(VIETOCR_VOCAB)}

VIESVTR_MODEL_URL = "https://huggingface.co/ngocthua830/viesvtr/resolve/main/weights/viesvtr_v7.onnx"
VIESVTR_MODEL_PATH = os.environ.get("VIESVTR_MODEL_PATH", "/app/models/viesvtr_v7.onnx")

VIESVTR_HEIGHT = 64
VIESVTR_W_STRIDE = 4
VIESVTR_W_MIN = 32
VIESVTR_W_MAX = 1600

det_engine = None
rec_engine = None


class VieSvtrRecognizer:
    """Lightweight ONNX Runtime wrapper for VieSVTR v7 Vietnamese + English line recognition."""

    def __init__(self, model_path: str):
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 2
        opts.inter_op_num_threads = 1
        self.sess = ort.InferenceSession(
            model_path,
            sess_options=opts,
            providers=["CPUExecutionProvider"],
        )
        self.input_name = self.sess.get_inputs()[0].name

    @staticmethod
    def _target_width(w: int, h: int) -> int:
        nw = int(math.ceil(VIESVTR_HEIGHT * float(w) / float(h)))
        nw = int(math.ceil(nw / VIESVTR_W_STRIDE) * VIESVTR_W_STRIDE)
        return max(VIESVTR_W_MIN, min(nw, VIESVTR_W_MAX))

    def _preprocess(self, crop_rgb: np.ndarray) -> np.ndarray:
        h, w = crop_rgb.shape[:2]
        tw = self._target_width(w, h)
        interp = cv2.INTER_AREA if h > VIESVTR_HEIGHT else cv2.INTER_LINEAR
        resized = cv2.resize(crop_rgb, (tw, VIESVTR_HEIGHT), interpolation=interp)
        x = resized.astype(np.float32).transpose(2, 0, 1)[None] / 255.0
        return (x - 0.5) / 0.5

    @staticmethod
    def _decode(logits: np.ndarray) -> tuple[str, float]:
        shifted = logits - logits.max(axis=-1, keepdims=True)
        exp_logits = np.exp(shifted)
        probs = exp_logits / exp_logits.sum(axis=-1, keepdims=True)
        ids = probs.argmax(axis=-1)
        scores = probs.max(axis=-1)

        out_chars: list[str] = []
        prev = -1
        for idx in ids.tolist():
            if idx != prev and idx != 0:
                ch = IDX_TO_CHAR.get(idx)
                if ch is not None:
                    out_chars.append(ch)
            prev = idx

        keep = ids != 0
        conf = float(scores[keep].mean()) if keep.any() else 0.0
        return "".join(out_chars).strip(), conf

    def __call__(self, crop_rgb: np.ndarray) -> tuple[str, float]:
        inp = self._preprocess(crop_rgb)
        logits = self.sess.run(None, {self.input_name: inp})[0][0]
        return self._decode(logits)


def get_det_engine():
    global det_engine
    if det_engine is not None:
        return det_engine

    logger.info("Initializing RapidOCR DBNet detector (detection-only)...")
    det_engine = RapidOCR(params={
        "Global.use_det": True,
        "Global.use_cls": False,
        "Global.use_rec": False,
    })
    return det_engine


def get_rec_engine() -> VieSvtrRecognizer:
    global rec_engine
    if rec_engine is not None:
        return rec_engine

    if not os.path.exists(VIESVTR_MODEL_PATH):
        os.makedirs(os.path.dirname(VIESVTR_MODEL_PATH), exist_ok=True)
        logger.info("Downloading VieSVTR v7 ONNX model to %s...", VIESVTR_MODEL_PATH)
        urllib.request.urlretrieve(VIESVTR_MODEL_URL, VIESVTR_MODEL_PATH)

    logger.info("Initializing VieSVTR v7 ONNX recognizer from %s...", VIESVTR_MODEL_PATH)
    rec_engine = VieSvtrRecognizer(VIESVTR_MODEL_PATH)
    return rec_engine


@app.on_event("startup")
def startup_event():
    get_det_engine()
    get_rec_engine()


@app.get("/health")
def health():
    return {"status": "UP", "service": "ocr-worker", "engine": "rapidocr-det+viesvtr-v7-vi"}


def _estimate_tilt_angle(boxes) -> float:
    angles: list[float] = []
    for poly in boxes:
        if poly is None or len(poly) < 4:
            continue
        tl, tr, _, bl = _order_quad(poly)
        width = float(np.linalg.norm(tr - tl))
        height = float(np.linalg.norm(bl - tl))
        if height <= 0 or width <= 2.2 * height:
            continue
        angle = math.atan2(float(tr[1] - tl[1]), float(tr[0] - tl[0]))
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


def _order_quad(poly) -> np.ndarray:
    """Orders 4 polygon vertices deterministically as [tl, tr, br, bl]."""
    pts = np.asarray(poly, dtype=np.float32).reshape(4, 2)
    by_y = pts[np.argsort(pts[:, 1])]
    top_two = by_y[:2][np.argsort(by_y[:2, 0])]
    bot_two = by_y[2:][np.argsort(by_y[2:, 0])]
    tl, tr = top_two[0], top_two[1]
    bl, br = bot_two[0], bot_two[1]
    return np.array([tl, tr, br, bl], dtype=np.float32)


def _crop_text_line(img_bgr: np.ndarray, poly) -> np.ndarray | None:
    """Perspective-crops and pads a detected quad polygon into an RGB text-line image."""
    if poly is None or len(poly) < 4:
        return None

    img_h, img_w = img_bgr.shape[:2]
    tl, tr, br, bl = _order_quad(poly)

    w_top = float(np.linalg.norm(tr - tl))
    w_bot = float(np.linalg.norm(br - bl))
    h_left = float(np.linalg.norm(bl - tl))
    h_right = float(np.linalg.norm(br - tr))

    w_box = max(w_top, w_bot)
    h_box = max(h_left, h_right)
    if w_box < 2.0 or h_box < 2.0:
        return None

    u_w = (tr - tl) / max(w_top, 1e-6)
    u_h = (bl - tl) / max(h_left, 1e-6)

    # Generous vertical padding preserves stacked Vietnamese diacritics (ế, ậ, ự, ở, ặ)
    pad_x = max(4.0, 0.10 * h_box)
    pad_y = max(3.0, 0.14 * h_box)

    src_quad = np.array([
        tl - pad_x * u_w - pad_y * u_h,
        tr + pad_x * u_w - pad_y * u_h,
        br + pad_x * u_w + pad_y * u_h,
        bl - pad_x * u_w + pad_y * u_h,
    ], dtype=np.float32)

    src_quad[:, 0] = np.clip(src_quad[:, 0], 0.0, float(img_w - 1))
    src_quad[:, 1] = np.clip(src_quad[:, 1], 0.0, float(img_h - 1))

    dst_w = max(8, int(round(w_box + 2.0 * pad_x)))
    dst_h = max(8, int(round(h_box + 2.0 * pad_y)))
    dst_quad = np.array([
        [0.0, 0.0],
        [float(dst_w - 1), 0.0],
        [float(dst_w - 1), float(dst_h - 1)],
        [0.0, float(dst_h - 1)],
    ], dtype=np.float32)

    matrix = cv2.getPerspectiveTransform(src_quad, dst_quad)
    warped_bgr = cv2.warpPerspective(
        img_bgr,
        matrix,
        (dst_w, dst_h),
        flags=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_REPLICATE,
    )
    return cv2.cvtColor(warped_bgr, cv2.COLOR_BGR2RGB)


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

        detector = get_det_engine()
        recognizer = get_rec_engine()

        det_result = detector(img, use_det=True, use_cls=False, use_rec=False)
        raw_boxes = getattr(det_result, "boxes", None)

        if raw_boxes is None or len(raw_boxes) == 0:
            logger.info("Processed image '%s': no text boxes detected", file.filename)
            return []

        tilt = _estimate_tilt_angle(raw_boxes)
        deskew_angle = -tilt
        cx = img.shape[1] / 2.0
        cy = img.shape[0] / 2.0

        output: list[dict] = []
        for poly in raw_boxes:
            crop_rgb = _crop_text_line(img, poly)
            if crop_rgb is None:
                continue

            text_str, conf = recognizer(crop_rgb)
            if not text_str or conf < 0.30:
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
