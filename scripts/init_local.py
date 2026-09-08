#!/usr/bin/env python3
"""Generate local-only credentials and a matching RSA public key for the UI."""
import base64
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    targets = [ROOT / '.env', ROOT / 'frontend/.env.local']
    if any(path.exists() for path in targets):
        raise SystemExit('Local configuration already exists; refusing to overwrite keys or passwords.')
    openssl = shutil.which('openssl')
    if not openssl:
        raise SystemExit('OpenSSL is required to generate a new RSA key pair.')
    with tempfile.TemporaryDirectory() as tmp:
        pem = Path(tmp) / 'key.pem'
        subprocess.run([openssl, 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(pem)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        private = subprocess.check_output([openssl, 'pkcs8', '-topk8', '-nocrypt', '-in', str(pem), '-outform', 'DER'])
        public = subprocess.check_output([openssl, 'pkey', '-in', str(pem), '-pubout', '-outform', 'DER'])
    public64 = base64.b64encode(public).decode()
    values = {
        'DB_HOST': '127.0.0.1', 'DB_PORT': '3306', 'DB_NAME': 'eve_corp_manager',
        'DB_USER': 'eve', 'DB_PWD': secrets.token_hex(20),
        'MYSQL_ROOT_PASSWORD': secrets.token_hex(20),
        'REDIS_HOST': '127.0.0.1', 'REDIS_PORT': '6379', 'REDIS_DB': '14',
        'REDIS_PWD': secrets.token_hex(20),
        'BOOTSTRAP_ADMIN_PASSWORD': secrets.token_urlsafe(24),
        'SA_JWT_SECRET': secrets.token_hex(32), 'FIELD_AES_KEY': secrets.token_hex(8),
        'FIELD_RSA_PUBLIC_KEY': public64, 'FIELD_RSA_PRIVATE_KEY': base64.b64encode(private).decode(),
    }
    files = [
        '\n'.join(f'{key}={value}' for key, value in values.items()) + '\n',
        f'VITE_RSA_PUBLIC_KEY={public64}\n',
    ]
    for path, content in zip(targets, files):
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as handle:
            handle.write(content)
    print('Created .env and frontend/.env.local. No credentials were printed.')

if __name__ == '__main__':
    main()
