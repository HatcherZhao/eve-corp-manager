#!/usr/bin/env python3
"""Start the built backend with local variables; never interpret .env as shell."""
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
env_file = ROOT / '.env'
jar = ROOT / 'backend/continew-server/target/continew-admin.jar'
if not env_file.exists() or not jar.exists():
    raise SystemExit('Run scripts/init_local.py and build the backend with -Pfat_jar first.')
env = os.environ.copy()
for line in env_file.read_text().splitlines():
    if not line or line.startswith('#'):
        continue
    key, sep, value = line.partition('=')
    if not sep or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
        raise SystemExit('Invalid local environment file.')
    env[key] = value
env['SPRING_PROFILES_ACTIVE'] = 'dev,local'
raise SystemExit(subprocess.call(['java', '-jar', str(jar)], env=env, cwd=ROOT / 'backend'))
