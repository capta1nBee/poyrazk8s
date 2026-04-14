package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.CertificateSigningRequest;
import com.k8s.platform.domain.repository.k8s.CertificateSigningRequestRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateSigningRequestService {

    private final CertificateSigningRequestRepository repository;
    private final K8sClientService k8sClientService;

    public List<CertificateSigningRequest> listCertificateSigningRequests(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public CertificateSigningRequest getCertificateSigningRequest(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("CertificateSigningRequest not found"));
    }

    @Transactional
    public void deleteCertificateSigningRequest(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.certificates().v1().certificateSigningRequests().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest resource = client.certificates().v1()
                .certificateSigningRequests().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest resource = k8sClientService
                .deserializeFromYaml(yaml,
                        io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest.class);
        client.certificates().v1().certificateSigningRequests().resource(resource).update();
    }
}
