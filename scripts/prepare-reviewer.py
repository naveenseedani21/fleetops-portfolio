#!/usr/bin/env python3
"""Prepare an isolated copy of the documented Compose setup; never starts or deletes services."""
import argparse
import json
from pathlib import Path
import re
import shlex
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--project', required=True, help='A new Compose project name, e.g. fleetops-reviewer-20261113')
parser.add_argument('--output', type=Path, help='New fixture directory; use /private/tmp/<project> if Docker cannot read a synced workspace')
args = parser.parse_args()
if not re.fullmatch(r'fleetops-reviewer-[a-z0-9-]+', args.project):
    parser.error('Use a new fleetops-reviewer-<suffix> project name')
repo = Path(__file__).resolve().parents[1]
output = args.output.resolve() if args.output else repo / 'work' / args.project
if output.exists():
    parser.error('That fixture directory already exists; choose a fresh project name. Nothing was overwritten.')
(output / 'keycloak').mkdir(parents=True)
(output / 'scripts').mkdir()
shutil.copyfile(repo / 'scripts/init-db.sh', output / 'scripts/init-db.sh')
realm = json.loads((repo / 'infrastructure/keycloak/fleetops-realm.json').read_text())
for client in realm['clients']:
    if client['clientId'] == 'fleetops-web':
        client['redirectUris'] = ['http://localhost:14200/*']
        client['webOrigins'] = ['http://localhost:14200']
(output / 'keycloak/fleetops-realm.json').write_text(json.dumps(realm, indent=2) + '\n')
config = json.loads((repo / 'frontend/public/config.json').read_text())
config['issuer'] = 'http://localhost:18180/realms/fleetops'
(output / 'config.json').write_text(json.dumps(config) + '\n')
(output / 'nginx.conf').write_text((repo / 'frontend/nginx.conf').read_text().replace('http://localhost:8180', 'http://localhost:18180'))
compose = (repo / 'docker-compose.yml').read_text()
replacements = {
    'build: ./backend': 'build: ' + json.dumps(str(repo / 'backend')),
    'build: ./frontend': 'build: ' + json.dumps(str(repo / 'frontend')),
    '"./scripts:/scripts:ro"': json.dumps(str(output / 'scripts') + ':/scripts:ro'),
    '"./infrastructure/keycloak:/opt/keycloak/data/import:ro"': json.dumps(str(output / 'keycloak') + ':/opt/keycloak/data/import:ro'),
    '127.0.0.1:8000:8000': '127.0.0.1:18000:8000',
    '127.0.0.1:8180:8080': '127.0.0.1:18180:8080',
    '127.0.0.1:8080:8080': '127.0.0.1:18080:8080',
    '127.0.0.1:4200:8080': '127.0.0.1:14200:8080',
    'http://localhost:8180': 'http://localhost:18180',
}
for old, new in replacements.items():
    if old not in compose:
        raise SystemExit('Documented Compose structure changed; review the generator before continuing: ' + old)
    compose = compose.replace(old, new)
compose = compose.replace('    depends_on: [backend]', '    volumes:\n      - ' + json.dumps(str(output / 'config.json') + ':/usr/share/nginx/html/config.json:ro') + '\n      - ' + json.dumps(str(output / 'nginx.conf') + ':/etc/nginx/conf.d/default.conf:ro') + '\n    depends_on: [backend]')
(output / 'docker-compose.yml').write_text(compose)
print(f'Prepared {output}')
compose_path = shlex.quote(str(output / 'docker-compose.yml'))
print(f'docker compose -p {args.project} -f {compose_path} config --quiet')
print(f'docker compose -p {args.project} -f {compose_path} up --build -d')
print('UI: http://localhost:14200 | API: http://localhost:18080 | identity: http://localhost:18180')
print('Existing services and volumes were not changed. Use this new project name consistently; do not delete volumes.')
