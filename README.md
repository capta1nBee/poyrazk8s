<p align="center">
  <img src="images/logo.svg" alt="PoyrazK8s Logo" width="240">
</p>

<h1 align="center">PoyrazK8s</h1>

<p align="center">
  <strong>Kubernetes Security & Orchestration Workspace</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kubernetes-v1.20%2B-blue?style=for-the-badge&logo=kubernetes" alt="K8s Support">
  <img src="https://img.shields.io/badge/Security-eBPF-green?style=for-the-badge&logo=linux" alt="eBPF Powered">
  <img src="https://img.shields.io/badge/Architecture-Distributed-orange?style=for-the-badge" alt="Distributed Architecture">
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License MIT">
</p>

---

## Strategic Advantage

**PoyrazK8s** is designed for organizations requiring deep visibility into Kubernetes infrastructures. Leveraging **eBPF (Extended Berkeley Packet Filter)**, **PoyrazK8s** delivers kernel-level observability , bridging the gap between infrastructure management and advanced threat detection.

---

## System Architecture

PoyrazK8s utilizes a high-performance, distributed architecture to ensure scalability across multi-node clusters.

```mermaid
graph TD
    subgraph "Kubernetes Cluster"
        NA[Network Agent] -->|TRACING| PK[Pods/Services]
        SA[Security Agent - eBPF] -->|ENFORCEMENT| PK
        CE[ClusterEye] -->|SCANNING| PK
    end

    subgraph "Control Plane (Docker Compose)"
        BE[Backend API]
        DB[(PostgreSQL)]
        FE[Enterprise Dashboard]
    end

    NA -.->|HTTP| BE
    SA -.->|Events/Logs| BE
    BE <--> DB
    FE <--> BE
    
    style SA fill:#f96,stroke:#333,stroke-width:2px
    style BE fill:#69f,stroke:#333,stroke-width:2px
    style FE fill:#6f9,stroke:#333,stroke-width:2px
```

---

## Enterprise Capability Matrix

| Capability | Feature | Enterprise Value |
| :--- | :--- | :--- |
| **Observability** | Multi-Cluster Dashboard | Real-time health and resource visualization across the estate. |
| **Security** | eBPF Runtime Protection | Kernel-level detection of unauthorized executions and syscalls. |
| **Network** | L3/L4 Topology Mapping | Visual traffic flow analysis to identify bottlenecks and breaches. |
| **Governance** | RBAC Command Governance | Granular control over terminal access and command execution. |
| **App Creator** | Low-Code App Creator | Accelerated deployment via standardized enterprise templates. |
| **Compliance** | Automated Vulnerability Scanning | Security posture of images from designated registries is continuously assessed. |

---

## Feature Walkthrough

### Enterprise Dashboard & Multi-Cluster Overlook
Centrally manage and monitor your entire Kubernetes fleet. Gain immediate insights into cluster health, resource utilization, and critical events.

<p align="center">
  <img src="images/dashboard.png" width="900">
</p>

---

### App Creator: 8-Step Deployment Excellence
Our intuitive wizard simplifies complex deployments from Git-to-Cluster, ensuring standardized resource allocation and persistence.

#### The 8-Step Wizard Workflow
<p align="center">
  <img src="images/app-creator-build-remote.png" width="400"> 
</p>

**Key Capabilities:**
- **Build from Git:** Integrated CI/CD that builds images directly from your repository.
- **Helm Discovery:** Intelligent searching and deployment of Helm charts.
- **Yaml Backup Scheduler:** Full cluster resource persistence and recovery.

|  Helm Discovery | Backup Scheduler |
|:---:|:---:|
| <img src="images/helm-discover.png" width="400"> | <img src="images/backup.png" width="400"> |

---

### Network Intelligence & Topology Forensics
Visualize your cluster's traffic flow and secure your perimeter with automated network policy generation and multi-cluster federation.

| Federation Management | Real-time Topology | Automated Firewall Rules |
|:---:|:---:|:---:|
| <img src="images/federation-2.png" width="300"> | <img src="images/topologcy.png" width="300"> | <img src="images/policy.png" width="300"> |

---

### RBAC Command Governance & Pod Session Record
Secure your runtime environment with kernel-level execution control and comprehensive session auditing.

| ClusterEye Security | Vulnerability Inventory | RBAC Command Governance |
|:---:|:---:|:---:|
| <img src="images/cluster-eye-1.png" width="300"> | <img src="images/vuln-scanner-1.png" width="300"> | <img src="images/exec-allow.png" width="300"> |

#### Pod Session Record Walkthrough
Detailed recording and auditing of all terminal interactions and automated command execution events.

![Pod Session Record Demo](images/exec.gif)

---
### Observability & Metric API Integration
### Detect and analyze objects dependent on ConfigMap & Secret modifications

---

## Strategic Roadmap

- [ ] **AI-Driven Infrastructure Baselining:** Machine-learning behavior modeling for alerting.
- [ ] **L7 Protocol Observability:** Deep-packet inspection 


---

## Getting Started

To begin your enterprise deployment of PoyrazK8s, please consult our comprehensive setup guide:

**[Launch Installation Guide](INSTALLATION.md)**

---

##  License & Support
PoyrazK8s is open-source software licensed under the **MIT License**.

<p align="right">
  Developed with focus by <a href="https://github.com/capta1nBee"><strong>capta1nBee</strong></a>
</p>
