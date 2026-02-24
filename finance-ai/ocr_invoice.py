from PIL import Image
import re
import os


def parse_invoice(image_path: str) -> dict:
    result = {'raw_text': '', 'total': None, 'vendor': None}

    if not os.path.exists(image_path):
        return {'error': f'File not found: {image_path}'}

    try:
        import pytesseract
        img = Image.open(image_path)
        text = pytesseract.image_to_string(img)
        result['raw_text'] = text

        total_match = re.search(
            r'(?i)total\s*[:\$]?\s*([\d,]+\.?\d{0,2})',
            text
        )
        if total_match:
            result['total'] = float(total_match.group(1).replace(',', ''))

        lines = [line.strip() for line in text.splitlines() if line.strip()]
        if lines:
            result['vendor'] = lines[0]

    except ImportError:
        result['error'] = 'pytesseract not installed'
    except Exception as e:
        result['error'] = str(e)

    return result


if __name__ == '__main__':
    print("OCR Invoice Parser ready.")
    print("Usage: parse_invoice('path/to/invoice.png')")