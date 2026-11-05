"""Referencias de caches GEN historicas a bytes conservados en el GEN canonico.

Una variante fisica siempre prevalece. Las escrituras deben usar la ruta original,
nunca la devuelta por resolve_file (que solo sirve para lectura).
"""
import argparse
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path('newSFAssets/gen_duplicates.json')
CANONICAL = 'newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/'


@lru_cache(maxsize=4)
def aliases(root=ROOT):
    return json.loads((root / MANIFEST).read_text(encoding='utf-8'))['aliases']


def safe_path(root, name):
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Referencia fuera del repositorio: ' + name)
    return path


def resolve_file(path, root=ROOT):
    path = Path(path).resolve()
    if path.is_file() or not path.is_relative_to(root.resolve()):
        return str(path)
    entry = aliases(root).get(path.relative_to(root).as_posix())
    return str(safe_path(root, entry['target'])) if entry else str(path)


def list_gen_files(directory, root=ROOT):
    directory = Path(directory).resolve()
    physical = {p.name for p in directory.iterdir() if p.is_file()} if directory.exists() else set()
    virtual = {Path(name).name for name in aliases(root)
               if safe_path(root, name).parent == directory}
    return sorted(physical | virtual)


def materialize(source, output, root=ROOT):
    """Exporta una cache historica completa para herramientas externas, sin enlaces."""
    source, output = Path(source).resolve(), Path(output).resolve()
    if output.exists() or output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('La salida debe ser nueva e independiente de la cache fuente')
    names = {p.relative_to(source) for p in source.rglob('*') if p.is_file()}
    names.update(safe_path(root, name).relative_to(source) for name in aliases(root)
                 if safe_path(root, name).is_relative_to(source))
    if not names:
        raise ValueError('Cache vacia o desconocida: ' + str(source))
    for name in sorted(names):
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(resolve_file(source / name, root), target)


def validate(root=ROOT):
    for name, entry in aliases(root).items():
        source, target = safe_path(root, name), safe_path(root, entry['target'])
        if not entry['target'].startswith(CANONICAL):
            raise ValueError('El destino no pertenece al GEN canonico: ' + name)
        content = target.read_bytes()
        exact = len(content) == entry['bytes'] and hashlib.sha256(content).hexdigest() == entry['sha256']
        # Git convierte CRLF/LF en metadatos JSON. Mantener el hash original y
        # aceptar ademas la forma LF de Git, sin normalizar imagenes ni otros binarios.
        normalized = content.replace(b'\r\n', b'\n')
        git_text = (target.suffix == '.json' and 'lf_sha256' in entry
                    and len(normalized) == entry['lf_bytes']
                    and hashlib.sha256(normalized).hexdigest() == entry['lf_sha256'])
        if not exact and not git_text:
            raise ValueError('Destino canonico modificado: ' + entry['target'])
        if source.is_file() and source.read_bytes() == content:
            raise ValueError('Duplicado reintroducido: ' + name)
    print('Referencias GEN verificadas:', len(aliases(root)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['check', 'materialize'])
    parser.add_argument('source', nargs='?')
    parser.add_argument('output', nargs='?')
    args = parser.parse_args()
    if args.command == 'check':
        validate()
    elif args.source and args.output:
        materialize(args.source, args.output)
    else:
        parser.error('materialize necesita source y output')
