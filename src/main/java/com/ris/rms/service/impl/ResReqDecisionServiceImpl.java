package com.ris.rms.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Notification;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.ResourceRequestApproval;
import com.ris.rms.entity.ResourceRequestGroup;
import com.ris.rms.entity.ResourceRequestSkill;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.NotificationRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqApprovalRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.ResourceRequestSkillRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.ResReqDecisionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ResReqDecisionServiceImpl implements ResReqDecisionService {

	private final ResourceRequestRepository rrRepo;
	private final ProjectRepository projectRepo;

	private final ResReqApprovalRepository hrRepo;

	private final RoleRepository roleRepo;
	private final UserAccountRepository uaRepo;

	private final NotificationRepository notifRepo;

	private final ResReqGroupRepository groupRepo;
	private final ResourceRequestSkillRepository rrSkillRepo;
	private final SkillRepository skillRepo;
	private final DemandRepository demandRepo;
	private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Override
	public Map<String, Object> hrDecideGroup(Long groupId, Long approverUserId, String decision, String comments) {

		ResourceRequestGroup group = groupRepo.findById(groupId)
				.orElseThrow(() -> new IllegalArgumentException("Group not found"));
		Project project = projectRepo.findById(group.getProjectId())
				.orElseThrow(() -> new IllegalArgumentException("Project not found"));

		UserAccount approver = uaRepo.findById(approverUserId)
				.orElseThrow(() -> new IllegalArgumentException("Approver user not found"));
		if (!Objects.equals(approver.getCompanyId(), project.getCompanyId())) {
			throw new IllegalArgumentException("Approver must belong to the same company");
		}
		if (!userHasRoleAlias(approver)) {
			throw new IllegalArgumentException("Approver must be an HR user of the same company");
		}

		String normDecision = normalize(decision, List.of("Approved", "Rejected"));

		if ("Approved".equals(normDecision)) {

			group.setStatus("HRApproved");
			group.setHrApprovedAt(OffsetDateTime.now());
			groupRepo.save(group);

			Map<String, Object> tpl = group.getRequestTemplate();
			int count = group.getTotalRequested() == null ? 0 : group.getTotalRequested();
			if (count <= 0)
				throw new IllegalArgumentException("Group totalRequested must be > 0");

			List<Long> createdIds = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				ResourceRequest rr = new ResourceRequest();
				rr.setProjectId(group.getProjectId());
				rr.setRequesterUserId(group.getCreatedBy());
				rr.setNumberOfResources(1);
				rr.setExperienceRange(asText(tpl.get("experienceRange")));
				rr.setLocationType(asText(tpl.get("locationType")));
				rr.setWorkMode(asText(tpl.get("workMode")));
				rr.setLocation(asText(tpl.get("location")));
				rr.setPriority(asText(tpl.get("priority")));
				rr.setEstimatedCostTotal(asBigDecimal(tpl.get("estimatedCostTotal")));
				rr.setEstimatedCostPerResourceMonth(asBigDecimal(tpl.get("estimatedCostPerResourceMonth")));
				rr.setGroupId(group.getGroupId());

				rr.setStatus("Submitted");
				rr.setSubmittedDate(LocalDate.now());

				ResourceRequest saved = rrRepo.save(rr);
				createdIds.add(saved.getRequestId());

				@SuppressWarnings("unchecked")
				List<Integer> prim = (List<Integer>) tpl.get("primarySkillIds");
				@SuppressWarnings("unchecked")
				List<Integer> sec = (List<Integer>) tpl.get("secondarySkillIds");
				@SuppressWarnings("unchecked")
				List<Integer> legacy = (List<Integer>) tpl.get("skillIds");

				if (prim != null && !prim.isEmpty())
					linkSkillsByType(saved.getRequestId(), prim.stream().map(Long::valueOf).toList(), "Primary");
				if (sec != null && !sec.isEmpty())
					linkSkillsByType(saved.getRequestId(), sec.stream().map(Long::valueOf).toList(), "Secondary");
				if ((prim == null || prim.isEmpty()) && (sec == null || sec.isEmpty()) && legacy != null
						&& !legacy.isEmpty())
					linkSkillsByType(saved.getRequestId(), legacy.stream().map(Long::valueOf).toList(), "Primary");
			}

			if (group.getCreatedBy() != null) {
				notifyUser(group.getCreatedBy(), "HR Approved (Group)",
						"Your group #" + groupId + " was approved by HR and " + count + " requests were created.",
						"Normal", "ResourceRequestGroup", groupId);
			}

			return Map.of("groupId", groupId, "decision", normDecision, "groupStatus", group.getStatus(),
					"createdRequestIds", createdIds);

		} else {
			group.setStatus("Rejected");
			groupRepo.save(group);

			if (group.getCreatedBy() != null) {
				notifyUser(group.getCreatedBy(), "HR Rejected (Group)",
						"Your group #" + groupId + " was rejected by HR."
								+ (comments != null && !comments.isBlank() ? " Comments: " + comments : ""),
						"Normal", "ResourceRequestGroup", groupId);
			}

			return Map.of("groupId", groupId, "decision", normDecision, "groupStatus", group.getStatus());
		}
	}

	@Override
	public Map<String, Object> hrDecide(Long requestId, Long approverUserId, String decision, String comments) {

		ResourceRequest rr = rrRepo.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Resource request not found"));

		Long companyId = null;

		if (rr.getDemandId() != null) {
			companyId = demandRepo.findById(rr.getDemandId()).map(Demand::getCompanyId).orElse(null);
		} else if (rr.getProjectId() != null) {
			companyId = projectRepo.findById(rr.getProjectId()).map(Project::getCompanyId).orElse(null);
		}

		if (companyId == null && rr.getRequesterUserId() != null) {
			companyId = uaRepo.findById(rr.getRequesterUserId()).map(UserAccount::getCompanyId).orElse(null);
		}

		if (companyId == null) {
			throw new IllegalStateException("Cannot determine company context for this request. Request is orphaned.");
		}

		if (!"Submitted".equalsIgnoreCase(rr.getStatus())) {
			throw new IllegalArgumentException("HR can decide only on Submitted requests");
		}

		UserAccount approver = uaRepo.findById(approverUserId)
				.orElseThrow(() -> new IllegalArgumentException("Approver user not found"));

		if (!Objects.equals(approver.getCompanyId(), companyId)) {
			throw new IllegalArgumentException("Approver must belong to the same company");
		}
		if (!userHasRoleAlias(approver)) {
			throw new IllegalArgumentException("Approver must be an HR user of the same company");
		}

		String normDecision = normalize(decision, List.of("Approved", "Rejected"));

		ResourceRequestApproval a = new ResourceRequestApproval();
		a.setApprovalId(null);
		a.setRequestId(requestId);
		a.setApproverUserId(approverUserId);
		a.setApproverRole("HR");
		a.setStatus(normDecision);
		a.setComments(comments);
		a.setDecidedAt(OffsetDateTime.now());
		ResourceRequestApproval saved = hrRepo.save(a);

		rr.setStatus("Approved".equals(normDecision) ? "Approved" : "Rejected");
		rrRepo.save(rr);

		Long requesterUserId = rr.getRequesterUserId();
		if (requesterUserId != null) {
			notifyUser(requesterUserId, "HR " + normDecision,
					"Your resource request #" + requestId + " was " + normDecision + " by HR.", "Normal",
					"ResourceRequest", requestId);
		}

		notifyRoleByAliases(companyId, "HR " + normDecision,
				"Resource request #" + requestId + " is " + normDecision + " by HR.", "Normal", "ResourceRequest",
				requestId);

		return Map.of("hrApprovalId", saved.getApprovalId(), "requestId", requestId, "decision", normDecision, "status",
				rr.getStatus());
	}

	private static final Set<String> HR_ALIASES = Set.of("hr", "humanresources", "humanresource");

	private String normalize(String input, List<String> allowed) {
		if (input == null)
			throw new IllegalArgumentException("decision is required");
		for (String a : allowed)
			if (a.equalsIgnoreCase(input))
				return a;
		throw new IllegalArgumentException("decision must be one of " + allowed);
	}

	private static String normRole(String s) {
		return s == null ? null : s.replaceAll("[^A-Za-z]", "").toLowerCase();
	}

	private boolean userHasRoleAlias(UserAccount ua) {
		if (ua == null || ua.getRoleId() == null)
			return false;
		return roleRepo.findById(ua.getRoleId()).map(Role::getRoleName).map(ResReqDecisionServiceImpl::normRole)
				.map(HR_ALIASES::contains).orElse(false);
	}

	private List<UserAccount> usersByRoleAliases(Long companyId) {
		var roles = roleRepo.findAllByCompanyId(companyId).stream()
				.filter(r -> HR_ALIASES.contains(normRole(r.getRoleName()))).collect(Collectors.toList());

		if (!roles.isEmpty()) {
			var roleIds = roles.stream().map(Role::getRoleId).toList();
			return uaRepo.findAll().stream().filter(u -> Objects.equals(u.getCompanyId(), companyId))
					.filter(u -> u.getRoleId() != null && roleIds.contains(u.getRoleId())).toList();
		}

		return uaRepo.findAll().stream().filter(u -> Objects.equals(u.getCompanyId(), companyId))
				.filter(u -> userHasRoleAlias(u)).toList();
	}

	private void notifyRoleByAliases(Long companyId, String title, String message, String priority, String type,
			Long entityId) {
		List<UserAccount> users = usersByRoleAliases(companyId);
		for (UserAccount u : users) {
			notifyUser(u.getUserId(), title, message, priority, type, entityId);
		}
	}

	private void notifyUser(Long userId, String title, String message, String priority, String type, Long entityId) {
		Notification n = new Notification();
		n.setUserId(userId);
		n.setTitle(title);
		n.setMessage(message);
		n.setPriority(priority);
		n.setRelatedEntityType(type);
		n.setRelatedEntityId(entityId);
		notifRepo.save(n);
	}

	private Map<String, Object> readTemplate(String json) {
		try {
			if (json == null || json.isBlank())
				return Map.of();
			return om.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid request template on group");
		}
	}

	private void linkSkillsByType(Long requestId, List<Long> skillIds, String type) {
		if (skillIds == null || skillIds.isEmpty())
			return;
		for (Long sid : skillIds) {
			if (sid == null)
				continue;
			if (!skillRepo.existsById(sid))
				throw new IllegalArgumentException("Skill not found: " + sid);
			ResourceRequestSkill rs = new ResourceRequestSkill();
			rs.setRequestId(requestId);
			rs.setSkillId(sid);
			rs.setSkillType(type);
			rrSkillRepo.save(rs);
		}
	}

	private static String asText(Object o) {
		return o == null ? null : o.toString();
	}

	private static BigDecimal asBigDecimal(Object o) {
		if (o == null)
			return null;
		if (o instanceof BigDecimal b)
			return b;
		if (o instanceof Number n)
			return new BigDecimal(n.toString());
		try {
			return new BigDecimal(o.toString());
		} catch (Exception e) {
			return null;
		}
	}
}