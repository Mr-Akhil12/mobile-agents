#!/usr/bin/env python3
"""Fail closed before publication if an existing published identity/version differs."""
import json, pathlib, subprocess, urllib.request
candidate=json.loads(pathlib.Path('publication/local-agents-update.json').read_text())
releases=json.loads(subprocess.check_output(['gh','api','repos/Mr-Akhil12/mobile-agents/releases?per_page=100']))
for release in releases:
    if release['draft']: continue
    for asset in release['assets']:
        if asset['name'] != 'local-agents-update.json': continue
        req=urllib.request.Request(asset['browser_download_url'], headers={'User-Agent':'Local-Agents-Publication'})
        with urllib.request.urlopen(req, timeout=30) as response:
            raw=response.read(65537)
        assert len(raw)<=65536, 'Published metadata too large'
        previous=json.loads(raw)
        assert candidate['applicationId']==previous['applicationId'], 'Package changed'
        assert candidate['signerSha256']==previous['signerSha256'], 'Permanent signing key changed; stop'
        assert candidate['versionCode']>previous['versionCode'], 'Choose a strictly greater versionCode'
print('Publication version/signing continuity checked against all returned releases.')
