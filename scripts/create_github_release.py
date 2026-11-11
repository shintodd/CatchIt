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
        "## CatchIt v1.2.6 🚌\n\n"
        "Native real-time MRT feeder bus and transit tracker for Malaysia.\n\n"
        "### What's New in v1.2.6\n"
        "- **Legal & Privacy Compliance**: Full audit against the 20-Point AI App Legal Compliance Checklist.\n"
        "- **Privacy & Terms Integration**: Integrated dedicated in-app links to `PRIVACY_POLICY.md` and `TERMS_OF_SERVICE.md` inside Settings.\n"
        "- **Self-Serve Data Erasure (GDPR Art. 17)**: Added 1-click 'Reset & Erase All Local Data' feature in Settings that clears all local storage, alarms, and preferences.\n"
        "- **Zero Tracking & Data Minimization**: Verified zero user PII collection, zero tracker/analytics SDKs, zero ads.\n"
        "- **120/120 Unit Tests Passing**.\n\n"
        "### Installation\n"
        "Download `CatchIt-v1.2.6.apk` below and install directly on your Android phone."
    )

    release_payload = json.dumps({
        'tag_name': 'v1.2.6',
        'name': 'CatchIt v1.2.6',
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
                f'{upload_url}?name=CatchIt-v1.2.6.apk',
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
