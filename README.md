# PoyrazK8s Installation Guide

This document explains step-by-step how to set up the PoyrazK8s project using Docker Compose and Kubernetes agents.

---

## 1. Clone the Repository

```bash
git clone https://github.com/capta1nBee/poyrazk8s.git
cd poyrazk8s/deploy/compose
```

---

## 2. Port and Configuration Settings

### Port Change

You can modify ports in the compose file:

```yaml
- "30025:30025"
```

You also need to update the following file:

* `frontend/nginx.conf`

---

### Network Policy Configuration

To define ingress and egress rules, you can customize the selectors:

```env
POLICY_LABELS=app,uygulama,deploy,project
```

---

## 3. Start Services with Docker Compose

```bash
docker compose up -d
```

---

## 4. Network Agent Setup

> Note: Tested on Kernel 5.8 and above.

Apply the agent:

```bash
kubectl apply -f https://raw.githubusercontent.com/capta1nBee/poyrazk8s/main/deploy/k8s/network-agent.yaml
```

### Important Settings

* Do not forget to update the backend IP address:

```yaml
http_endpoint: "BACKEND_IP:8686"
```

* Adjust `exclude_ips` and `exclude_port` fields according to your environment.

### Recommendations

* **If using Flannel:**

  * CIDR: `10.244.0.0/16`
  * Exclude IPs ending with `.0` and `.1`

* **If using Cilium:**

  * Exclude the `cilium_host` interface IP

---

## 5. Security Agent Setup

> Note: Tested on Kernel 5.8 and above.

### Important Settings

* Update backend base URL:

```yaml
BACKEND__BASE_URL: "http://BACKEND_IP:8686"
```

* Ensure API key matches the one used in Docker Compose:

```yaml
BACKEND__AGENT_API_KEY: "<YOUR_API_KEY>"
```

### Installation

```bash
kubectl apply -f https://raw.githubusercontent.com/capta1nBee/poyrazk8s/main/deploy/k8s/security-agent.yaml
```

---

## Notes

* Ensure your kernel version is **5.8 or higher**.
* Update all IP addresses and ports according to your environment.
* Make sure agents can communicate with the backend service.

---

## Done 
