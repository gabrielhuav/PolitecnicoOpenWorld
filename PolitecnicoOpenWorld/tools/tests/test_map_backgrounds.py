"""Contrato entre el pipeline de escenarios y el selector/renderer compartidos."""
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_map_backgrounds as backgrounds


class MapBackgroundPipelineTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def build_static_fixture(self):
        source = self.root / 'Test Stage.png'
        with Image.new('RGB', (640, 360), '#183550') as image:
            image.paste('#aabbcc', (0, 0, 160, 360))
            image.save(source)
        original = source.read_bytes()
        images = self.root / 'IMAGES'
        with Image.new('RGBA', (80, 40), '#ff3300') as logo:
            with contextlib.redirect_stdout(io.StringIO()):
                backgrounds.process_static(
                    source, images, self.root / 'static', logo,
                    False, backgrounds.BuildStats(),
                )
        self.assertEqual(original, source.read_bytes(), 'La fuente debe conservarse intacta')
        return images

    def test_png_pipeline_produces_runtime_ready_atlas_json_and_webp_thumb(self):
        images = self.build_static_fixture()
        atlas = images / 'fondo_test_stage_anim.webp'
        thumb = images / 'fondo_test_stage_anim_thumb.webp'
        metadata = json.loads(atlas.with_suffix('.json').read_text())
        with Image.open(atlas) as image:
            self.assertEqual('WEBP', image.format)
            self.assertEqual(1, image.n_frames, 'El runtime necesita un atlas, no un WebP animado')
            self.assertEqual((metadata['cols'] * metadata['frameWidth'],
                              metadata['rows'] * metadata['frameHeight']), image.size)
            self.assertLessEqual(max(image.size), 2048)
        self.assertEqual((480, 270), (metadata['frameWidth'], metadata['frameHeight']))
        self.assertEqual(28, metadata['frameCount'])
        self.assertTrue(metadata['pingPong'])
        self.assertTrue(metadata['logoApplied'])
        with Image.open(thumb) as image:
            self.assertEqual('WEBP', image.format)
            self.assertEqual(1, image.n_frames)
            self.assertEqual((256, 144), image.size)
        self.assertFalse(thumb.with_suffix('.png').exists())

    def test_diagnostic_accepts_generated_assets_and_rejects_png_or_missing_thumb(self):
        images = self.build_static_fixture()
        theme = self.root / 'SfTheme.kt'
        theme.write_text('SfStageBg("fondo_test_stage_anim.webp", "Test")', encoding='utf-8')
        thumb = images / 'fondo_test_stage_anim_thumb.webp'
        with patch.object(backgrounds, 'THEME_PATH', theme):
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual([], backgrounds.diagnose_theme_backgrounds(images))
                with Image.new('RGB', (256, 144)) as image:
                    image.save(thumb, format='PNG')
                failures = backgrounds.diagnose_theme_backgrounds(images)
                self.assertTrue(any('thumb no es WebP' in failure for failure in failures))
                thumb.unlink()
                failures = backgrounds.diagnose_theme_backgrounds(images)
                self.assertTrue(any('thumb ausente' in failure for failure in failures))

    def test_canonical_shared_theme_contains_real_backgrounds(self):
        files = backgrounds.parse_theme_backgrounds()
        self.assertGreater(len(files), 0)
        self.assertIn('fondo_escom_anim.webp', files)
        self.assertIn('shared/src/commonMain/kotlin', backgrounds.THEME_PATH.as_posix())

    def test_empty_theme_is_a_diagnostic_failure(self):
        theme = self.root / 'SfTheme.kt'
        theme.write_text('// sin escenarios', encoding='utf-8')
        with patch.object(backgrounds, 'THEME_PATH', theme):
            with contextlib.redirect_stdout(io.StringIO()):
                failures = backgrounds.diagnose_theme_backgrounds(self.root)
        self.assertTrue(any('no se encontraron escenarios' in failure for failure in failures))

    def test_missing_theme_is_a_diagnostic_failure(self):
        with patch.object(backgrounds, 'THEME_PATH', self.root / 'missing.kt'):
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertTrue(backgrounds.diagnose_theme_backgrounds(self.root))


if __name__ == '__main__':
    unittest.main()
