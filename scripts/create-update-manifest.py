#!/usr/bin/env python3
"""Describe the already built, apksigner-verified APK; never handles private keys."""
import argparse, hashlib, json, pathlib, re, subprocess
p=argparse.ArgumentParser(); p.add_argument('apk',type=pathlib.Path);p.add_argument('--tools',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
sig=subprocess.check_output([str(a.tools/'apksigner'),'verify','--print-certs',str(a.apk)],text=True)
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',sig)
assert cert and 'Signer #2 certificate' not in sig
badging=subprocess.check_output([str(a.tools/'aapt'),'dump','badging',str(a.apk)],text=True)
package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
assert package and package[1]=='dev.lfm.mobile'
manifest=dict(applicationId=package[1],versionCode=int(package[2]),versionName=package[3],apk=a.apk.name,
              bytes=a.apk.stat().st_size,sha256=hashlib.file_digest(a.apk.open('rb'),'sha256').hexdigest(),signerSha256=cert[1],minSdk=28)
a.output.write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
