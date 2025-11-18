package com.ris.rms.service.impl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ris.rms.dto.InterviewDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.entity.Interview;
import com.ris.rms.entity.Notification;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.InterviewFeedbackRepository;
import com.ris.rms.repository.InterviewRepository;
import com.ris.rms.repository.NotificationRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.InterviewService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InterviewServiceImpl implements InterviewService {

	private final InterviewRepository repo;
	private final InterviewFeedbackRepository feedbackRepo;
	private final EmailService emailService;
	private final ResourceRequestRepository rrRepo;
	private final ProjectRepository projectRepo;
	private final AccountRepository accountRepo;
	private final CompanyRepository companyRepo;
	private final EmployeeRepository employeeRepo;
	private final UserAccountRepository userAccountRepo;
	private final NotificationRepository notificationRepo;
	private final DemandRepository demandRepo;
	private final ResReqGroupRepository groupRepo;

	private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	private static final Set<String> ALLOWED_LEVELS = Set.of("L1", "L2", "L3", "HR", "Managerial");
	private static final java.time.format.DateTimeFormatter OUT_FMT = java.time.format.DateTimeFormatter
			.ofPattern("dd-MM-uuuu HH-mm");
	private static final java.time.format.DateTimeFormatter IN_FMT_DASH = java.time.format.DateTimeFormatter
			.ofPattern("dd-MM-uuuu HH-mm");
	private static final java.time.format.DateTimeFormatter IN_FMT_COLON = java.time.format.DateTimeFormatter
			.ofPattern("dd-MM-uuuu HH:mm");
	private static final String META_LEVEL = "__META__";

	@Override
	public InterviewDto createBatchNoInterviewer(InterviewDto dto) {
		validateCreate(dto);
		requireEmployee(dto.getEmployeeId());
		ResourceRequest rr = requireRequest(dto.getRequestId());
		Project proj = rr.getProjectId() != null ? requireProject(rr.getProjectId()) : null;
		requireAllowedLevels(dto.getInterviewLevels());

		OffsetDateTime when = resolveScheduledAt(dto);
		List<String> plannedLevels = dto.getInterviewLevels() == null ? List.of() : dto.getInterviewLevels();
		List<LevelProgressDto> progress = buildProgress(plannedLevels, dto.getLevelProgress(), dto.getCreatedByUserId(),
				false);

		Interview entity = initInterviewEntity(dto, when);
		entity.setPlannedLevels(plannedLevels);
		entity.setLevelProgress(writeProgress(progress));

		Interview saved = repo.save(entity);

		String accountName = proj != null && proj.getAccountId() != null
				? accountRepo.findById(proj.getAccountId()).map(a -> a.getAccountName()).orElse(null)
				: null;

		notifyOnCreateOrUpdateEmails(saved, proj, accountName, EmailService.MailAction.CREATED, progress);
		return enrich(buildCreateOrUpdateResponse(saved));
	}

	@Override
	public InterviewDto create(InterviewDto dto) {
		validateCreate(dto);
		requireEmployee(dto.getEmployeeId());
		ResourceRequest rr = requireRequest(dto.getRequestId());
		Project proj = rr.getProjectId() != null ? requireProject(rr.getProjectId()) : null;

		if (dto.getInterviewerUserId() != null)
			requireUser(dto.getInterviewerUserId());
		if (dto.getLevelProgress() != null)
			for (LevelProgressDto lp : dto.getLevelProgress())
				if (lp != null && lp.getInterviewerUserId() != null)
					requireUser(lp.getInterviewerUserId());

		requireAllowedLevels(dto.getInterviewLevels());
		String accountName = null;
		if (proj != null && proj.getAccountId() != null) {
		    accountName = accountRepo.findById(proj.getAccountId()).map(a -> a.getAccountName()).orElse(null);
		} else if (rr.getDemandId() != null) {
		    accountName = demandRepo.findById(rr.getDemandId())
		            .flatMap(d -> d.getAccountId() == null ? Optional.empty() : accountRepo.findById(d.getAccountId()))
		            .map(a -> a.getAccountName())
		            .orElse(null);
		}


		OffsetDateTime when = resolveScheduledAt(dto);
		List<LevelProgressDto> progress = buildProgress(dto.getInterviewLevels(), dto.getLevelProgress(),
				dto.getInterviewerUserId(), true);

		Interview entity = initInterviewEntity(dto, when);
		entity.setPlannedLevels(dto.getInterviewLevels());
		entity.setLevelProgress(writeProgress(progress));

		Interview saved = repo.save(entity);

		notifyOnCreateOrUpdateEmails(saved, proj, accountName, EmailService.MailAction.CREATED, progress);
		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
	}

	@Override
	@Transactional(readOnly = true)
	public InterviewDto getById(Long id) {
		Interview iv = requireInterview(id);
		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(iv)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<InterviewDto> list(Long requestId, Long employeeId, String status, String interviewType, Integer page,
			Integer size) {

		if (requestId != null && !rrRepo.existsById(requestId))
			throw new IllegalArgumentException("Request not found: " + requestId);
		if (employeeId != null && !employeeRepo.existsById(employeeId))
			throw new IllegalArgumentException("Employee not found: " + employeeId);

		List<Interview> base = (page != null && size != null && page >= 0 && size > 0)
				? repo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "interviewId", "scheduledAt")))
						.getContent()
				: repo.findAll().stream().sorted(Comparator
						.comparing(Interview::getInterviewId, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Interview::getScheduledAt, Comparator.nullsLast(Comparator.reverseOrder())))
						.toList();

		String sl = status == null ? null : status.toLowerCase();
		String tl = interviewType == null ? null : interviewType.toLowerCase();

		return base.stream().filter(i -> requestId == null || Objects.equals(i.getRequestId(), requestId))
				.filter(i -> employeeId == null || Objects.equals(i.getEmployeeId(), employeeId))
				.filter(i -> sl == null || (i.getStatus() != null && i.getStatus().toLowerCase().contains(sl)))
				.filter(i -> tl == null
						|| (i.getInterviewType() != null && i.getInterviewType().toLowerCase().contains(tl)))
				.map(this::buildCreateOrUpdateResponse).map(this::enrich).map(this::sanitizeForClient)
				.collect(Collectors.toList());
	}

	@Override
	public InterviewDto updateWithRequestCheck(Long interviewId, Long requestId, InterviewDto dto) {
		Interview existing = requireInterview(interviewId);
		requireSameRequest(existing, requestId);

		if (dto.getLevelProgress() != null)
			for (LevelProgressDto in : dto.getLevelProgress())
				if (in != null && in.getInterviewerUserId() != null)
					requireUser(in.getInterviewerUserId());

		requireAllowedLevels(dto.getInterviewLevels());

		OffsetDateTime when = resolveScheduledAt(dto);
		if (when != null)
			existing.setScheduledAt(when);

		if (StringUtils.hasText(dto.getFeedback())) {
			String prev = existing.getNotes();
			existing.setNotes(prev == null || prev.isBlank() ? dto.getFeedback() : (prev + " | " + dto.getFeedback()));
		}

		List<LevelProgressDto> progress = readProgress(existing.getLevelProgress());

		if (dto.getInterviewLevels() != null) {
			for (String lvl : dto.getInterviewLevels())
				if (!ALLOWED_LEVELS.contains(lvl))
					throw new IllegalArgumentException("Invalid interview level: " + lvl);

			existing.setPlannedLevels(dto.getInterviewLevels());

			for (String lvl : dto.getInterviewLevels())
				if (findRow(progress, lvl).isEmpty()) {
					LevelProgressDto r = new LevelProgressDto();
					r.setLevel(lvl);
					r.setStatus("Planned");
					progress.add(r);
				}
		}

		if (dto.getLevelProgress() != null && !dto.getLevelProgress().isEmpty()) {
			for (LevelProgressDto in : dto.getLevelProgress()) {
				if (in.getLevel() == null)
					continue;
				if (!ALLOWED_LEVELS.contains(in.getLevel()))
					throw new IllegalArgumentException("Invalid interview level: " + in.getLevel());

				LevelProgressDto row = findRow(progress, in.getLevel()).orElseGet(() -> {
					LevelProgressDto r = new LevelProgressDto();
					r.setLevel(in.getLevel());
					r.setStatus("Planned");
					progress.add(r);
					return r;
				});

				if (in.getInterviewerUserId() != null)
					row.setInterviewerUserId(in.getInterviewerUserId());

				if (StringUtils.hasText(in.getScheduledAtText())) {
					row.setScheduledAtText(in.getScheduledAtText());
					if (!"Selected".equalsIgnoreCase(row.getStatus()) && !"Rejected".equalsIgnoreCase(row.getStatus()))
						row.setStatus("Scheduled");
				}

				if (StringUtils.hasText(in.getInterviewNotes()))
					row.setInterviewNotes(in.getInterviewNotes());
			}
		}

		if (dto.getRescheduleLevels() != null && !dto.getRescheduleLevels().isEmpty()) {
			for (String lvl : new LinkedHashSet<>(dto.getRescheduleLevels())) {
				if (!ALLOWED_LEVELS.contains(lvl))
					throw new IllegalArgumentException("Invalid interview level: " + lvl);

				LevelProgressDto row = findRow(progress, lvl).orElseGet(() -> {
					LevelProgressDto r = new LevelProgressDto();
					r.setLevel(lvl);
					r.setStatus("Planned");
					progress.add(r);
					return r;
				});

				row.setStatus("Rescheduled");
				row.setCompletedAt(null);

				if (StringUtils.hasText(dto.getFeedback())) {
					String prev = row.getFeedback();
					row.setFeedback(
							prev == null || prev.isBlank() ? dto.getFeedback() : (prev + " | " + dto.getFeedback()));
				}
			}
		}

		existing.setLevelProgress(writeProgress(progress));
		if (!"Scheduled".equalsIgnoreCase(existing.getStatus()))
			existing.setStatus("Scheduled");

		Interview saved = repo.save(existing);
		ResourceRequest rr = requireRequest(requestId);
		Project proj = rr.getProjectId() != null ? requireProject(rr.getProjectId()) : null;

		String accountName = null;
		if (proj != null && proj.getAccountId() != null) {
			accountName = accountRepo.findById(proj.getAccountId()).map(a -> a.getAccountName()).orElse(null);
		} else if (rr.getDemandId() != null) {
			accountName = demandRepo.findById(rr.getDemandId())
					.flatMap(d -> d.getAccountId() == null ? Optional.empty() : accountRepo.findById(d.getAccountId()))
					.map(a -> a.getAccountName()).orElse(null);
		}

		notifyOnCreateOrUpdateEmails(saved, proj, accountName, EmailService.MailAction.UPDATED,
				readProgress(saved.getLevelProgress()));

		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
	}

	@Override
	public InterviewDto cancel(Long interviewId, Long requestId, String reason) {
		Interview iv = requireInterview(interviewId);
		requireSameRequest(iv, requestId);

		iv.setStatus("Cancelled");
		if (StringUtils.hasText(reason))
			iv.setNotes((iv.getNotes() == null ? "" : iv.getNotes() + " | ") + "Cancelled: " + reason);

		Interview saved = repo.save(iv);

		rrRepo.findById(requestId).ifPresent(r -> {
			if (r.getRequesterUserId() != null)
				notifyUser(
						r.getRequesterUserId(), "Interview Cancelled", "Interview #" + saved.getInterviewId()
								+ " cancelled for your request #" + r.getRequestId() + ".",
						"Normal", "Interview", saved.getInterviewId());
		});

		try {
			ResourceRequest rr = requireRequest(requestId);

			Project proj = rr.getProjectId() != null ? requireProject(rr.getProjectId()) : null;

			String accountName = (proj != null && proj.getAccountId() != null)
					? accountRepo.findById(proj.getAccountId()).map(a -> a.getAccountName()).orElse(null)
					: null;

			String projectName = (proj != null) ? proj.getProjectName() : null;

			if (projectName == null && rr.getDemandId() != null) {
				projectName = demandRepo.findById(rr.getDemandId()).map(d -> d.getProjectName()).orElse(null);
			}

			List<LevelProgressDto> progress = readProgress(saved.getLevelProgress());
			String typeDisplay = deriveTypeFromLevels(progress);
			String whenRaw = fmt(saved.getScheduledAt());
			if (whenRaw == null || whenRaw.isBlank())
				whenRaw = deriveFirstTimeFromLevels(progress);

			String employeeEmail = null, employeeName = null;
			if (saved.getEmployeeId() != null) {
				var emp = employeeRepo.findById(saved.getEmployeeId()).orElse(null);
				if (emp != null) {
					employeeName = (emp.getFirstName() + " " + emp.getLastName()).trim();
					employeeEmail = (emp.getEmail() != null && !emp.getEmail().isBlank()) ? emp.getEmail()
							: userAccountRepo.findByEmployeeId(emp.getEmployeeId()).map(ua -> ua.getEmail())
									.orElse(null);
				}
			}

			CompletableFuture<Boolean> fEmp = (employeeEmail != null && !employeeEmail.isBlank())
					? emailService.sendEmployeeInterviewMailAsync(employeeEmail, projectName, accountName, // Use
																											// variable
							(employeeName != null ? employeeName : "Team Member"), typeDisplay, whenRaw,
							EmailService.MailAction.CANCELLED)
					: CompletableFuture.completedFuture(true);

			LinkedHashSet<Long> uniqueInterviewers = new LinkedHashSet<>();
			for (LevelProgressDto r : progress)
				if (r.getInterviewerUserId() != null)
					uniqueInterviewers.add(r.getInterviewerUserId());

			List<CompletableFuture<Boolean>> futures = new ArrayList<>();
			for (Long uid : uniqueInterviewers) {
				UserAccount intr = userAccountRepo.findById(uid).orElse(null);
				if (intr == null || intr.getEmail() == null || intr.getEmail().isBlank())
					continue;

				String interviewerName = resolveInterviewerDisplayName(intr);
				String candName = (employeeName != null ? employeeName : "Employee");

				futures.add(emailService.sendInterviewerNotificationMailAsync(intr.getEmail(), projectName, // Use
																											// variable
						accountName, interviewerName, candName, typeDisplay, whenRaw,
						EmailService.MailAction.CANCELLED));
			}

			CompletableFuture.allOf(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)), fEmp).join();
		} catch (Exception e) {
		}

		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
	}

	@Override
	public InterviewDto noShow(Long interviewId, Long requestId, String who, String feedback, List<String> levels) {
		Interview iv = requireInterview(interviewId);
		requireSameRequest(iv, requestId);

		String msg = "No-show: " + (StringUtils.hasText(who) ? who : "Unknown");
		if (StringUtils.hasText(feedback))
			msg += " (" + feedback + ")";

		iv.setNotes((iv.getNotes() == null ? "" : iv.getNotes() + " | ") + msg);

		if (levels != null && !levels.isEmpty()) {
			for (String lvl : levels)
				if (!ALLOWED_LEVELS.contains(lvl))
					throw new IllegalArgumentException("Invalid interview level: " + lvl);

			List<LevelProgressDto> progress = readProgress(iv.getLevelProgress());

			for (String lvl : new LinkedHashSet<>(levels)) {
				LevelProgressDto row = findRow(progress, lvl).orElseGet(() -> {
					LevelProgressDto r = new LevelProgressDto();
					r.setLevel(lvl);
					r.setStatus("Planned");
					progress.add(r);
					return r;
				});

				row.setStatus("NoShow");
				if (StringUtils.hasText(feedback)) {
					String prev = row.getFeedback();
					row.setFeedback(prev == null || prev.isBlank() ? feedback : (prev + " | " + feedback));
				}
			}
			iv.setLevelProgress(writeProgress(progress));
		}

		iv.setStatus("NoShow");
		Interview saved = repo.save(iv);
		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("Interview not found");
		feedbackRepo.deleteByInterviewId(id);
		repo.deleteById(id);
	}

	@Override
	public InterviewDto updateOnboarding(Long interviewId, String status, String note) {
		if (interviewId == null)
			throw new IllegalArgumentException("interviewId is required");
		if (!StringUtils.hasText(status))
			throw new IllegalArgumentException("status is required");

		Interview iv = requireInterview(interviewId);
		List<LevelProgressDto> progress = readProgress(iv.getLevelProgress());
		String overall = computeOverall(progress);

		if (!"Selected".equalsIgnoreCase(overall))
			throw new IllegalArgumentException("Onboarding allowed only when overall is 'Interview Selected'");

		LevelProgressDto ev = findRow(progress, "ONBOARDING").orElseGet(() -> {
			LevelProgressDto newLevel = new LevelProgressDto();
			newLevel.setLevel("ONBOARDING");
			progress.add(newLevel);
			return newLevel;
		});

		ev.setStatus(status);
		if (StringUtils.hasText(note))
			ev.setFeedback(note);

		OffsetDateTime now = OffsetDateTime.now();
		ev.setCompletedAt(now);
		ev.setCompletedAtText(fmt(now));

		iv.setLevelProgress(writeProgress(progress));
		Interview saved = repo.save(iv);

		InterviewDto out = sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
		out.setOnboardingStatus(status);
		return out;
	}

	@Override
	public InterviewDto completeLevels(Long interviewId, Long requestId, List<String> levels, String notes,
			Long interviewerUserId, String decision) {

		if (levels == null || levels.isEmpty())
			throw new IllegalArgumentException("levels is required");
		if (decision == null || decision.isBlank())
			throw new IllegalArgumentException("status is required");

		String norm = decision.trim();
		if (!norm.equalsIgnoreCase("Selected") && !norm.equalsIgnoreCase("Rejected"))
			throw new IllegalArgumentException("status must be Selected or Rejected");

		requireAllowedLevels(levels);
		Interview iv = requireInterview(interviewId);
		requireSameRequest(iv, requestId);
		if (interviewerUserId != null)
			requireUser(interviewerUserId);

		List<LevelProgressDto> progress = readProgress(iv.getLevelProgress());
		OffsetDateTime now = OffsetDateTime.now();

		for (String lvl : new LinkedHashSet<>(levels)) {
			LevelProgressDto row = findRow(progress, lvl).orElseGet(() -> {
				LevelProgressDto r = new LevelProgressDto();
				r.setLevel(lvl);
				r.setStatus("Planned");
				progress.add(r);
				return r;
			});

			if (interviewerUserId != null)
				row.setInterviewerUserId(interviewerUserId);

			row.setCompletedAt(now);
			row.setCompletedAtText(fmt(now));

			if (StringUtils.hasText(notes)) {
				String prev = row.getFeedback();
				row.setFeedback(prev == null || prev.isBlank() ? notes : (prev + " | " + notes));
			}

			row.setStatus(norm.substring(0, 1).toUpperCase() + norm.substring(1).toLowerCase());
		}

		iv.setLevelProgress(writeProgress(progress));
		String overall = computeOverall(progress);
		if ("Selected".equalsIgnoreCase(overall))
			iv.setStatus("Selected");
		else if ("Rejected".equalsIgnoreCase(overall))
			iv.setStatus("Rejected");

		Interview saved = repo.save(iv);
		return sanitizeForClient(enrich(buildCreateOrUpdateResponse(saved)));
	}

	private ResourceRequest requireRequest(Long requestId) {
		if (requestId == null)
			throw new IllegalArgumentException("requestId is required");
		return rrRepo.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
	}

	private Project requireProject(Long projectId) {
		if (projectId == null)
			throw new IllegalArgumentException("projectId is required");
		return projectRepo.findById(projectId)
				.orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
	}

	private Interview requireInterview(Long interviewId) {
		if (interviewId == null)
			throw new IllegalArgumentException("interviewId is required");
		return repo.findById(interviewId)
				.orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));
	}

	private void requireSameRequest(Interview iv, Long requestId) {
		if (!Objects.equals(iv.getRequestId(), requestId))
			throw new IllegalArgumentException(
					"Interview " + iv.getInterviewId() + " does not belong to requestId " + requestId);
	}

	private void requireEmployee(Long employeeId) {
		if (employeeId == null)
			throw new IllegalArgumentException("employeeId is required");
		if (!employeeRepo.existsById(employeeId))
			throw new IllegalArgumentException("Employee not found: " + employeeId);
	}

	private void requireUser(Long userId) {
		if (userId == null)
			throw new IllegalArgumentException("interviewerUserId is required");
		if (!userAccountRepo.existsById(userId))
			throw new IllegalArgumentException("User not found: " + userId);
	}

	private void requireAllowedLevels(List<String> levels) {
		if (levels == null || levels.isEmpty())
			return;
		for (String lvl : levels)
			if (!ALLOWED_LEVELS.contains(lvl))
				throw new IllegalArgumentException("Invalid interview level: " + lvl);
	}

	private void validateCreate(InterviewDto dto) {
		if (dto.getRequestId() == null)
			throw new IllegalArgumentException("requestId is required");
		if (dto.getEmployeeId() == null)
			throw new IllegalArgumentException("employeeId is required");
		if (!rrRepo.existsById(dto.getRequestId()))
			throw new IllegalArgumentException("Request not found: " + dto.getRequestId());
		if (!employeeRepo.existsById(dto.getEmployeeId()))
			throw new IllegalArgumentException("Employee not found: " + dto.getEmployeeId());
		if (dto.getInterviewerUserId() != null && !userAccountRepo.existsById(dto.getInterviewerUserId()))
			throw new IllegalArgumentException("User not found: " + dto.getInterviewerUserId());

		boolean hasRoot = dto.getScheduledAt() != null
				|| (dto.getScheduledAtText() != null && !dto.getScheduledAtText().isBlank());

		boolean hasAnyLevel = dto.getLevelProgress() != null && dto.getLevelProgress().stream()
				.anyMatch(lp -> lp.getScheduledAtText() != null && !lp.getScheduledAtText().isBlank());

		if (!hasRoot && !hasAnyLevel)
			throw new IllegalArgumentException("scheduledAt is required (format: dd-MM-yyyy HH-mm)");

		requireAllowedLevels(dto.getInterviewLevels());

		if (dto.getLevelProgress() != null)
			for (LevelProgressDto lp : dto.getLevelProgress())
				if (lp != null && lp.getInterviewerUserId() != null
						&& !userAccountRepo.existsById(lp.getInterviewerUserId()))
					throw new IllegalArgumentException("User not found: " + lp.getInterviewerUserId());
	}

	private OffsetDateTime resolveScheduledAt(InterviewDto dto) {
		OffsetDateTime when = parseClientDateTime(dto.getScheduledAtText());
		if (when == null)
			when = dto.getScheduledAt();
		if (when == null)
			when = earliestFromLevels(dto);
		return when;
	}

	private OffsetDateTime earliestFromLevels(InterviewDto dto) {
		if (dto.getLevelProgress() == null || dto.getLevelProgress().isEmpty())
			return null;
		OffsetDateTime best = null;
		for (LevelProgressDto lp : dto.getLevelProgress()) {
			if (lp.getScheduledAtText() == null || lp.getScheduledAtText().isBlank())
				continue;
			OffsetDateTime t = parseClientDateTime(lp.getScheduledAtText());
			if (t == null)
				continue;
			if (best == null || t.isBefore(best))
				best = t;
		}
		return best;
	}

	private Interview initInterviewEntity(InterviewDto dto, OffsetDateTime when) {
		Interview entity = new Interview();
		entity.setInterviewId(null);
		entity.setRequestId(dto.getRequestId());
		entity.setEmployeeId(dto.getEmployeeId());
		entity.setInterviewerId(null);
		entity.setInterviewType(null);
		entity.setScheduledAt(when);
		entity.setNotes(dto.getFeedback());
		entity.setStatus("Scheduled");
		return entity;
	}

	private List<LevelProgressDto> buildProgress(List<String> plannedLevels, List<LevelProgressDto> incoming,
			Long metaUserId, boolean copyInterviewer) {

		List<LevelProgressDto> progress = new LinkedList<>();

		if (plannedLevels != null && !plannedLevels.isEmpty()) {
			for (String lvl : new LinkedHashSet<>(plannedLevels)) {
				LevelProgressDto row = new LevelProgressDto();
				row.setLevel(lvl);
				row.setStatus("Planned");
				progress.add(row);
			}
		}

		if (incoming != null && !incoming.isEmpty()) {
			for (LevelProgressDto in : incoming) {
				if (in == null || !StringUtils.hasText(in.getLevel()))
					continue;
				if (!ALLOWED_LEVELS.contains(in.getLevel()))
					throw new IllegalArgumentException("Invalid interview level: " + in.getLevel());

				LevelProgressDto r = findRow(progress, in.getLevel()).orElseGet(() -> {
					LevelProgressDto x = new LevelProgressDto();
					x.setLevel(in.getLevel());
					x.setStatus("Planned");
					progress.add(x);
					return x;
				});

				if (copyInterviewer)
					r.setInterviewerUserId(in.getInterviewerUserId());

				r.setScheduledAtText(in.getScheduledAtText());
				r.setInterviewNotes(in.getInterviewNotes());

				if (StringUtils.hasText(in.getScheduledAtText()))
					r.setStatus("Scheduled");
			}
		}

		if (metaUserId != null) {
			LevelProgressDto meta = new LevelProgressDto();
			meta.setLevel(META_LEVEL);
			meta.setStatus("created");
			meta.setInterviewerUserId(metaUserId);
			progress.add(meta);
		}
		return progress;
	}

	private InterviewDto buildCreateOrUpdateResponse(Interview i) {
		InterviewDto dto = new InterviewDto();
		dto.setInterviewId(i.getInterviewId());
		dto.setRequestId(i.getRequestId());
		dto.setEmployeeId(i.getEmployeeId());
		dto.setStatus(i.getStatus());
		dto.setCurrentStatus(i.getStatus());
		dto.setInterviewLevels(i.getPlannedLevels());
		dto.setOverallNotes(i.getNotes());

		List<LevelProgressDto> rows = readProgress(i.getLevelProgress());
		dto.setLevelProgress(rows);

		String overall = computeOverall(rows);
		if ("Selected".equals(overall))
			dto.setOverallStatus("Interview Selected");
		else if ("Rejected".equals(overall))
			dto.setOverallStatus("Interview Rejected");
		else
			dto.setOverallStatus("Pending");

		if (rows != null)
			rows.stream().filter(r -> "ONBOARDING".equalsIgnoreCase(r.getLevel())).findFirst()
					.ifPresent(onboardingRow -> dto.setOnboardingStatus(onboardingRow.getStatus()));

		return dto;
	}

	private InterviewDto enrich(InterviewDto dto) {
		if (dto.getRequestId() != null) {
			rrRepo.findById(dto.getRequestId()).ifPresent(rr -> {
				if (rr.getGroupId() != null) {
					dto.setGroupId(rr.getGroupId());
					groupRepo.findById(rr.getGroupId()).ifPresent(g -> dto.setGroupTitle(g.getTitle()));
				}

				if (rr.getDemandId() != null) {
					dto.setDemandId(rr.getDemandId());
					demandRepo.findById(rr.getDemandId()).ifPresent(d -> {
						dto.setDemandTitle(d.getDemandtitle());

						dto.setProjectName(d.getProjectName());
						dto.setCompanyId(d.getCompanyId());
						companyRepo.findById(d.getCompanyId()).ifPresent(co -> dto.setCompanyName(co.getCompanyName()));

						if (d.getAccountId() != null) {
							accountRepo.findById(d.getAccountId()).ifPresent(a -> {
								dto.setAccountId(a.getAccountId());
								dto.setAccountName(a.getAccountName());
							});
						}
					});
				}

				Long projId = rr.getProjectId();
				if (projId != null) {
					projectRepo.findById(projId).ifPresent(p -> {
						dto.setProjectId(p.getProjectId());
						dto.setProjectName(p.getProjectName());
						dto.setCompanyId(p.getCompanyId());
						companyRepo.findById(p.getCompanyId()).ifPresent(co -> dto.setCompanyName(co.getCompanyName()));
						if (p.getAccountId() != null) {
							accountRepo.findById(p.getAccountId()).ifPresent(a -> {
								dto.setAccountId(a.getAccountId());
								dto.setAccountName(a.getAccountName());
							});
						}
					});
				}
			});
		}

		if (dto.getEmployeeId() != null) {
			employeeRepo.findById(dto.getEmployeeId()).ifPresent(e -> {
				dto.setEmployeeName((e.getFirstName() + " " + e.getLastName()).trim());
				dto.setEmployeeEmail(e.getEmail());
			});
		}

		if (dto.getLevelProgress() != null) {
			for (LevelProgressDto r : dto.getLevelProgress()) {
				if (META_LEVEL.equals(r.getLevel())) {
					if (r.getInterviewerUserId() != null) {
						dto.setCreatedByUserId(r.getInterviewerUserId());
						userAccountRepo.findById(r.getInterviewerUserId()).ifPresent(ua -> {
							String name = resolveInterviewerDisplayName(ua);
							dto.setCreatedByUserName(name);
						});
					}
				} else {
					if (r.getInterviewerUserId() != null) {
						userAccountRepo.findById(r.getInterviewerUserId()).ifPresent(ua -> {
							r.setInterviewerEmail(ua.getEmail());
							String name = resolveInterviewerDisplayName(ua);
							r.setInterviewerName(name);
						});
					}
				}
			}
		}
		return dto;
	}

	private void notifyOnCreateOrUpdateEmails(Interview saved, Project proj, String accountName,
			EmailService.MailAction action, List<LevelProgressDto> progress) {
		try {
			String employeeEmail = null, employeeName = null;
			if (saved.getEmployeeId() != null) {
				var emp = employeeRepo.findById(saved.getEmployeeId()).orElse(null);
				if (emp != null) {
					employeeName = (emp.getFirstName() + " " + emp.getLastName()).trim();
					employeeEmail = (emp.getEmail() != null && !emp.getEmail().isBlank()) ? emp.getEmail()
							: userAccountRepo.findByEmployeeId(emp.getEmployeeId()).map(ua -> ua.getEmail())
									.orElse(null);
				}
			}

			String projectName = null;
			if (proj != null) {
				projectName = proj.getProjectName();
			} else if (saved.getRequestId() != null) {
				projectName = rrRepo.findById(saved.getRequestId()).flatMap(
						rr -> rr.getDemandId() == null ? Optional.empty() : demandRepo.findById(rr.getDemandId()))
						.map(d -> d.getProjectName()).orElse(null);
			}
			String safeProjectName = (projectName != null && !projectName.isBlank()) ? projectName : "Project";

			String typeDisplay = deriveTypeFromLevels(progress);
			String whenRaw = fmt(saved.getScheduledAt());
			if (whenRaw == null || whenRaw.isBlank())
				whenRaw = deriveFirstTimeFromLevels(progress);

			CompletableFuture<Boolean> fEmp = (employeeEmail != null && !employeeEmail.isBlank())
					? emailService.sendEmployeeInterviewMailAsync(employeeEmail, safeProjectName, accountName,
							(employeeName != null ? employeeName : "Team Member"), typeDisplay, whenRaw, action)
					: CompletableFuture.completedFuture(true);

			LinkedHashSet<Long> uniqueInterviewers = new LinkedHashSet<>();
			for (LevelProgressDto r : progress)
				if (r.getInterviewerUserId() != null)
					uniqueInterviewers.add(r.getInterviewerUserId());

			List<CompletableFuture<Boolean>> futures = new ArrayList<>();
			for (Long uid : uniqueInterviewers) {
				UserAccount intr = userAccountRepo.findById(uid).orElse(null);
				if (intr == null || intr.getEmail() == null || intr.getEmail().isBlank())
					continue;

				String interviewerName = resolveInterviewerDisplayName(intr);
				String candName = (employeeName != null ? employeeName : "Employee");

				futures.add(emailService.sendInterviewerNotificationMailAsync(intr.getEmail(), safeProjectName,
						accountName, interviewerName, candName, typeDisplay, whenRaw, action));

				notifyUser(uid, action == EmailService.MailAction.CREATED ? "Interview Scheduled" : "Interview Updated",
						"Interview assigned/updated for project " + safeProjectName, "Normal", "Interview",
						saved.getInterviewId());
			}

			CompletableFuture.allOf(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)), fEmp).join();
		} catch (Exception e) {
		}
	}

	private String deriveTypeFromLevels(List<LevelProgressDto> progress) {
		if (progress == null || progress.isEmpty())
			return "Interview";

		Set<String> lvls = new LinkedHashSet<>();
		for (LevelProgressDto r : progress)
			if (r.getLevel() != null && !META_LEVEL.equals(r.getLevel())
					&& !"ONBOARDING".equalsIgnoreCase(r.getLevel()))
				lvls.add(r.getLevel());

		if (lvls.isEmpty())
			return "Interview";
		if (lvls.size() == 1)
			return lvls.iterator().next();
		return "Multiple Levels";
	}

	private String deriveFirstTimeFromLevels(List<LevelProgressDto> progress) {
		if (progress == null)
			return null;
		OffsetDateTime best = null;
		for (LevelProgressDto r : progress) {
			if (r.getScheduledAtText() == null || r.getScheduledAtText().isBlank())
				continue;
			OffsetDateTime t = parseClientDateTime(r.getScheduledAtText());
			if (t == null)
				continue;
			if (best == null || t.isBefore(best))
				best = t;
		}
		return fmt(best);
	}

	private String resolveInterviewerDisplayName(UserAccount ua) {
		if (ua == null)
			return "Interviewer";
		if (ua.getEmployeeId() != null) {
			var e = employeeRepo.findById(ua.getEmployeeId()).orElse(null);
			if (e != null)
				return (e.getFirstName() + " " + e.getLastName()).trim();
		}
		if (ua.getEmail() != null && !ua.getEmail().isBlank())
			return ua.getEmail();
		return "Interviewer";
	}

	private InterviewDto sanitizeForClient(InterviewDto dto) {
		if (dto.getLevelProgress() != null) {
			List<LevelProgressDto> cleaned = dto.getLevelProgress().stream()
					.filter(r -> !META_LEVEL.equals(r.getLevel())).peek(r -> {
						r.setRating(null);
						r.setRecommendation(null);
						r.setComments(null);
					}).collect(Collectors.toList());
			dto.setLevelProgress(cleaned);
		}

		dto.setInterviewerUserId(null);
		dto.setInterviewType(null);
		dto.setInterviewerName(null);
		dto.setInterviewerEmail(null);
		dto.setFeedbackId(null);
		dto.setRating(null);
		dto.setRecommendation(null);
		dto.setFeedbackComments(null);
		dto.setScheduledAtText(null);
		return dto;
	}

	private void notifyUser(Long userId, String title, String message, String priority, String type, Long entityId) {
		Notification n = new Notification();
		n.setUserId(userId);
		n.setTitle(title);
		n.setMessage(message);
		n.setPriority(priority);
		n.setRelatedEntityType(type);
		n.setRelatedEntityId(entityId);
		notificationRepo.save(n);
	}

	private List<LevelProgressDto> readProgress(String json) {
		try {
			if (json == null || json.isBlank())
				return new ArrayList<>();
			return om.readValue(json, new TypeReference<List<LevelProgressDto>>() {
			});
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	private String writeProgress(List<LevelProgressDto> rows) {
		try {
			return om.writeValueAsString(rows == null ? List.of() : rows);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to serialize level progress");
		}
	}

	private Optional<LevelProgressDto> findRow(List<LevelProgressDto> rows, String level) {
		if (rows == null)
			return Optional.empty();
		for (LevelProgressDto r : rows)
			if (r.getLevel() != null && r.getLevel().equals(level))
				return Optional.of(r);
		return Optional.empty();
	}

	private OffsetDateTime parseClientDateTime(String txt) {
		if (txt == null || txt.isBlank())
			return null;
		try {
			var ld = java.time.LocalDateTime.parse(txt, IN_FMT_DASH);
			return ld.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
		} catch (Exception ignore) {
		}
		try {
			var ld = java.time.LocalDateTime.parse(txt, IN_FMT_COLON);
			return ld.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
		} catch (Exception ignore) {
		}
		return OffsetDateTime.parse(txt);
	}

	private String fmt(OffsetDateTime odt) {
		return odt == null ? null : OUT_FMT.format(odt);
	}

	private String computeOverall(List<LevelProgressDto> rows) {
		if (rows == null || rows.isEmpty())
			return "Pending";

		List<LevelProgressDto> relevantLevels = rows.stream().filter(r -> r.getLevel() != null
				&& !META_LEVEL.equals(r.getLevel()) && !"ONBOARDING".equalsIgnoreCase(r.getLevel()))
				.collect(Collectors.toList());

		if (relevantLevels.isEmpty())
			return "Pending";

		boolean anyRejected = relevantLevels.stream().anyMatch(r -> "Rejected".equalsIgnoreCase(r.getStatus()));
		if (anyRejected)
			return "Rejected";

		boolean allSelected = relevantLevels.stream().allMatch(r -> "Selected".equalsIgnoreCase(r.getStatus()));
		if (allSelected)
			return "Selected";

		return "Pending";
	}
}
