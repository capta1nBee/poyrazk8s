package com.k8s.platform.config.casbin;

import com.k8s.platform.domain.entity.casbin.CasbinRule;
import com.k8s.platform.domain.repository.casbin.CasbinRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.Helper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Casbin Adapter backed by Spring Data JPA (H2 database).
 * Stores policies in the casbin_rule table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaAdapter implements Adapter {

    private final CasbinRuleRepository repository;

    @Override
    public void loadPolicy(Model model) {
        List<CasbinRule> rules = repository.findAll();
        for (CasbinRule rule : rules) {
            String line = toLine(rule);
            if (line != null && !line.isBlank()) {
                Helper.loadPolicyLine(line, model);
            }
        }
        log.debug("Casbin: loaded {} rules from DB", rules.size());
    }

    @Override
    public void savePolicy(Model model) {
        repository.deleteAll();
        model.model.forEach((ptype, sectionMap) ->
            sectionMap.forEach((key, assertion) ->
                assertion.policy.forEach(rule -> {
                    CasbinRule entity = fromRule(ptype, rule);
                    repository.save(entity);
                })
            )
        );
    }

    @Override
    public void addPolicy(String sec, String ptype, List<String> rule) {
        CasbinRule entity = fromRule(ptype, rule);
        repository.save(entity);
        log.debug("Casbin: added policy {} {}", ptype, rule);
    }

    @Override
    public void removePolicy(String sec, String ptype, List<String> rule) {
        List<CasbinRule> existing = repository.findByPtype(ptype);
        for (CasbinRule r : existing) {
            if (ruleMatches(r, rule)) {
                repository.delete(r);
                log.debug("Casbin: removed policy {} {}", ptype, rule);
                return;
            }
        }
    }

    @Override
    public void removeFilteredPolicy(String sec, String ptype, int fieldIndex, String... fieldValues) {
        List<CasbinRule> rules = repository.findByPtype(ptype);
        for (CasbinRule r : rules) {
            List<String> ruleList = toList(r);
            boolean match = true;
            for (int i = 0; i < fieldValues.length; i++) {
                String fv = fieldValues[i];
                if (fv != null && !fv.isEmpty()) {
                    int idx = fieldIndex + i;
                    if (idx < ruleList.size() && !ruleList.get(idx).equals(fv)) {
                        match = false;
                        break;
                    }
                }
            }
            if (match) repository.delete(r);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String toLine(CasbinRule r) {
        StringBuilder sb = new StringBuilder(r.getPtype());
        if (r.getV0() != null) sb.append(", ").append(r.getV0());
        if (r.getV1() != null) sb.append(", ").append(r.getV1());
        if (r.getV2() != null) sb.append(", ").append(r.getV2());
        if (r.getV3() != null) sb.append(", ").append(r.getV3());
        if (r.getV4() != null) sb.append(", ").append(r.getV4());
        if (r.getV5() != null) sb.append(", ").append(r.getV5());
        return sb.toString();
    }

    private CasbinRule fromRule(String ptype, List<String> rule) {
        return CasbinRule.builder()
                .ptype(ptype)
                .v0(get(rule, 0))
                .v1(get(rule, 1))
                .v2(get(rule, 2))
                .v3(get(rule, 3))
                .v4(get(rule, 4))
                .v5(get(rule, 5))
                .build();
    }

    private List<String> toList(CasbinRule r) {
        return List.of(
                r.getV0() != null ? r.getV0() : "",
                r.getV1() != null ? r.getV1() : "",
                r.getV2() != null ? r.getV2() : "",
                r.getV3() != null ? r.getV3() : "",
                r.getV4() != null ? r.getV4() : "",
                r.getV5() != null ? r.getV5() : "");
    }

    private boolean ruleMatches(CasbinRule r, List<String> rule) {
        List<String> rList = toList(r);
        for (int i = 0; i < rule.size(); i++) {
            if (i >= rList.size() || !rule.get(i).equals(rList.get(i))) return false;
        }
        return true;
    }

    private String get(List<String> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }
}
