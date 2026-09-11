"""JJX 固定版式历史作业规范识别服务（dev-20260911-004）。"""
from __future__ import annotations

import base64
import os
import re
from functools import lru_cache
from pathlib import Path

os.environ.setdefault("PADDLE_PDX_CACHE_HOME", str(Path(__file__).resolve().parent / ".model-cache"))

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from paddleocr import PaddleOCR

app = FastAPI(title="JJX Local Archive OCR", version="1.0.0")


@lru_cache(maxsize=1)
def engine() -> PaddleOCR:
    model_root = Path(os.environ["PADDLE_PDX_CACHE_HOME"]) / "official_models"
    return PaddleOCR(text_detection_model_name="PP-OCRv5_mobile_det",
                     text_detection_model_dir=str(model_root / "PP-OCRv5_mobile_det"),
                     text_recognition_model_name="PP-OCRv5_mobile_rec",
                     text_recognition_model_dir=str(model_root / "PP-OCRv5_mobile_rec"),
                     use_doc_orientation_classify=False,
                     use_doc_unwarping=False, use_textline_orientation=False)


def crop(image: np.ndarray, x1: float, y1: float, x2: float, y2: float) -> np.ndarray:
    height, width = image.shape[:2]
    return image[int(height*y1):int(height*y2), int(width*x1):int(width*x2)]


def recognize_lines(image: np.ndarray) -> list[tuple[float, float, str]]:
    result = engine().predict(image)
    lines: list[tuple[float, float, str]] = []
    height, width = image.shape[:2]
    for item in result:
        data = getattr(item, "json", None)
        if callable(data):
            data = data()
        if isinstance(data, dict):
            data = data.get("res", data)
            texts = data.get("rec_texts", [])
            polygons = data.get("rec_polys", data.get("dt_polys", []))
            for text, polygon in zip(texts, polygons):
                if not str(text).strip():
                    continue
                points = np.asarray(polygon)
                lines.append((float(points[:, 0].mean())/width, float(points[:, 1].mean())/height, str(text).strip()))
    return lines


def region_text(lines: list[tuple[float, float, str]], x1: float, y1: float, x2: float, y2: float) -> str:
    selected = [(y, x, text) for x, y, text in lines if x1 <= x <= x2 and y1 <= y <= y2]
    return " ".join(text for _, _, text in sorted(selected)).strip()


def normalized_icon(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if len(image.shape) == 3 else image
    binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
    horizontal = cv2.morphologyEx(binary, cv2.MORPH_OPEN,
                                  cv2.getStructuringElement(cv2.MORPH_RECT, (max(8, image.shape[1]//2), 1)))
    vertical = cv2.morphologyEx(binary, cv2.MORPH_OPEN,
                                cv2.getStructuringElement(cv2.MORPH_RECT, (1, max(8, image.shape[0]//2))))
    cleaned = cv2.bitwise_and(binary, cv2.bitwise_not(cv2.bitwise_or(horizontal, vertical)))
    points = cv2.findNonZero(cleaned)
    canvas = np.zeros((64, 64), dtype=np.uint8)
    if points is None:
        return canvas
    x, y, w, h = cv2.boundingRect(points)
    symbol = cleaned[y:y+h, x:x+w]
    scale = min(52/max(w, 1), 52/max(h, 1))
    resized = cv2.resize(symbol, (max(1, int(w*scale)), max(1, int(h*scale))), interpolation=cv2.INTER_AREA)
    yy = (64-resized.shape[0])//2
    xx = (64-resized.shape[1])//2
    canvas[yy:yy+resized.shape[0], xx:xx+resized.shape[1]] = resized
    return canvas


def perceptual_hash(image: np.ndarray) -> str:
    small = cv2.resize(image, (8, 8), interpolation=cv2.INTER_AREA)
    mean = float(small.mean())
    value = 0
    for pixel in small.flatten():
        value = (value << 1) | int(pixel >= mean)
    return f"{value:016x}"


def png64(image: np.ndarray) -> str:
    ok, encoded = cv2.imencode(".png", image)
    if not ok:
        return ""
    return base64.b64encode(encoded.tobytes()).decode("ascii")


def parse_quantity(specification: str) -> tuple[float, str]:
    match = re.search(r"(?:=|/)(\d+(?:\.\d+)?)\s*(PCS|颗|个|PIN)?", specification, re.I)
    if not match:
        return 1.0, "PCS"
    return float(match.group(1)), (match.group(2) or "PCS").upper()


def workflow_rows(image: np.ndarray, lines: list[tuple[float, float, str]], workflow_type: str, x1: float, x2: float,
                  y1: float, y2: float, rows: int = 14) -> dict:
    result = {"workflowType": workflow_type, "steps": []}
    for index in range(rows):
        top = y1 + (y2-y1)*index/rows
        bottom = y1 + (y2-y1)*(index+1)/rows
        row = crop(image, x1, top, x2, bottom)
        if row.size == 0:
            continue
        icon_width = max(20, int(row.shape[1]*0.28))
        icon = row[:, :icon_width]
        normal = normalized_icon(icon)
        raw_text = region_text(lines, x1+(x2-x1)*0.28, top, x2, bottom)
        if cv2.countNonZero(normal) < 30 and not raw_text:
            continue
        result["steps"].append({
            "stepNo": index + 1,
            "rawText": raw_text,
            "processId": None,
            "processName": raw_text or None,
            "perceptualHash": perceptual_hash(normal),
            "iconOriginalBase64": png64(icon),
            "iconNormalizedBase64": png64(normal),
        })
    return result


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "engine": "PaddleOCR", "mode": "local"}


@app.post("/recognize")
async def recognize(file: UploadFile = File(...)) -> dict:
    raw = await file.read()
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="无法读取图片")

    lines = recognize_lines(image)
    footer = region_text(lines, 0.0, 0.955, 1.0, 1.0)
    name_match = re.search(r"名称\s*[:：]\s*(.+?)(?=编号\s*[:：]|发行日期|$)", footer)
    code_match = re.search(r"编号\s*[:：]\s*([^\s]+)", footer)
    product_name = name_match.group(1).strip() if name_match else ""
    product_code = code_match.group(1).strip() if code_match else ""

    materials = []
    material_top, material_bottom, count = 0.087, 0.381, 14
    remarks = []
    for index in range(count):
        top = material_top + (material_bottom-material_top)*index/count
        bottom = material_top + (material_bottom-material_top)*(index+1)/count
        name = region_text(lines, 0.115, top, 0.34, bottom)
        specification = region_text(lines, 0.34, top, 0.60, bottom)
        tooling = region_text(lines, 0.60, top, 0.73, bottom)
        emboss = region_text(lines, 0.73, top, 0.995, bottom)
        if not name:
            continue
        quantity, unit = parse_quantity(specification)
        materials.append({"itemNo": index+1, "materialName": name,
                          "specification": specification, "quantity": quantity, "unit": unit})
        if tooling or emboss:
            remarks.append(f"{index+1}. {name}；刀模位置：{tooling or '-'}；凹凸条件：{emboss or '-'}")

    return {
        "template": "JJX_PRODUCT_OPERATION_STANDARD_V1",
        "productName": product_name,
        "productCode": product_code,
        "productRemark": "【历史作业规范】\n" + "\n".join(remarks),
        "materials": materials,
        "workflows": [
            workflow_rows(image, lines, "PANEL", 0.064, 0.338, 0.411, 0.779),
            workflow_rows(image, lines, "UP_LINE", 0.385, 0.670, 0.626, 0.779, 6),
            workflow_rows(image, lines, "DOWN_LINE", 0.716, 0.930, 0.411, 0.779),
        ],
        "rawFooter": footer,
    }
