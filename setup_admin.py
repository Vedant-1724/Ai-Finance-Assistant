import json
import os
import subprocess
import sys
import time
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parent
DEFAULT_CONFIG_PATHS = [
    ROOT / 'admin.local.json',
]


def load_admin_config():
    for path in DEFAULT_CONFIG_PATHS:
        if path.is_file():
            with path.open('r', encoding='utf-8') as handle:
                data = json.load(handle)
            if data.get('email') and data.get('password') and data.get('companyName'):
                return data

    email = os.getenv('BOOTSTRAP_ADMIN_EMAIL')
    password = os.getenv('BOOTSTRAP_ADMIN_PASSWORD')
    company_name = os.getenv('BOOTSTRAP_ADMIN_COMPANY')
    if email and password and company_name:
        return {
            'email': email,
            'password': password,
            'companyName': company_name,
        }

    raise RuntimeError('Admin config not found. Add admin.local.json or set BOOTSTRAP_ADMIN_* environment variables.')


config = load_admin_config()
email = config['email']
password = config['password']
company_name = config['companyName']

url = 'http://localhost:8080/actuator/health'
print('Waiting for backend to start on port 8080...')
for _ in range(60):
    try:
        response = requests.get(url, timeout=5)
        if response.status_code == 200:
            print('Backend is ready!')
            break
    except requests.exceptions.RequestException:
        pass
    time.sleep(2)
else:
    print('Backend did not become ready within 120 seconds.')
    sys.exit(1)

print('Registering admin user...')
payload = {
    'email': email,
    'password': password,
    'companyName': company_name,
}
try:
    auth_resp = requests.post('http://localhost:8080/api/v1/auth/register', json=payload, timeout=15)
    print('Registration response:', auth_resp.status_code, auth_resp.text)
except Exception as exc:
    print('Registration failed:', exc)
    sys.exit(1)

print('Updating user in database...')
sql_command = f"UPDATE users SET email_verified = true, subscription_status = 'MAX' WHERE email = '{email}';"
docker_cmd = ['docker', 'exec', 'finance-postgres', 'psql', '-U', 'postgres', '-d', 'financedb', '-c', sql_command]
result = subprocess.run(docker_cmd, capture_output=True, text=True)
print('DB Update STDOUT:', result.stdout)
print('DB Update STDERR:', result.stderr)
if result.returncode == 0:
    print('Successfully initialized admin user.')
else:
    print('Failed to initialize user in DB.')
    sys.exit(1)

