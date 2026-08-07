#!/usr/bin/env python3
"""Generate the stability anchor's entity sheets.

The model unwraps against eight uniform 32x32 material islands on a 128x128 sheet - see
src/client/java/com/xm/thefourthfrequency/client_render/StabilityAnchorUv.java, which is the
authority on where each island is and what it is for. Every island here is a flat, self-similar
patch of one substance, so a box may unwrap anywhere inside its own island and still land on the
right material; nothing in the model depends on a per-part island.

Two sheets are written:

  stability_anchor.png           fully opaque base colour
  stability_anchor_emissive.png  transparent except the core island and the seam strip

The emissive mask is deliberately almost empty. Only the chest core, the relay core and a hairline
of gold through each claw pivot are allowed to glow; the structure itself has to keep reading as
obsidian and bone standing in the dark, or the destruction has nowhere left to go.

Deterministic: every random draw is seeded per island, so re-running this reproduces both files
byte for byte and ResourceContractTest's dimension and sparseness checks stay meaningful.
"""

from __future__ import annotations

import hashlib
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"

SHEET = 128
ISLAND = 32

# (name, u, v, base rgb, fleck rgb, fleck density, streak rgb)
BASE_ISLANDS = [
	# Near-black obsidian with a violet sheen: the torso column, its plinth and its cap.
	("obsidian", 0, 0, (24, 19, 32), (58, 42, 84), 0.16, (14, 11, 20)),
	# Cold bone ash: the claw arms. Light enough to hold the silhouette against the End's own sky.
	("claw", 32, 0, (168, 166, 158), (196, 194, 186), 0.20, (128, 126, 120)),
	# Dim violet: every pivot and wrist axle.
	("joint", 64, 0, (60, 44, 84), (86, 66, 118), 0.18, (40, 29, 58)),
	# Restrained ancient gold. Used for seams only; nothing on this anchor is plated in it.
	("gold", 96, 0, (172, 138, 70), (206, 172, 96), 0.22, (124, 97, 46)),
	# Platinum white: both cores, the absorbed strike's shell and its ripple.
	("core", 0, 32, (236, 231, 214), (255, 250, 236), 0.26, (206, 190, 154)),
	# Pale metal flecked with gold: the four calibration petals.
	("petal", 32, 32, (150, 148, 142), (186, 160, 96), 0.14, (116, 114, 110)),
	# Dark metal: the hairline axle under the relay core.
	("spindle", 64, 32, (74, 70, 80), (104, 98, 112), 0.16, (52, 48, 58)),
	# Darker, rougher grip pads; these are the parts actually touching the bedrock cap.
	("foot", 96, 32, (118, 114, 108), (142, 138, 130), 0.20, (86, 83, 78)),
]

# The seam strip: the only part of the gold island a box ever unwraps into. A 4.6x1x1 bar has a
# footprint of 11.2 by 2 pixels, so painting a little past that keeps the hairline continuous
# without lighting up the rest of the island.
SEAM_STRIP = (0, 0, 15, 4)


def _island(image: Image.Image, u: int, v: int, base, fleck, density: float, streak, seed: int) -> None:
	rng = random.Random(seed)
	pixels = image.load()
	for y in range(v, v + ISLAND):
		for x in range(u, u + ISLAND):
			roll = rng.random()
			if roll < density * 0.45:
				colour = fleck
			elif roll < density:
				colour = streak
			else:
				# A shallow per-pixel jitter so a flat patch still has grain at model scale.
				jitter = rng.randint(-6, 6)
				colour = tuple(max(0, min(255, channel + jitter)) for channel in base)
			pixels[x, y] = (*colour, 255)


def build_base() -> Image.Image:
	# Started opaque obsidian rather than transparent: the four unused islands are never sampled by
	# the model, but a cutout render type would punch holes if a UV ever drifted into them.
	image = Image.new("RGBA", (SHEET, SHEET), (24, 19, 32, 255))
	for index, (name, u, v, base, fleck, density, streak) in enumerate(BASE_ISLANDS):
		_island(image, u, v, base, fleck, density, streak, 4100 + index * 17)
	return image


def build_emissive() -> Image.Image:
	image = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
	pixels = image.load()
	rng = random.Random(4271)

	# The core island, painted edge to edge: both cores, the pressure shell and the ripple all
	# unwrap into it, and every one of them is meant to be lit.
	core_u, core_v = 0, 32
	for y in range(core_v, core_v + ISLAND):
		for x in range(core_u, core_u + ISLAND):
			# Warm platinum with a faint inward gradient, so a 6x6x6 cube does not read as a
			# perfectly flat white block at arena distance.
			dx = abs((x - core_u) - ISLAND / 2) / (ISLAND / 2)
			dy = abs((y - core_v) - ISLAND / 2) / (ISLAND / 2)
			edge = max(dx, dy)
			alpha = int(255 - edge * 46)
			warm = int(rng.randint(-5, 5))
			pixels[x, y] = (
				min(255, 255 + warm),
				min(255, 246 + warm),
				min(255, max(0, 214 - int(edge * 24) + warm)),
				max(0, min(255, alpha)),
			)

	# The seam strip on the gold island, at a deliberately low alpha: a hairline through the pivot,
	# not a lit bar down the arm.
	gold_u, gold_v = 96, 0
	x0, y0, x1, y1 = SEAM_STRIP
	for y in range(gold_v + y0, gold_v + y1):
		for x in range(gold_u + x0, gold_u + x1):
			flicker = rng.randint(-12, 12)
			pixels[x, y] = (255, 214, 132, max(0, min(255, 132 + flicker)))
	return image


def main() -> None:
	ENTITY.mkdir(parents=True, exist_ok=True)
	written = []
	for name, image in (("stability_anchor.png", build_base()),
			("stability_anchor_emissive.png", build_emissive())):
		path = ENTITY / name
		image.save(path, optimize=True)
		written.append(path)
	for path in written:
		digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
		print(f"{path.relative_to(ROOT)} {digest}")


if __name__ == "__main__":
	main()
