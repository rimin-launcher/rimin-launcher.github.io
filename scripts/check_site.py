"""Check the public website, downloads and signed update feeds before Pages deployment."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

BASE_URL = 'https://rimin-launcher.github.io/'
PUBLIC_KEY = 'MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAuI5h4EDtDSRv/RtaQmty/m4qAKrhZQuTesx/SSxg60atEdR5svajiIX8Bcqp4huMGNAEY0aesvYjHCkRbJmXqEmg150RcJkpfOj3ZH6f+08ON2rDSbtdgymzDX70aESvVOGB5nOfOCCwbO/2fwuyrPwwnq1QSOtVJ6pUDH42XTxVY864QkU48iUk276GakjqBPsV+vZJFg4RFTW0n8ZUh1aXlx6FSG4KSsnfLNh50cVcmiJ7eOSFSrygGPy0R5pSnOqze7c5WvMdG7u+1gYx/33JX8Y381WedCDD24HTVTn097CJPFQA+2/UF/XXwdoSV4MBqpWmwVWCK5C7i8hbEhr+hkkrg8MrT4Yvmsk3X1KRR14Buhg5KxloWXZKAieSu1NRxyUDe8zSbqjxQer/TEmyN708Lx2SmZ6jKHgHdazXcQOJD8YaERIiU9IiuU3JjvX+1TmISFvCpeXmUfsvezkU4f14PsbPNr2gkBqGvjAtpFY0CfCjT4Ejt3GNWXyvAgMBAAE='
CERTIFICATE = 'cf74eb862c21f5af4ed3c4987ce6ea391fff23eb793852b88c9473975156bd6c'
STATIC_FILES = {
    '.nojekyll', 'index.html', 'privacy.html', 'licenses.html', 'release-notes.html',
    'releases.json', 'robots.txt', 'assets/site.js', 'assets/site.css', 'assets/mark.svg',
    'licenses/third-party-notices.txt', 'licenses/tailnet-notices.txt', 'licenses/mit-notice.txt',
    'updates/vehicle-public.json', 'updates/phone-public.json',
}
REPOSITORY_FILES = {'README.md', '.github/workflows/pages.yml', 'scripts/check_site.py'}

# Keep earlier published downloads for direct links and cached signed feeds.
ARCHIVED_DOWNLOADS = {
    'downloads/SealionHome-0.12.11-vehicle-release.apk':
        (24682890, 'a9133684a6c00d9a00942c13844f419a3779e4ad2cb9302c455051fbf5660bb9'),
    'downloads/SealionHome-0.12.12-vehicle-release.apk':
        (24682890, '470491f69f302a7c54cf91b18c856e2d9705d37b0b703b7320cc248fccf3a63d'),
    'downloads/SealionLink-0.8.73-phone-release.apk':
        (23647825, '1e0f5d405b2c6802d41ae084b1fb687c6b37b7c5a39ec3b3ad23d5be6250ae42'),
    'downloads/SealionLink-0.8.74-phone-release.apk':
        (23647825, 'd6bf868f8389945698f2ebd4cb3a9802fda0b99288128b9460331efe07085c9b'),
    'downloads/SealionWatch-0.1.0-watch-release.apk':
        (304188, 'f538a6a36848ef714f693f373ebaa3cdf9d08c07c6e092d7863d1ee01bc0ffa9'),
}



def require(value, message):
    if not value:
        raise ValueError(message)


def valid_notes(notes):
    if not isinstance(notes, list) or not 0 < len(notes) <= 20:
        return False
    try:
        return all(isinstance(note, str) and note and '\0' not in note
                   and len(note.encode('utf-16-le')) // 2 <= 500 for note in notes)
    except UnicodeEncodeError:
        return False


def files(directory, ignore_git=False):
    require(directory.is_dir() and not directory.is_symlink(), 'Expected a real directory')
    found = {}
    for path in directory.rglob('*'):
        relative = path.relative_to(directory).as_posix()
        if ignore_git and (relative == '.git' or relative.startswith('.git/')):
            continue
        require(not path.is_symlink(), 'Symbolic links cannot be published')
        if path.is_file():
            require(path.stat().st_nlink == 1, 'Hard links cannot be published')
            found[relative] = path
    return found


def verify_signature(raw, signature):
    der = base64.b64decode(PUBLIC_KEY, validate=True)
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding, rsa
    except ImportError:
        executable = shutil.which('openssl')
        require(executable, 'OpenSSL is required to verify signed update feeds')
        encoded = base64.b64encode(der).decode('ascii')
        pem = '-----BEGIN PUBLIC KEY-----\n' + '\n'.join(encoded[i:i+64] for i in range(0, len(encoded), 64)) + '\n-----END PUBLIC KEY-----\n'
        with tempfile.TemporaryDirectory(prefix='sealion-feed-check-') as temporary:
            temporary = Path(temporary)
            (temporary / 'public.pem').write_text(pem, encoding='ascii')
            (temporary / 'payload').write_bytes(raw)
            (temporary / 'signature').write_bytes(signature)
            result = subprocess.run([executable, 'dgst', '-sha256', '-verify', str(temporary / 'public.pem'),
                                     '-signature', str(temporary / 'signature'), str(temporary / 'payload')],
                                    capture_output=True)
            require(result.returncode == 0, 'Update feed signature is invalid')
    else:
        key = serialization.load_der_public_key(der)
        require(isinstance(key, rsa.RSAPublicKey) and key.key_size >= 2048, 'Invalid pinned update public key')
        key.verify(signature, raw, padding.PKCS1v15(), hashes.SHA256())


def check(site, repository=None):
    require(BASE_URL.startswith('https://') and BASE_URL.endswith('/') and '__' not in BASE_URL,
            'Publication URL has not been configured')
    inventory = files(site)
    require(STATIC_FILES <= inventory.keys(), 'A required public website file is missing')
    releases = json.loads(inventory['releases.json'].read_text(encoding='utf-8'))
    require(releases.get('schema') == 1 and re.fullmatch(r'\d{4}-\d{2}-\d{2}', releases.get('date', '')),
            'Invalid public release metadata')
    allowed = set(STATIC_FILES)
    for kind, product, package in [('vehicle', 'SealionHome', 'kr.personal.sealion.home.release'),
                                   ('phone', 'SealionLink', 'kr.personal.sealion.link.release')]:
        value = releases.get(kind)
        require(isinstance(value, dict), 'Both public downloads must be ready before publication')
        version = value.get('versionName')
        require(isinstance(version, str) and re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid public version')
        filename = 'downloads/' + product + '-' + version + '-' + kind + '-release.apk'
        require(value.get('file') == filename and filename in inventory, 'Public APK path mismatch')
        require(type(value.get('versionCode')) is int and value['versionCode'] > 0, 'Invalid public version code')
        require(type(value.get('bytes')) is int and 0 < value['bytes'] < 100 * 1024 * 1024,
                'APK must fit the repository file limit')
        require(re.fullmatch(r'[0-9a-f]{64}', value.get('sha256', '')), 'Invalid public APK hash')
        path = inventory[filename]
        require(path.stat().st_size == value['bytes'], 'Public APK size mismatch')
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        require(digest == value['sha256'], 'Public APK hash mismatch')
        feed = inventory['updates/' + kind + '-public.json'].read_bytes()
        require(0 < len(feed) <= 131072, 'Signed feed exceeds the app reader size bound')
        envelope = json.loads(feed)
        require(set(envelope) == {'payload', 'signature'}, 'Unexpected signed feed fields')
        raw = base64.b64decode(envelope['payload'], validate=True)
        signature = base64.b64decode(envelope['signature'], validate=True)
        require(0 < len(raw) <= 65536 and 0 < len(signature) <= 1024, 'Signed feed exceeds its size bound')
        verify_signature(raw, signature)
        payload = json.loads(raw)
        require(type(payload.get('minSdk')) is int and 28 <= payload['minSdk'] <= 34,
                'Invalid signed minimum Android SDK')
        expected = {'schema': 1, 'app': kind, 'channel': 'public', 'package': package,
                    'versionCode': value['versionCode'], 'versionName': version,
                    'apkUrl': BASE_URL + filename, 'apkBytes': value['bytes'], 'sha256': digest,
                    'certificateSha256': [CERTIFICATE]}
        require(all(payload.get(key) == expected_value for key, expected_value in expected.items()),
                'Signed feed differs from the public APK or install identity')
        require(valid_notes(payload.get('changelog')), 'Invalid public release notes')
        allowed.add(filename)
    for name, (size, digest) in ARCHIVED_DOWNLOADS.items():
        if name in inventory:
            path = inventory[name]
            require(path.stat().st_size == size and hashlib.sha256(path.read_bytes()).hexdigest() == digest,
                    'Archived public APK differs from its previously verified release')
            allowed.add(name)
    require(set(inventory) == allowed, 'Unexpected files in public website output')
    if repository is not None:
        root_files = files(repository, ignore_git=True)
        expected_files = REPOSITORY_FILES | {'site/' + name for name in allowed}
        require(set(root_files) == expected_files, 'Repository contains files outside the public website allowlist')
    return len(allowed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, required=True)
    parser.add_argument('--repository', type=Path)
    args = parser.parse_args()
    count = check(args.site.resolve(), args.repository.resolve() if args.repository else None)
    print('Public website verified: ' + str(count) + ' files; two signed feeds and matching APKs.')


if __name__ == '__main__':
    main()
