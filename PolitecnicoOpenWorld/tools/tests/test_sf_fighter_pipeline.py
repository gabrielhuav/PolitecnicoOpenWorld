"""Casos de regresion del gate de assets, sin depender de Android ni de la red."""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sf_fighter_pipeline import check, place


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

    def test_normalizer_refuses_to_silently_clip_art(self):
        with self.assertRaisesRegex(ValueError, 'no cabe'):
            place(Image.new('RGBA', (400, 100), 'white'))


if __name__ == '__main__':
    unittest.main()
