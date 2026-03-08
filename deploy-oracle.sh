#!/bin/bash

# Exit on error
set -e

echo "==========================================================="
echo "  FinanceAI - Oracle Ampere A1 (ARM64) Deployment Script   "
echo "==========================================================="

echo "[1/6] Updating system packages..."
sudo apt-get update -y && sudo apt-get upgrade -y

echo "[2/6] Installing Docker & Docker Compose..."
if ! command -v docker &> /dev/null; then
  # Install Docker using the official convenience script
  curl -fsSL https://get.docker.com -o get-docker.sh
  sudo sh get-docker.sh
  sudo usermod -aG docker $USER
  rm get-docker.sh
  echo "Docker installed successfully."
else
  echo "Docker is already installed."
fi

# Ensure the docker-compose-plugin is installed
sudo apt-get install -y docker-compose-plugin

echo "[3/6] Configuring OS-level iptables to open ports 80 & 443..."
# Oracle instances often ship with strict iptables rules on Canonical Ubuntu
# Note: If you are using Oracle Linux instead of Ubuntu, the command might involve firewalld instead
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT

# Attempt to save rules (specific to Ubuntu/Debian)
if command -v netfilter-persistent &> /dev/null; then
  sudo netfilter-persistent save
else
  echo "Warning: netfilter-persistent not found. You may need to persist iptables manually."
fi

echo "[4/6] Checking for repository files..."
if [ ! -f "docker-compose.yml" ]; then
  echo "Error: docker-compose.yml not found."
  echo "Please clone the repository first, 'cd' into it, and then run this script."
  exit 1
fi

echo "[5/6] Building Docker images locally for ARM64 architecture..."
# Building on the instance guarantees the images match the ARM64 architecture natively
sudo docker compose build backend frontend ai-service

echo "[6/6] Starting the application stack in the background..."
sudo docker compose up -d

echo "==========================================================="
echo " Deployment sequence finished! "
echo "==========================================================="
echo "Don't forget to:"
echo " 1. Create your production '.env' file in this directory."
echo " 2. Configure Ingress Rules in the Oracle Cloud Console (VCN -> Security List) to allow TCP 80 & 443."
echo " 3. Verify services are running with 'docker compose ps'."
echo "==========================================================="
