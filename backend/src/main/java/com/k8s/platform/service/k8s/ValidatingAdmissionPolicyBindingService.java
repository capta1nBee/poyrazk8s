package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.ValidatingAdmissionPolicyBinding;
import com.k8s.platform.domain.repository.k8s.ValidatingAdmissionPolicyBindingRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidatingAdmissionPolicyBindingService {

    private final ValidatingAdmissionPolicyBindingRepository repository;
    private final K8sClientService k8sClientService;

    public List<ValidatingAdmissionPolicyBinding> listValidatingAdmissionPolicyBindings(Long clusterId) {
        return repository.findByClusterIdAndIsDeletedFalse(clusterId);
    }

    public ValidatingAdmissionPolicyBinding getValidatingAdmissionPolicyBinding(Long clusterId, String name) {
        return repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .orElseThrow(() -> new RuntimeException("ValidatingAdmissionPolicyBinding not found"));
    }

    @Transactional
    public void deleteValidatingAdmissionPolicyBinding(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings().withName(name).delete();

        repository.findByClusterIdAndNameAndIsDeletedFalse(clusterId, name)
                .ifPresent(r -> {
                    r.setIsDeleted(true);
                    repository.save(r);
                });
    }

    public String getYaml(Long clusterId, String name) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding resource = client
                .admissionRegistration().v1beta1().validatingAdmissionPolicyBindings().withName(name).get();
        return k8sClientService.serializeToYaml(resource);
    }

    public void updateYaml(Long clusterId, String name, String yaml) {
        KubernetesClient client = k8sClientService.getClient(clusterId);
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding resource = k8sClientService
                .deserializeFromYaml(yaml,
                        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding.class);
        client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings().resource(resource).update();
    }
}
