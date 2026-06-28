# Demo evidence images (Electric Lens)

Deterministic, high-contrast label images the on-device VLM (SmolVLM/NPU) can read,
used to walk the full demo flow to the Permit state. Upload these via the app's
**"Upload evidence"** buttons (not Capture — Capture uses the live camera).

## Path to PERMIT (5 uploads, in order)

| App step | Image | Reads as |
|----------|-------|----------|
| AI Session → Upload Fault Code Image | `vfd_test.png` | fault code **F071** |
| Guided LOTO Step 1 — Breaker B-201 | `breaker_b201_off.png` | identity **B-201**, handle **OFF** |
| Step 2 — Breaker B-205 | `breaker_b205_off.png` | identity **B-205**, handle **OFF** |
| Step 3 — Lock & Tag | `lock_tag.png` | padlock + danger tag applied (B-201) |
| Step 4 — MCC Cabinet | `mcc_open.png` | cabinet **DOOR: OPEN** → Permit ready |

## Show WORK BLOCKED (safety gate)
At Step 1 (expects B-201), upload `breaker_b205_off.png` →
**"WORK BLOCKED — Mismatch detected. Expected B-201. Detected B-205."** (no advance).

## Regenerating
- `gen_loto.py` — breaker B-201/B-205 OFF + lock/tag images
- `gen_mcc.py` — MCC cabinet door-open image
- `raw_to_png.py` — converts the vision_encoder `.raw` back to `vfd_test.png`

Run with Python + Pillow. Images are 512×512 RGB (the model's input size).
On the device they live in `/sdcard/Pictures/` so the photo picker can see them.
