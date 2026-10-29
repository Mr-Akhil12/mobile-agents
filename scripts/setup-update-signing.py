#!/usr/bin/env python3
"""Run once on YOUR computer with JDK keytool and authenticated GitHub CLI.
Private key/password never enter the repository, chat, logs or build artifacts.
"""
import base64, getpass, os, pathlib, shutil, subprocess, sys
REPO = 'Mr-Akhil12/mobile-agents'
def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)
def main():
    if not all(shutil.which(x) for x in ('gh', 'keytool')):
        sys.exit('Install JDK 17+ and GitHub CLI, then gh auth login with repository access.')
    login = subprocess.check_output(['gh', 'api', 'user', '--jq', '.login'], text=True).strip()
    if login.lower() != 'mr-akhil12': sys.exit('Authenticate GitHub CLI as Mr-Akhil12 first.')
    folder = pathlib.Path.home() / 'LocalAgentsSigning'
    folder.mkdir(mode=0o700, exist_ok=True); os.chmod(folder, 0o700)
    key = folder / 'local-agents.p12'
    password = getpass.getpass('Permanent signing password (16+ characters; store safely): ')
    if len(password) < 16: sys.exit('Use at least 16 characters.')
    if password != getpass.getpass('Confirm password: '): sys.exit('Passwords differ.')
    env = os.environ.copy(); env['LOCAL_AGENTS_KEY_PASSWORD'] = password
    if not key.exists():
        run(['keytool', '-genkeypair', '-storetype', 'PKCS12', '-keystore', str(key),
             '-storepass:env', 'LOCAL_AGENTS_KEY_PASSWORD', '-keypass:env', 'LOCAL_AGENTS_KEY_PASSWORD',
             '-alias', 'local-agents', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '36500',
             '-dname', 'CN=Local Agents, O=Personal prototype'], env=env,
             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        run(['keytool', '-list', '-keystore', str(key), '-storepass:env', 'LOCAL_AGENTS_KEY_PASSWORD',
             '-alias', 'local-agents'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.chmod(key, 0o600)
    print('This uploads encrypted repository Actions secrets to ' + REPO + '. Keep this SAME key for every future update.')
    if input('Type SETUP to continue: ').strip() != 'SETUP': sys.exit('No secrets uploaded; local encrypted key retained.')
    run(['gh', 'secret', 'set', 'LOCAL_AGENTS_KEYSTORE_BASE64', '--repo', REPO], input=base64.b64encode(key.read_bytes()))
    run(['gh', 'secret', 'set', 'LOCAL_AGENTS_KEYSTORE_PASSWORD', '--repo', REPO], input=password.encode())
    print('Secrets configured. Back up the encrypted keystore and password privately. Never generate a replacement key for ordinary updates.')
    print('Next: run the Publish signed prototype workflow on main after its PR passes and merges.')
if __name__ == '__main__': main()
