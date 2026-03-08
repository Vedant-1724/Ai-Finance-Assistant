import time
import requests
import subprocess
import sys

# 1. Wait for backend to be ready
url = "http://localhost:8080/actuator/health"
print("Waiting for backend to start on port 8080...")
for _ in range(60):
    try:
        response = requests.get(url)
        if response.status_code == 200:
            print("Backend is ready!")
            break
    except requests.exceptions.ConnectionError:
        pass
    time.sleep(2)
else:
    print("Backend did not become ready within 120 seconds.")
    sys.exit(1)

# 2. Register admin user
print("Registering admin user...")
payload = {
    "email": "vedantj553@gmail.com",
    "password": "Vedant6927",
    "companyName": "Vedant's Company"
}
try:
    auth_resp = requests.post("http://localhost:8080/api/v1/auth/register", json=payload)
    print("Registration response:", auth_resp.status_code, auth_resp.text)
except Exception as e:
    print("Registration failed:", e)
    sys.exit(1)

# 3. Update database
print("Updating user in database...")
sql_command = "UPDATE users SET email_verified = true, subscription_status = 'MAX' WHERE email = 'vedantj553@gmail.com';"
docker_cmd = ["docker", "exec", "finance-postgres", "psql", "-U", "postgres", "-d", "financedb", "-c", sql_command]
result = subprocess.run(docker_cmd, capture_output=True, text=True)
print("DB Update STDOUT:", result.stdout)
print("DB Update STDERR:", result.stderr)
if result.returncode == 0:
    print("Successfully initialized admin user.")
else:
    print("Failed to initialize user in DB.")
    sys.exit(1)
