import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generar_celdas import celda, celdas_de_way, generar


class CallesTest(unittest.TestCase):
    def test_clave_escom_coincide_con_kotlin(self):
        self.assertEqual((975, -4958), celda(19.504603, -99.145985))

    def test_incluye_celda_cruzada_sin_nodos_dentro(self):
        puntos = [(1, 19.505, -99.21), (2, 19.505, -99.09)]
        self.assertIn((975, -4958), celdas_de_way(puntos, (975, 975, -4958, -4958)))

    def test_no_incluye_rectangulo_que_no_toca_segmento_diagonal(self):
        puntos = [(1, 19.40, -99.20), (2, 19.60, -99.00)]
        self.assertEqual(set(), celdas_de_way(puntos, (970, 970, -4952, -4952)))

    def test_salida_conserva_nodos_y_filtra_tipos_no_jugables(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'test.osm'
            source.write_text('''<osm version="0.6">
              <node id="1" lat="19.5046" lon="-99.146"/>
              <node id="2" lat="19.505" lon="-99.145"/>
              <way id="10"><nd ref="1"/><nd ref="2"/><tag k="highway" v="residential"/></way>
              <way id="11"><nd ref="1"/><nd ref="2"/><tag k="highway" v="construction"/></way>
            </osm>''')
            generar(str(source), str(root / 'out'), (-99.16, 19.50, -99.14, 19.52), 'fixture')
            data = json.loads((root / 'out/v1/celdas/975_-4958.json').read_text())
            nodes = {e['id'] for e in data['elements'] if e['type'] == 'node'}
            ways = [e for e in data['elements'] if e['type'] == 'way']
            self.assertEqual([10], [w['id'] for w in ways])
            self.assertTrue(set(ways[0]['nodes']).issubset(nodes))


if __name__ == '__main__':
    unittest.main()
