import subprocess
import urllib.request
import json
import os

def main():
    p = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate('protocol=https\nhost=github.com\n\n')
    token = ''
    for line in out.splitlines():
        if line.startswith('password='):
            token = line.split('=', 1)[1]

    apk_path = r'C:\Users\ainol\Dev\FeederMY\feedermy-android\app\build\outputs\apk\release\app-release.apk'
    apk_size = os.path.getsize(apk_path)
    print(f'Uploading APK size: {apk_size} bytes')

    release_notes = (
        "## CatchIt v1.2.8 🚌\n\n"
        "Native real-time MRT feeder bus and transit tracker for Malaysia.\n\n"
        "### What's New in v1.2.8\n"
        "- **Nationwide Live Fleet Normalization**: Added full operator route mapping and direction stripping covering 140+ previously missing bus routes.\n"
        "- **Council Lines Live Tracking**: Live tracking restored for Petaling Jaya (PJ01–PJ06), Putrajaya (P101–P108), Shah Alam (SA02–SA08), Ampang Jaya (AJ01–AJ04), Kajang (KJ01–KJ04), Selayang (MPS1–MPS4), and Banting (BTG1–BTG2).\n"
        "- **Trunk & Feeder Variant Tracking**: Live tracking restored for numeric trunk lines (e.g. 420, 600, 754 broadcast as U-prefixed) and feeder direction variants (T810B, T110B, T711B, etc.).\n"
        "- **Clean Route Details**: Fixed duplicate headsign display (e.g. D40 D40 now shows clean route name).\n"
        "- **Active Fleet Jump**: 760+ buses now tracked concurrently in real-time.\n\n"
        "### Installation\n"
        "Download `CatchIt-v1.2.8.apk` below or update directly in-app."
    )

    release_payload = json.dumps({
        'tag_name': 'v1.2.8',
        'name': 'CatchIt v1.2.8',
        'body': release_notes,
        'draft': False,
        'prerelease': False
    }).encode('utf-8')

    req = urllib.request.Request(
        'https://api.github.com/repos/shintodd/CatchIt/releases',
        data=release_payload,
        headers={
            'Authorization': f'token {token}',
            'User-Agent': 'CatchIt-Setup',
            'Content-Type': 'application/json',
            'Accept': 'application/vnd.github.v3+json'
        }
    )

    try:
        with urllib.request.urlopen(req) as resp:
            release = json.loads(resp.read().decode('utf-8'))
            upload_url = release.get('upload_url', '').split('{')[0]
            html_url = release.get('html_url')
            print(f'Release created: {html_url}')
            print(f'Upload URL: {upload_url}')

            with open(apk_path, 'rb') as f:
                apk_bytes = f.read()

            upload_req = urllib.request.Request(
                f'{upload_url}?name=CatchIt-v1.2.8.apk',
                data=apk_bytes,
                headers={
                    'Authorization': f'token {token}',
                    'User-Agent': 'CatchIt-Setup',
                    'Content-Type': 'application/vnd.android.package-archive',
                    'Content-Length': str(len(apk_bytes)),
                    'Accept': 'application/vnd.github.v3+json'
                }
            )
            with urllib.request.urlopen(upload_req) as up_resp:
                asset = json.loads(up_resp.read().decode('utf-8'))
                print('APK uploaded successfully!')
                print('Download URL:', asset.get('browser_download_url'))

    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='ignore')
        print(f'HTTP {e.code}: {body}')
    except Exception as e:
        print('Error:', e)

if __name__ == '__main__':
    main()
