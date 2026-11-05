"""Contrato de lectura de variantes historicas tras retirar duplicados fisicos."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import shutil
from unittest.mock import patch

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sf_gen_cache import aliases, list_gen_files, materialize, resolve_file, validate, CANONICAL
import pack_sf_character


class GenCacheTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.old = self.root / 'newSFAssets/GEN_old/char/idle.png'
        self.target = self.root / CANONICAL / 'char/idle.png'
        self.target.parent.mkdir(parents=True)
        self.target.write_bytes(b'pose-original')
        self.old.parent.mkdir(parents=True)
        manifest = {'aliases': {self.old.relative_to(self.root).as_posix(): {
            'target': self.target.relative_to(self.root).as_posix(),
            'sha256': hashlib.sha256(b'pose-original').hexdigest(), 'bytes': 13}}}
        (self.root / 'newSFAssets/gen_duplicates.json').write_text(json.dumps(manifest))
        self.addCleanup(aliases.cache_clear)

    def test_referencia_y_exportacion_recuperan_bytes_sin_recrear_duplicados(self):
        validate(self.root)
        self.assertEqual(b'pose-original', Path(resolve_file(self.old, self.root)).read_bytes())
        self.assertEqual(['idle.png'], list_gen_files(self.old.parent, self.root))
        output = self.root / 'staging'
        materialize(self.old.parent, output, self.root)
        self.assertEqual(b'pose-original', (output / 'idle.png').read_bytes())
        self.assertFalse(self.old.exists())

    def test_variante_local_tiene_prioridad_y_no_modifica_canonico(self):
        self.old.write_bytes(b'variante-unica')
        materialize(self.old.parent, self.root / 'staging', self.root)
        self.assertEqual(b'variante-unica', (self.root / 'staging/idle.png').read_bytes())
        self.assertEqual(b'pose-original', self.target.read_bytes())

    def test_detecta_destino_canonico_modificado(self):
        self.target.write_bytes(b'otro-contenido')
        with self.assertRaisesRegex(ValueError, 'modificado'):
            validate(self.root)

    def test_rechaza_referencias_fuera_del_repo(self):
        manifest = {'aliases': {self.old.relative_to(self.root).as_posix(): {
            'target': '../escape.png', 'sha256': '', 'bytes': 0}}}
        (self.root / 'newSFAssets/gen_duplicates.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'fuera'):
            validate(self.root)


class HistoricalPackTest(unittest.TestCase):
    def test_atlas_y_json_coinciden_con_resultados_anteriores_a_la_limpieza(self):
        root = Path(__file__).resolve().parents[3]
        expected = json.loads((root / 'newSFAssets/gen_dedup_verification.json').read_text())
        for cache, entry in expected.items():
            with self.subTest(cache=cache), tempfile.TemporaryDirectory() as folder:
                out = Path(folder)
                (out / 'DATA').mkdir()
                (out / 'IMAGES').mkdir()
                shutil.copyfile(root / 'PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/DATA/sf_template.json',
                                out / 'DATA/sf_template.json')
                with patch.object(pack_sf_character, 'DATA_DIR', str(out / 'DATA')), \
                        patch.object(pack_sf_character, 'IMAGES_DIR', str(out / 'IMAGES')):
                    pack_sf_character.pack_character(entry['character'], entry['title'],
                                                     str(root / 'newSFAssets' / cache))
                data = json.loads((out / 'DATA' / (entry['character'] + '.json')).read_text())
                digest = hashlib.sha256(json.dumps(data, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
                self.assertEqual(entry['json_sha256'], digest)
                image = Image.open(out / 'IMAGES' / (entry['title'] + '.webp')).convert('RGBA')
                self.assertEqual(entry['rgba_sha256'], hashlib.sha256(image.tobytes()).hexdigest())
