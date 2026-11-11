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
        "## CatchIt v1.2.3 🚌\n\n"
        "Native real-time MRT feeder bus and transit tracker for Malaysia.\n\n"
        "### What's New in v1.2.3\n"
        "- **Shopify/iOS Tumbler Wheel UI Overhaul**: Refined both Dark Mode (deep graphite card `#161817`) and Light Mode (pure crisp white `#FFFFFF`). Center highlight bar now features a subtle translucent pill with zero harsh borders.\n"
        "- **Simplified Alarm Sheet UI**: Removed redundant walls of text and repetitive lists. Transformed into clean, scannable cards highlighting the active commute schedule (`17:10 – 19:10 · Mon–Fri`).\n"
        "- **No Box-in-Box Nesting**: Stage cards redesigned as unified, high-contrast journey rows.\n"
        "- **Concise Settings Explainer**: Replaced verbose paragraphs with quick 2-stage visual summaries.\n"
        "- **Battery Guard**: Clear status indicators showing when background monitoring sleeps.\n"
        "- **Lightweight (2.8 MB)**: Jetpack Compose + R8 minification.\n\n"
        "### Installation\n"
        "Download `CatchIt-v1.2.3.apk` below and install directly on your Android phone."
    )

    release_payload = json.dumps({
        'tag_name': 'v1.2.3',
        'name': 'CatchIt v1.2.3',
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
                f'{upload_url}?name=CatchIt-v1.2.3.apk',
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
