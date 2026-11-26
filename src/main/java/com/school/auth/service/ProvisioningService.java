package com.school.auth.service;

import com.school.auth.dto.PlatformDto.*;
import com.school.auth.entity.BranchProvisioningEntity;
import com.school.auth.entity.OrganizationEntity;
import com.school.auth.entity.SchoolEntity;
import com.school.auth.repository.BranchProvisioningRepository;
import com.school.auth.repository.OrganizationRepository;
import com.school.auth.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * The onboarding wizard (Organization -> Branches -> Features -> Admins -> Review & go live) as one batch.
 * Each branch is provisioned in its own transaction and recorded in branch_provisioning, so one bad branch
 * is reported as FAILED without undoing the others.
 */
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final PlatformService platformService;
    private final RoleService roleService;
    private final MembershipService membershipService;
    private final BranchProvisioningRepository provisioningRepository;
    private final OrganizationRepository organizationRepository;
    private final SchoolRepository schoolRepository;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper jsonMapper;

    public ProvisioningResponse provision(Long actorUserId, ProvisioningRequest request) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long organizationId;
        if (request.organizationId() != null) {
            organizationId = platformService.findOrganization(request.organizationId()).getOrganizationId();
        } else if (request.organization() != null) {
            organizationId = tx.execute(s -> platformService.createOrganization(actorUserId, request.organization()).getOrganizationId());
        } else {
            throw new IllegalArgumentException("Give organizationId or a new organization.");
        }

        UUID batchId = UUID.randomUUID();
        List<String> inviteLinks = new ArrayList<>();
        List<BranchProvisioningEntity> rows = new ArrayList<>();
        for (ProvisionBranch item : request.branches()) {
            BranchProvisioningEntity row = tx.execute(s -> provisioningRepository.save(BranchProvisioningEntity.builder()
                    .batchId(batchId)
                    .organizationId(organizationId)
                    .templateSchoolId(item.branch().templateSchoolId())
                    .copyFeatures(item.branch().featureCodes() == null && item.branch().templateSchoolId() != null)
                    .copyCustomRoles(item.copyRoles())
                    .copyRoleMappings(item.copyMappings())
                    .payload(jsonMapper.convertValue(item, MAP))
                    .status("PROVISIONING")
                    .requestedByUserId(actorUserId)
                    .build()));
            try {
                List<String> links = tx.execute(s -> provisionBranch(actorUserId, organizationId, row.getProvisioningId(), item));
                inviteLinks.addAll(links);
            } catch (RuntimeException ex) {
                tx.executeWithoutResult(s -> provisioningRepository.findById(row.getProvisioningId()).ifPresent(r -> {
                    r.setStatus("FAILED");
                    r.setErrorMessage(rootMessage(ex));
                    r.setCompletedAt(OffsetDateTime.now());
                }));
            }
            rows.add(row);
        }

        List<BranchProvisioningEntity> result = provisioningRepository.findByBatchIdOrderByProvisioningId(batchId);
        boolean allOk = result.stream().allMatch(r -> "COMPLETED".equals(r.getStatus()));
        if (request.goLiveRequested() && allOk) {
            tx.executeWithoutResult(s -> goLive(organizationId, result));
        }
        return new ProvisioningResponse(batchId, organizationId,
                provisioningRepository.findByBatchIdOrderByProvisioningId(batchId).stream().map(ProvisioningRowResponse::from).toList(),
                inviteLinks);
    }

    @Transactional(readOnly = true)
    public List<ProvisioningRowResponse> batch(UUID batchId) {
        List<BranchProvisioningEntity> rows = provisioningRepository.findByBatchIdOrderByProvisioningId(batchId);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Provisioning batch not found: " + batchId);
        }
        return rows.stream().map(ProvisioningRowResponse::from).toList();
    }

    private List<String> provisionBranch(Long actorUserId, Long organizationId, Long provisioningId, ProvisionBranch item) {
        SchoolEntity school = platformService.createBranch(organizationId, item.branch());
        if (item.branch().templateSchoolId() != null && (item.copyRoles() || item.copyMappings())) {
            roleService.copyRoles(item.branch().templateSchoolId(), school.getSchoolId(),
                    item.copyRoles(), item.copyMappings());
        }
        List<String> links = new ArrayList<>();
        if (item.adminInvites() != null) {
            item.adminInvites().forEach((role, emails) -> emails.forEach(email -> links.add(
                    membershipService.inviteToSchool(actorUserId, true, school.getSchoolId(), email, role).inviteLink())));
        }
        BranchProvisioningEntity row = provisioningRepository.findById(provisioningId).orElseThrow();
        row.setSchoolId(school.getSchoolId());
        row.setStatus("COMPLETED");
        row.setCompletedAt(OffsetDateTime.now());
        return links;
    }

    private void goLive(Long organizationId, List<BranchProvisioningEntity> rows) {
        OrganizationEntity org = organizationRepository.findById(organizationId).orElseThrow();
        org.setStatus(OrganizationEntity.STATUS_ACTIVE);
        rows.forEach(r -> schoolRepository.findById(r.getSchoolId()).ifPresent(s -> s.setStatus(SchoolEntity.STATUS_ACTIVE)));
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
