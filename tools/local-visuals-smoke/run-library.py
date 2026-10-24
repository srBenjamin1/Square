#!/usr/bin/env python3
"""Run the real Library-tab regression on a prepared disposable emulator."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--populated', action='store_true', help='Expect the documented synthetic fixtures')
parser.add_argument('--source', choices=['LOCAL', 'SPOTIFY', 'YOUTUBE_MUSIC'])
parser.add_argument('--switch-from', choices=['LOCAL', 'SPOTIFY', 'YOUTUBE_MUSIC'])
parser.add_argument('--output', default='/tmp/squareds-library-verification')
args = parser.parse_args()
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
if not serial.startswith('emulator-'):
    parser.error('A disposable emulator is required')
sdk = Path(os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', '/home/srbenjamin/Android/Sdk')))
adb = [str(sdk / 'platform-tools/adb'), '-s', serial]
folder = Path(args.output)
folder.mkdir(parents=True, exist_ok=True)
for source in ([args.source] if args.source else ['LOCAL', 'SPOTIFY', 'YOUTUBE_MUSIC']):
    command = adb + ['shell', 'am', 'instrument', '-w', '-e', 'source', source,
        '-e', 'populated', str(args.populated).lower()]
    if args.switch_from:
        command += ['-e', 'switchFrom', args.switch_from]
    command += ['dev.lelonio.square.visualsmoke/dev.lelonio.square.visualsmoke.LibrarySmoke']
    result = subprocess.run(command, text=True, capture_output=True, timeout=180)
    output = result.stdout + result.stderr
    print(output, flush=True)
    name = f'{source.lower()}-{"populated" if args.populated else "empty"}'
    if args.switch_from:
        name += '-from-' + args.switch_from.lower()
    (folder / f'{name}.txt').write_text(output)
    marker = f'PASS Library {source} populated={str(args.populated).lower()}'
    if result.returncode or marker not in output or 'FAIL' in output or 'Process crashed' in output:
        raise SystemExit(f'Library regression failed: {name}')
