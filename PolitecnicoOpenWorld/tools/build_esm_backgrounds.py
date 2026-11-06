#!/usr/bin/env python3
"""Rebuild only the three ESM stages from the versioned imagegen originals."""
import argparse
from pathlib import Path
import build_map_backgrounds as backgrounds


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path,
                        default=backgrounds.REPO_ROOT / '.codex-tmp/esm-preview')
    args = parser.parse_args()
    source = backgrounds.REPO_ROOT / 'additional_assets/STREETFIGHTER/esm'
    logo = backgrounds.load_logo(backgrounds.DEFAULT_LOGO)
    if logo is None:
        raise SystemExit('Missing canonical POW logo: ' + str(backgrounds.DEFAULT_LOGO))
    stats = backgrounds.BuildStats()
    for name in ('ESM.png', 'ESM Noche 1.png', 'ESM Noche 2.png'):
        backgrounds.process_static(source / name, args.output / 'IMAGES',
                                   args.output / 'static', logo, False, stats)
    print('Built 3 ESM variants in ' + str(args.output))


if __name__ == '__main__':
    main()
