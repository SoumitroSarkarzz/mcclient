#!/usr/bin/env python3
"""Install a built client JAR into a local Minecraft instance."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import sys
import zipfile


def default_mods():
    if platform.system() == 'Windows':
        return Path(os.environ.get('APPDATA', Path.home() / 'AppData/Roaming')) / '.minecraft/mods'
    if platform.system() == 'Darwin':
        return Path.home() / 'Library/Application Support/minecraft/mods'
    return Path.home() / '.minecraft/mods'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mods-dir', type=Path, default=default_mods(), help='Instance mods directory; required for custom launchers')
    parser.add_argument('--jar', type=Path, default=Path(__file__).resolve().parents[1] / 'build/libs/mcclient-1.1.1+mc1.21.11.jar')
    args = parser.parse_args()
    if not args.jar.is_file():
        parser.error(f'Build or download the client JAR first: {args.jar}')
    try:
        with zipfile.ZipFile(args.jar) as jar:
            import json
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata.get('id') != 'mcclient':
                parser.error('Selected file is not an MC Client mod')
    except (OSError, zipfile.BadZipFile, KeyError, ValueError) as e:
        parser.error(f'Invalid mod JAR: {e}')
    if not args.mods_dir.parent.is_dir():
        parser.error(f'Minecraft instance not found at {args.mods_dir.parent}; specify --mods-dir')
    args.mods_dir.mkdir(exist_ok=True)
    destination = args.mods_dir / args.jar.name
    if destination.exists():
        if destination.read_bytes() == args.jar.read_bytes():
            print(f'Already installed: {destination}')
            return
        parser.error(f'Refusing to overwrite {destination}; move the previous JAR out first')
    other_versions = list(args.mods_dir.glob('mcclient-*.jar'))
    if other_versions:
        parser.error('Remove old MC Client JARs before installing: ' + ', '.join(map(str, other_versions)))
    shutil.copy2(args.jar, destination)
    print(f'Installed: {destination}')
    print('Requires Fabric Loader for Minecraft 1.21.11 and Fabric API in the same mods folder.')


if __name__ == '__main__':
    main()
