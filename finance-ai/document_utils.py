import io
import logging
import re

logger = logging.getLogger(__name__)

IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "webp", "bmp", "tiff", "tif", "heic", "heif"}

_HEIF_ENABLED = False
try:
    import pillow_heif

    pillow_heif.register_heif_opener()
    _HEIF_ENABLED = True
except Exception:
    _HEIF_ENABLED = False


def detect_file_type(file_bytes: bytes, filename: str = "", mime_type: str = "") -> str:
    ext = ""
    if "." in filename:
        ext = filename.rsplit(".", 1)[-1].lower().strip()
    mime = (mime_type or "").lower().strip()
    header = file_bytes[:32]

    if ext in IMAGE_EXTENSIONS | {"csv", "pdf"}:
        return "jpg" if ext == "jpeg" else "tiff" if ext == "tif" else ext
    if mime in {"text/csv", "application/csv"} or _looks_like_csv(file_bytes):
        return "csv"
    if mime == "application/pdf" or file_bytes.startswith(b"%PDF"):
        return "pdf"
    if header.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if header.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if header[:4] == b"RIFF" and b"WEBP" in header[:16]:
        return "webp"
    if header.startswith((b"II*\x00", b"MM\x00*")):
        return "tiff"
    if _looks_like_heif(file_bytes):
        return "heic"
    if _looks_like_image(file_bytes):
        return "image"
    return ext or "unknown"


def open_image_bytes(file_bytes: bytes):
    from PIL import Image, ImageOps

    image = Image.open(io.BytesIO(file_bytes))
    image = ImageOps.exif_transpose(image)
    if image.mode not in {"RGB", "L"}:
        image = image.convert("RGB")
    return image


def preprocess_image_for_ocr(image) -> list:
    from PIL import ImageFilter, ImageOps

    base = ImageOps.exif_transpose(image)
    if base.mode != "L":
        base = base.convert("L")

    width, height = base.size
    if width and width < 1600:
        scale = 1600 / width
        base = base.resize((int(width * scale), int(height * scale)), base.Resampling.LANCZOS)

    variants = [ImageOps.autocontrast(base)]
    sharpened = variants[0].filter(ImageFilter.SHARPEN)
    variants.append(sharpened)
    threshold = sharpened.point(lambda px: 255 if px > 185 else 0)
    variants.append(threshold)
    return variants


def extract_text_from_image_bytes(file_bytes: bytes) -> dict:
    try:
        import pytesseract
    except ImportError:
        return {"text": "", "warnings": ["pytesseract is not installed."], "ocrConfidence": 0.0}

    try:
        image = open_image_bytes(file_bytes)
    except Exception as exc:
        return {"text": "", "warnings": [f"Could not open image: {exc}"], "ocrConfidence": 0.0}

    variants = preprocess_image_for_ocr(image)
    best_text = ""
    best_score = -1.0
    warnings = []

    for variant in variants:
        for psm in ("6", "11"):
            try:
                text = pytesseract.image_to_string(variant, config=f"--oem 3 --psm {psm}")
            except Exception as exc:
                warnings.append(f"OCR attempt failed: {exc}")
                continue
            score = _score_ocr_text(text)
            if score > best_score:
                best_score = score
                best_text = text

    confidence = min(0.94, max(0.15, best_score / 180.0))
    return {
        "text": best_text.strip(),
        "warnings": _dedupe_preserve(warnings),
        "ocrConfidence": round(confidence, 3),
    }


def pdf_to_images(file_bytes: bytes) -> dict:
    try:
        from pdf2image import convert_from_bytes
    except ImportError:
        return {
            "images": [],
            "warnings": ["pdf2image is not installed; scanned PDF OCR fallback is unavailable."],
        }

    try:
        images = convert_from_bytes(file_bytes, dpi=220)
        return {"images": images, "warnings": []}
    except Exception as exc:
        return {"images": [], "warnings": [f"PDF to image conversion failed: {exc}"]}


def extract_text_from_pdf_bytes(file_bytes: bytes) -> dict:
    warnings = []
    text_pages = []

    try:
        import pdfplumber
    except ImportError:
        return {
            "text": "",
            "warnings": ["pdfplumber is not installed; text PDF parsing is unavailable."],
            "usedOcrFallback": False,
            "ocrConfidence": 0.0,
        }

    try:
        with pdfplumber.open(io.BytesIO(file_bytes)) as pdf:
            for page in pdf.pages:
                extracted = page.extract_text() or ""
                if extracted.strip():
                    text_pages.append(extracted)
    except Exception as exc:
        warnings.append(f"PDF parse failed: {exc}")

    joined_text = "\n".join(text_pages).strip()
    if _text_looks_useful(joined_text):
        return {
            "text": joined_text,
            "warnings": warnings,
            "usedOcrFallback": False,
            "ocrConfidence": 0.0,
        }

    image_result = pdf_to_images(file_bytes)
    warnings.extend(image_result["warnings"])
    images = image_result["images"]
    if not images:
        return {
            "text": joined_text,
            "warnings": _dedupe_preserve(warnings),
            "usedOcrFallback": False,
            "ocrConfidence": 0.0,
        }

    page_texts = []
    confidences = []
    for image in images:
        buf = io.BytesIO()
        image.save(buf, format="PNG")
        ocr_result = extract_text_from_image_bytes(buf.getvalue())
        page_text = (ocr_result.get("text") or "").strip()
        if page_text:
            page_texts.append(page_text)
        confidences.append(float(ocr_result.get("ocrConfidence") or 0.0))
        warnings.extend(ocr_result.get("warnings") or [])

    return {
        "text": "\n".join(page_texts).strip(),
        "warnings": _dedupe_preserve(warnings),
        "usedOcrFallback": bool(page_texts),
        "ocrConfidence": round(sum(confidences) / len(confidences), 3) if confidences else 0.0,
    }


def best_warning(warnings) -> str | None:
    unique = _dedupe_preserve([warning for warning in warnings if warning])
    if not unique:
        return None
    return " ".join(unique[:2])


def heif_enabled() -> bool:
    return _HEIF_ENABLED


def _looks_like_csv(file_bytes: bytes) -> bool:
    sample = file_bytes[:2048]
    try:
        text = sample.decode("utf-8-sig")
    except UnicodeDecodeError:
        return False
    return ("," in text or ";" in text or "\t" in text) and "\n" in text


def _looks_like_heif(file_bytes: bytes) -> bool:
    if len(file_bytes) <= 12:
        return False
    return file_bytes[4:12] in {b"ftypheic", b"ftypheif"}


def _looks_like_image(file_bytes: bytes) -> bool:
    try:
        open_image_bytes(file_bytes)
        return True
    except Exception:
        return False


def _text_looks_useful(text: str) -> bool:
    if not text:
        return False
    date_hits = len(re.findall(r"\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}", text))
    amount_hits = len(re.findall(r"₹?\s*[\d,]+\.\d{2}", text))
    return len(text) > 80 and (date_hits >= 2 or amount_hits >= 3)


def _score_ocr_text(text: str) -> float:
    if not text:
        return 0.0
    compact = " ".join(text.split())
    date_hits = len(re.findall(r"\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}", compact))
    amount_hits = len(re.findall(r"₹?\s*[\d,]+(?:\.\d{2})", compact))
    keywords = len(re.findall(r"\b(?:debit|credit|payment|received|upi|balance|withdrawal|deposit)\b", compact, re.I))
    return min(220.0, len(compact) / 8.0 + date_hits * 14 + amount_hits * 10 + keywords * 8)


def _dedupe_preserve(items: list[str]) -> list[str]:
    seen = set()
    result = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result
