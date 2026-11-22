"""Build a preview of MG4Link's 300 ms startup fade from rendered layouts."""
from pathlib import Path

from PIL import Image

output = Path("app/build/screenshots")
with Image.open(output / "startup_overlay.png") as splash, Image.open(output / "home_update_up_to_date.png") as home:
    splash_rgb = splash.convert("RGB")
    home_rgb = home.convert("RGB")
    frames = [Image.blend(splash_rgb, home_rgb, step / 10) for step in range(11)]
    frames[0].save(output / "startup.gif", save_all=True, append_images=frames[1:],
                   duration=[700, *([30] * 9), 1500], loop=0)
