"""Import the publicly served reference fonts as Android-readable TTF resources.

Run with fonttools + brotli. No account, APK data, credentials or production
operations are involved. Typeface outlines and names are preserved.
"""
from pathlib import Path
from io import BytesIO
from urllib.request import urlopen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ASSETS = ROOT / "app/src/main/assets"
if not RES.is_dir():
    raise RuntimeError("Android resource parent is missing")
FONT_DIR = RES / "font"
FONT_DIR.mkdir(exist_ok=True)
LICENSE_DIR = ASSETS / "font_licenses"
LICENSE_DIR.mkdir(parents=True, exist_ok=True)

FONTS = {
    "schibsted_regular": "schibsted-grotesk-400-normal-DPhJBilQ.woff2",
    "schibsted_medium": "schibsted-grotesk-500-normal-rf9C4Thp.woff2",
    "schibsted_semibold": "schibsted-grotesk-600-normal-Czv9Obfv.woff2",
    "schibsted_bold": "schibsted-grotesk-700-normal-BkH0uJ1o.woff2",
    "schibsted_extrabold": "schibsted-grotesk-800-normal-CIaq-TR1.woff2",
    "geist_mono_regular": "geist-mono-400-normal-DTRLJnHl.woff2",
    "geist_mono_medium": "geist-mono-500-normal-YINYabwD.woff2",
    "geist_mono_semibold": "geist-mono-600-normal-V-KvD_pi.woff2",
}
for name, source in FONTS.items():
    target = FONT_DIR / (name + ".ttf")
    if target.exists():
        raise RuntimeError(f"Refusing to overwrite {target.name}")
    font = TTFont(BytesIO(urlopen("https://puntto.do/build/assets/" + source).read()))
    font.flavor = None
    font.save(target)
    print(target.name)

for family in ("schibstedgrotesk", "geistmono"):
    license_text = urlopen(f"https://raw.githubusercontent.com/google/fonts/main/ofl/{family}/OFL.txt").read()
    (LICENSE_DIR / (family + "_OFL.txt")).write_bytes(license_text)
