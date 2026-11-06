"""Casos de regresion del gate de assets, sin depender de Android ni de la red."""
import copy
import json
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sf_fighter_pipeline import build, check, place
from build_la_planchada import world_sprite


class FighterAssetGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'DATA').mkdir()
        (self.root / 'IMAGES').mkdir()
        self.recipe = {'character': 'test', 'title': 'Test'}
        self.data = {'frames': {'idle-1': {'src': [0, 0, 256, 256], 'origin': [128, 224]}},
                     'animations': {'idle': [['idle-1', 4]]}}

    def run_check(self, image, data=None):
        (self.root / 'DATA/test.json').write_text(json.dumps(data or self.data))
        image.save(self.root / 'IMAGES/Test.webp', lossless=True)
        return check(self.recipe, self.root, self.root / 'qa')

    def test_detects_original_pr_geometry_and_accepts_normalized_sprite(self):
        image = Image.new('RGBA', (256, 256))
        image.paste((20, 80, 160, 255), (103, 123, 161, 226))
        original = image.tobytes()
        self.assertTrue(self.run_check(image))
        self.assertFalse(self.run_check(place(image, 100)))
        self.assertEqual(original, image.tobytes(), 'La fuente debe conservarse intacta')

    def test_rejects_missing_animation_frame_and_empty_sprite(self):
        data = copy.deepcopy(self.data)
        data['animations']['idle'] = [['missing', 4]]
        self.assertTrue(self.run_check(Image.new('RGBA', (256, 256)), data))
        report = json.loads((self.root / 'qa/test-report.json').read_text())
        self.assertTrue(any('referencia inexistente' in e for e in report['errors']))
        self.assertTrue(any('vacio' in e for e in report['errors']))

    def test_rejects_art_on_cell_edge(self):
        image = Image.new('RGBA', (256, 256))
        image.paste((255, 255, 255, 255), (0, 124, 256, 224))
        self.assertTrue(self.run_check(image))

    def test_gate_checks_horizontal_body_length_instead_of_standing_height(self):
        self.recipe['frame_widths'] = {'fall-5': 110}
        data = {'frames': {'fall-5': {'src': [0, 0, 256, 256], 'origin': [128, 224]}},
                'animations': {'idle': [['fall-5', 4]]}}
        image = Image.new('RGBA', (256, 256))
        image.paste('white', (33, 182, 224, 224))
        self.assertTrue(self.run_check(image, data))
        self.assertFalse(self.run_check(place(image, target_width=110), data))

    def test_compressed_hurt_pose_uses_recipe_calibration(self):
        self.recipe['frame_heights'] = {'hit-face-1': 84}
        data = {'frames': {'hit-face-1': {'src': [0, 0, 256, 256], 'origin': [128, 224]}},
                'animations': {'idle': [['hit-face-1', 4]]}}
        image = Image.new('RGBA', (256, 256))
        image.paste('white', (100, 124, 156, 224))
        self.assertTrue(self.run_check(image, data))
        self.assertFalse(self.run_check(place(image, 84), data))

    def test_normalizer_refuses_to_silently_clip_art(self):
        with self.assertRaisesRegex(ValueError, 'no cabe'):
            place(Image.new('RGBA', (400, 100), 'white'))


class GridSheetBuildTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.addCleanup(patch.stopall)
        patch("sf_fighter_pipeline.ROOT", self.root).start()
        self.data = {
            "frames": {
                "idle-1": {"src": [1000, 1000, 256, 256], "origin": [0, 0], "push": [-16, -80, 32, 78]},
                "fall-1": {"src": [2000, 2000, 256, 256], "origin": [0, 0]},
            },
            "animations": {"idle": [["idle-1", 4]], "ko": [["fall-1", -1]]},
            "events": {"projectile": {"light": {"frame": 2}}},
        }
        (self.root / "moveset.json").write_text(json.dumps(self.data))
        self.sheet = Image.new("RGBA", (256, 128))
        self.sheet.paste("white", (40, 10, 80, 110))
        self.sheet.paste("green", (145, 80, 235, 110))
        self.sheet.save(self.root / "sheet.png")
        self.recipe = {
            "character": "test", "title": "Test", "mode": "grid-sheets",
            "source_data": "moveset.json", "atlas_columns": 2,
            "sheets": [{"path": "sheet.png", "grid": [2, 1]}],
            "frame_cells": {"idle-1": [0, 0], "fall-1": [0, 1]},
            "frame_widths": {"fall-1": 110},
        }

    def test_builds_new_atlas_from_only_art_and_metadata_reproducibly(self):
        original = (self.root / "sheet.png").read_bytes()
        output = self.root / "first"
        build(self.recipe, output)
        self.assertFalse(check(self.recipe, output, self.root / "qa"))
        data = json.loads((output / "DATA/test.json").read_text())
        self.assertEqual([0, 0, 256, 256], data["frames"]["idle-1"]["src"])
        self.assertEqual([256, 0, 256, 256], data["frames"]["fall-1"]["src"])
        self.assertEqual(self.data["animations"], data["animations"])
        self.assertEqual(self.data["events"], data["events"])
        self.assertEqual(self.data["frames"]["idle-1"]["push"], data["frames"]["idle-1"]["push"])
        build(self.recipe, self.root / "second")
        self.assertEqual((output / "DATA/test.json").read_bytes(), (self.root / "second/DATA/test.json").read_bytes())
        self.assertEqual((output / "IMAGES/Test.webp").read_bytes(), (self.root / "second/IMAGES/Test.webp").read_bytes())
        self.assertEqual(original, (self.root / "sheet.png").read_bytes())

    def test_rejects_missing_extra_and_reused_source_cells(self):
        for mapping in ({"idle-1": [0, 0]},
                        {"idle-1": [0, 0], "fall-1": [0, 1], "extra": [0, 1]},
                        {"idle-1": [0, 0], "fall-1": [0, 0]}):
            with self.subTest(mapping=mapping):
                self.recipe["frame_cells"] = mapping
                with self.assertRaises(ValueError):
                    build(self.recipe, self.root / "output")

    def test_rejects_cells_outside_sheet_and_invalid_indices(self):
        for location in ([1, 0], [0, 2], [-1, 0], [0, -1], [0, True], [0]):
            with self.subTest(location=location):
                self.recipe["frame_cells"]["idle-1"] = location
                with self.assertRaises(ValueError):
                    build(self.recipe, self.root / "output")

    def test_requires_pose_calibration_and_consistent_dimensions(self):
        for values in ({}, {"fall-1": 0}, {"missing": 110}):
            with self.subTest(values=values):
                self.recipe["frame_widths"] = values
                with self.assertRaises(ValueError):
                    build(self.recipe, self.root / "output")
        self.recipe["frame_widths"] = {"fall-1": 110}
        self.recipe["frame_heights"] = {"fall-1": 100}
        with self.assertRaisesRegex(ValueError, "no ambos"):
            build(self.recipe, self.root / "output")

    def test_refuses_opaque_or_indivisible_source_sheets(self):
        self.sheet.convert("RGB").save(self.root / "sheet.png")
        with self.assertRaisesRegex(ValueError, "transparencia"):
            build(self.recipe, self.root / "output")
        Image.new("RGBA", (255, 128)).save(self.root / "sheet.png")
        with self.assertRaisesRegex(ValueError, "celdas enteras"):
            build(self.recipe, self.root / "output")

    def test_refuses_clipped_or_empty_source_poses(self):
        for image, message in ((Image.new("RGBA", (256, 128)), "vacia"),
                               (Image.new("RGBA", (256, 128), "white"), "borde")):
            with self.subTest(message=message):
                image.save(self.root / "sheet.png")
                with self.assertRaisesRegex(ValueError, message):
                    build(self.recipe, self.root / "output")

    def test_explicit_source_rect_recovers_complete_pose_crossing_nominal_grid(self):
        image = Image.new("RGBA", (128, 256))
        image.paste("white", (40, 10, 80, 135))
        image.paste("green", (20, 190, 110, 220))
        image.save(self.root / "sheet.png")
        self.recipe["sheets"][0]["grid"] = [1, 2]
        self.recipe["frame_cells"]["fall-1"] = [0, 1]
        with self.assertRaisesRegex(ValueError, "borde"):
            build(self.recipe, self.root / "output")
        self.recipe["frame_rects"] = {"idle-1": [0, 0, 128, 150], "fall-1": [0, 150, 128, 256]}
        build(self.recipe, self.root / "output")
        self.assertFalse(check(self.recipe, self.root / "output", self.root / "qa"))
        self.recipe["frame_rects"]["idle-1"] = [-1, 0, 128, 150]
        with self.assertRaisesRegex(ValueError, "rectangulo fuente invalido"):
            build(self.recipe, self.root / "output")

    def test_explicit_alpha_islands_keep_detached_aura_without_neighbour_pixels(self):
        self.sheet.paste("blue", (90, 60, 100, 70))
        self.sheet.save(self.root / "sheet.png")
        self.recipe["frame_rects"] = {"idle-1": [0, 0, 256, 128]}
        self.recipe["frame_components"] = {"idle-1": [[40, 10], [90, 60]]}
        output = self.root / "output"
        build(self.recipe, output)
        atlas = Image.open(output / "IMAGES/Test.webp").convert("RGBA")
        first = atlas.crop((0, 0, 256, 256))
        pixels = list(first.getdata())
        self.assertTrue(any(b > 100 and r < 50 and g < 50 and a > 128 for r, g, b, a in pixels))
        self.assertFalse(any(g > 50 and r < 50 and b < 50 and a > 128 for r, g, b, a in pixels))
        self.assertFalse(check(self.recipe, output, self.root / "qa"))
        self.recipe["frame_components"]["idle-1"] = [[0, 0]]
        with self.assertRaisesRegex(ValueError, "fuera del arte visible"):
            build(self.recipe, self.root / "output")

    def test_world_derivative_preserves_source_and_uses_world_geometry(self):
        original = self.sheet.tobytes()
        result = world_sprite(self.sheet.crop((0, 0, 128, 128)))
        bbox = result.getchannel("A").getbbox()
        self.assertEqual((512, 512), result.size)
        self.assertEqual(360, bbox[3] - bbox[1])
        self.assertEqual(456, bbox[3])
        self.assertLessEqual(abs((bbox[0] + bbox[2]) / 2 - 256), 1)
        self.assertEqual(original, self.sheet.tobytes())
        # A bent50px SF pose must stay half standing height in the world.
        crouch = Image.new("RGBA", (256, 256))
        crouch.paste("white", (100, 174, 140, 224))
        crouch_box = world_sprite(crouch).getchannel("A").getbbox()
        self.assertEqual(180, crouch_box[3] - crouch_box[1])
        self.assertEqual(456, crouch_box[3])
        wide = Image.new("RGBA", (256, 256))
        wide.paste("white", (20, 124, 220, 224))
        with self.assertRaisesRegex(ValueError, "no cabe"):
            world_sprite(wide)


if __name__ == '__main__':
    unittest.main()
