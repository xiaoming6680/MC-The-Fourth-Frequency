"""Build all terminal stages from one canonical flat control-bay geometry.

The image-generated master is stored in the repository as a 106x206 crop.
Stage variants are color transforms only: they never resample, translate, or
replace the canonical geometry.  Everything outside BAY_BOX is preserved.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


PANEL_SIZE = (512, 256)
BAY_BOX = (389, 24, 495, 230)  # right-exclusive / bottom-exclusive
BAY_SIZE = (BAY_BOX[2] - BAY_BOX[0], BAY_BOX[3] - BAY_BOX[1])
CONTROL_CENTER_X = 442
MODE_CENTER_Y = 78
TUNE_CENTER_Y = 169
MASTER_PATH = Path("tools/assets/terminal_control_bay_master.png")


def clamp_channel(value: float) -> int:
    return max(0, min(255, round(value)))


def stage_variant(master: Image.Image, stage: int) -> Image.Image:
    """Apply stage color without changing a single geometry coordinate."""
    if stage == 0:
        return master.copy()

    result = Image.new("RGB", master.size)
    source = master.load()
    target = result.load()
    for y in range(master.height):
        for x in range(master.width):
            red, green, blue = source[x, y]
            luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722
            if stage == 1:
                # Oxidized cyan: preserve the master's luminance edges and grain.
                target[x, y] = (
                    clamp_channel(red * 0.82 + luminance * 0.02),
                    clamp_channel(green * 0.91 + luminance * 0.10 + 2),
                    clamp_channel(blue * 0.88 + luminance * 0.13 + 4),
                )
            elif stage == 2:
                # Damaged red: darker, warmer metal with the same structural pixels.
                target[x, y] = (
                    clamp_channel(red * 0.86 + luminance * 0.12 + 2),
                    clamp_channel(green * 0.69),
                    clamp_channel(blue * 0.73 + luminance * 0.06 + 1),
                )
            else:
                raise ValueError(f"unsupported terminal stage {stage}")
    return result


def assert_flat_interior(master: Image.Image) -> None:
    """Reject accidental full-width dividers or full-height inner frames."""
    gray = master.convert("L")
    # Ignore the outer canonical frame; only audit its uninterrupted flat face.
    interior = gray.crop((9, 8, master.width - 6, master.height - 7))
    pixels = interior.load()
    horizontal_scores = []
    for y in range(1, interior.height):
        horizontal_scores.append(sum(
            1 for x in range(interior.width)
            if abs(pixels[x, y] - pixels[x, y - 1]) >= 24
        ))
    vertical_scores = []
    for x in range(1, interior.width):
        vertical_scores.append(sum(
            1 for y in range(interior.height)
            if abs(pixels[x, y] - pixels[x - 1, y]) >= 24
        ))
    if max(horizontal_scores, default=0) > interior.width // 3:
        raise AssertionError("canonical bay contains a horizontal divider")
    if max(vertical_scores, default=0) > interior.height // 3:
        raise AssertionError("canonical bay contains an inner vertical frame")


def compose(original_path: Path, master: Image.Image, stage: int, output_path: Path) -> None:
    original = Image.open(original_path).convert("RGB")
    if original.size != PANEL_SIZE:
        raise ValueError(f"{original_path} is {original.size}, expected {PANEL_SIZE}")

    candidate = original.copy()
    candidate.paste(stage_variant(master, stage), BAY_BOX[:2])

    # Protect the display, nameplates and outer terminal frame byte-for-byte.
    protected = Image.new("L", PANEL_SIZE, 255)
    ImageDraw.Draw(protected).rectangle(
        (BAY_BOX[0], BAY_BOX[1], BAY_BOX[2] - 1, BAY_BOX[3] - 1), fill=0
    )
    protected_difference = Image.composite(
        ImageChops.difference(candidate, original), Image.new("RGB", PANEL_SIZE), protected
    )
    if protected_difference.getbbox() is not None:
        raise AssertionError(f"pixels outside {BAY_BOX} changed for {original_path}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    candidate.save(output_path, format="PNG", optimize=True)
    print(f"{output_path}: {candidate.size}, canonical center x={CONTROL_CENTER_X}")


def save_alignment_audit(outputs: list[Path], audit_path: Path) -> None:
    crop_box = (380, 20, 500, 230)
    scale = 3
    crop_width = (crop_box[2] - crop_box[0]) * scale
    crop_height = (crop_box[3] - crop_box[1]) * scale
    audit = Image.new("RGB", (crop_width * len(outputs), crop_height), (0, 0, 0))
    for stage, path in enumerate(outputs):
        crop = Image.open(path).convert("RGB").crop(crop_box)
        draw = ImageDraw.Draw(crop)
        local_x = CONTROL_CENTER_X - crop_box[0]
        draw.line((local_x, 0, local_x, crop.height - 1), fill=(255, 0, 255), width=1)
        for center_y in (MODE_CENTER_Y, TUNE_CENTER_Y):
            local_y = center_y - crop_box[1]
            draw.line((local_x - 8, local_y, local_x + 8, local_y), fill=(255, 230, 0), width=1)
        audit.paste(crop.resize((crop_width, crop_height), Image.Resampling.NEAREST),
                    (stage * crop_width, 0))
    audit_path.parent.mkdir(parents=True, exist_ok=True)
    audit.save(audit_path, format="PNG", optimize=True)
    print(f"{audit_path}: three stages share x={CONTROL_CENTER_X}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("tmp/terminal-flat-panel-backup-20260714"))
    parser.add_argument("--output", type=Path, default=Path("tmp/terminal-flat-candidates"))
    parser.add_argument("--audit", type=Path, default=Path("tmp/terminal-center-audit.png"))
    args = parser.parse_args()

    master = Image.open(MASTER_PATH).convert("RGB")
    if master.size != BAY_SIZE:
        raise ValueError(f"{MASTER_PATH} is {master.size}, expected {BAY_SIZE}")
    assert_flat_interior(master)

    outputs = []
    for stage in range(3):
        output = args.output / f"panel_{stage}.png"
        compose(args.source / f"panel_{stage}.png", master, stage, output)
        outputs.append(output)
    save_alignment_audit(outputs, args.audit)


if __name__ == "__main__":
    main()
