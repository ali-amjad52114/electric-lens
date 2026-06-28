#!/usr/bin/env python3
# Convert the normalized float32 CHW raw (vision_encoder input) back to a PNG,
# so we can feed a real Bitmap through the production preprocessing path.
import numpy as np
from PIL import Image

SIZE = 512
raw = np.fromfile("/mnt/c/AI/Projects/ElectricSafe/vfd.raw", dtype=np.float32)
assert raw.size == 3 * SIZE * SIZE, f"unexpected size {raw.size}"
chw = raw.reshape(3, SIZE, SIZE)        # C,H,W normalized to [-1,1] via (x/255-0.5)/0.5
img = (chw * 0.5 + 0.5) * 255.0          # denormalize -> [0,255]
img = np.clip(img, 0, 255).astype(np.uint8)
hwc = np.transpose(img, (1, 2, 0))       # -> H,W,C (RGB)
Image.fromarray(hwc, "RGB").save("/mnt/c/AI/Projects/ElectricSafe/vfd_test.png")
print("wrote vfd_test.png", hwc.shape)
