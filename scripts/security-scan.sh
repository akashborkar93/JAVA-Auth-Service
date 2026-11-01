#!/usr/bin/env bash
# Runs the full local pipeline: build + tests, Trivy scans, container build, image scan, SonarQube.
# Mirrors CI: the jar is built once, copied to dist/, and the image is built from that jar.
# Usage: scripts/security-scan.sh            (set SONAR_HOST_URL and SONAR_TOKEN to enable SonarQube)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

step() { printf '\n==> %s\n' "$*"; }

step "1/4 Build, tests and coverage"
./gradlew clean build --no-daemon

step "2/4 Trivy filesystem scan (vulnerabilities, secrets, misconfigurations)"
trivy fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed \
  --skip-dirs build --skip-dirs .gradle --skip-dirs dist --exit-code 1 .

step "3/4 Container image and Trivy image scan"
mkdir -p dist
cp auth-service/build/libs/app.jar dist/app.jar
docker build -f auth-service/Dockerfile -t secure-auth/auth-service:local .
trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 secure-auth/auth-service:local

step "4/4 SonarQube analysis"
if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "SONAR_TOKEN not set; skipping SonarQube. Set SONAR_HOST_URL and SONAR_TOKEN for your SonarQube server."
else
  ./gradlew sonar --no-daemon -Dsonar.qualitygate.wait=true
fi

step "All scans passed."
