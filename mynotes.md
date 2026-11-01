# Java Auth Service – Setup Notes

## Overview

Running this app requires **two instances**:

| # | Instance | Purpose |
|---|----------|---------|
| 1 | Runner | Executes CI jobs. A GitHub-hosted runner can be used instead, because self-hosted runners are a bit slow. |
| 2 | SonarQube server | Runs the Sonar server for code analysis. |

---

## A) Set up the SonarQube server

### 1. Security group (SG) rules

| Port | Purpose |
|------|---------|
| `22` | SSH access |
| `9000` | SonarQube UI (e.g. `http://3.89.66.178:9000/`) |

### 2. Install dependencies

```bash
sudo apt update
```

Install Docker by following the [official Ubuntu guide](https://docs.docker.com/engine/install/ubuntu).

### 3. Allow your user to run Docker

```bash
sudo usermod -aG docker $USER
```

> Log out and back in for the group change to take effect.

---

## B) Set up the runner

1. In GitHub, go to **Settings → Actions → Runners** and select **Linux**.
2. Install Docker by following the [official Ubuntu guide](https://docs.docker.com/engine/install/ubuntu).

---

## C) Future improvements

- [ ] Set up the GitHub Actions workflow
- [ ] Set up ECR
- [ ] Set up EKS
- [ ] Run the app on Kubernetes using YAML manifests
