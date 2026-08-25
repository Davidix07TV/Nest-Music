#!/usr/bin/env python3
"""Resize the nest-music-logo.png to all mipmap densities"""

from PIL import Image
import os

# Source image
source_path = r"c:\Users\Principale\Desktop\Davide\Nest-Music\assets\nest-music-logo.png"

# Mipmap densities and their sizes
mipmap_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Open source image
img = Image.open(source_path)
print(f"Original size: {img.size}")

# Resize for each density
base_dir = r"c:\Users\Principale\Desktop\Davide\Nest-Music\app\src\main\res"

for folder, size in mipmap_sizes.items():
    folder_path = os.path.join(base_dir, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    # Resize with high quality
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # Save as PNG (better quality than webp for icons)
    output_path = os.path.join(folder_path, "ic_launcher.png")
    resized.save(output_path, "PNG")
    print(f"Saved {output_path} ({size}x{size})")
    
    # Also save round version
    round_output = os.path.join(folder_path, "ic_launcher_round.png")
    resized.save(round_output, "PNG")
    print(f"Saved {round_output} ({size}x{size})")

# Also create adaptive icon foreground for anydpi-v31
anydpi_v31_path = os.path.join(base_dir, "mipmap-anydpi-v31")
os.makedirs(anydpi_v31_path, exist_ok=True)

# Create adaptive icon XML
adaptive_icon_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>'''

with open(os.path.join(anydpi_v31_path, "ic_launcher.xml"), "w") as f:
    f.write(adaptive_icon_xml)

with open(os.path.join(anydpi_v31_path, "ic_launcher_round.xml"), "w") as f:
    f.write(adaptive_icon_xml)

print("Created adaptive icon XML files")

# Also create foreground image (108x108 for adaptive icon)
foreground_size = 108
foreground = img.resize((foreground_size, foreground_size), Image.Resampling.LANCZOS)
foreground.save(os.path.join(base_dir, "mipmap-xxxhdpi", "ic_launcher_foreground.png"), "PNG")
print(f"Created foreground image ({foreground_size}x{foreground_size})")

print("Done!")