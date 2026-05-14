package com.ris.rms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.dto.MatchResponseDto;
import com.ris.rms.entity.*;
import com.ris.rms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.Locale; 

@Slf4j
@Service
@RequiredArgsConstructor
public class DemandMatchingService {

    private final DemandRepository demandRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeSkillRepository empSkillRepo;
    private final SkillRepository skillRepo;
    private final EmployeeDocumentRepository docRepo;
    private final ResumeStorageService resumeStorage;
    private final LlmClientService llmClient;

    private final Tika tika = new Tika();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public Map<String, Object> findMatchesForDemand(Long demandId) {
        Demand demand = demandRepo.findById(demandId)
                .orElseThrow(() -> new IllegalArgumentException("Demand not found: " + demandId));

        List<Long> skillIdList = new ArrayList<>();
        if (demand.getSkillIds() != null) {
            skillIdList.addAll(demand.getSkillIds());
        }
        List<String> demandSkills = resolveSkillNames(skillIdList);

        String demandText = String.format("Title: %s\nDescription: %s\nRequired Skills: %s\nExp: %s",
                demand.getDemandtitle(),
                demand.getDescription() != null ? demand.getDescription() : "N/A",
                String.join(", ", demandSkills),
                demand.getYearsofexp());

        List<Employee> employees = employeeRepo.findAllByCompanyId(demand.getCompanyId());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<MatchResponseDto>> futures = employees.stream()
                    .map(emp -> CompletableFuture.supplyAsync(
                            () -> processEmployee(emp, demandText, demandSkills), executor))
                    .toList();

            List<MatchResponseDto> matches = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .filter(m -> m.getMatchScore() > 0)
                    .sorted(Comparator.comparingInt(MatchResponseDto::getMatchScore).reversed())
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("demandId", demand.getDemandid());
            result.put("demandTitle", demand.getDemandtitle());
            result.put("totalEmployees", employees.size());
            result.put("matchedEmployees", matches.size());
            result.put("matches", matches);

            return result;
        }
    }

    private MatchResponseDto processEmployee(Employee emp, String demandContext, List<String> demandSkills) {
        try {
            List<String> empSkills = getEmployeeSkills(emp.getEmployeeId());
            List<String> intersectingSkills = findIntersectingSkills(demandSkills, empSkills);

            Optional<EmployeeDocument> primaryDoc = docRepo.findPrimaryResume(emp.getEmployeeId());
            boolean hasResume = primaryDoc.isPresent();

       
            if (intersectingSkills.isEmpty() && !hasResume) {
                return buildDto(
                        emp,
                        0,
                        "Auto-skipped: No matching skills or resume.",
                        "Quick Filter",
                        intersectingSkills,
                        false
                );
            }

      
            String resumeText = "";
            if (hasResume) {
                try {
                    ResumeStorageService.ResumeResource resource = resumeStorage.load(primaryDoc.get());
                    try (InputStream is = resource.resource().getInputStream()) {
                        resumeText = tika.parseToString(is);
                        if (resumeText.length() > 4000) {
                            resumeText = resumeText.substring(0, 4000);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Resume error for emp {}: {}", emp.getEmployeeId(), e.getMessage());
                }
            }

            String empProfile = String.format(
                    "Job Title: %s\nExperience: %d years\nSkills: %s\nSummary: %s",
                    emp.getJobTitle(),
                    emp.getExperienceYears(),
                    String.join(", ", empSkills),
                    emp.getProfilesummary() != null ? emp.getProfilesummary() : "N/A"
            );

        
            int ruleScore = calculateRuleBasedScore(demandSkills, empSkills, emp.getExperienceYears(), resumeText);

            
            if (!llmClient.isEnabled()) {
                return buildDto(
                        emp,
                        ruleScore,
                        "Rule-based scoring (skills + resume, AI disabled).",
                        "Rule-Based",
                        intersectingSkills,
                        hasResume
                );
            }

           
            if (ruleScore < 30) {
                return buildDto(
                        emp,
                        ruleScore,
                        "Rule-based scoring (skills + resume, low match, AI skipped).",
                        "Rule-Based",
                        intersectingSkills,
                        hasResume
                );
            }

     
            try {
                String prompt = String.format(
                        "DEMAND:\n%s\n\nCANDIDATE:\n%s\n\nRESUME:\n%s",
                        demandContext, empProfile, resumeText
                );

                String jsonResponse = llmClient.getMatchAnalysis(prompt);

                JsonNode root = objectMapper.readTree(extractJson(jsonResponse));
                int aiScore = root.path("score").asInt(ruleScore); // fallback to ruleScore if missing
                String reasoning = root.path("reasoning").asText("AI Analysis");

                return buildDto(
                        emp,
                        aiScore,
                        reasoning,
                        "AI (" + llmClient.getModel() + ")",
                        intersectingSkills,
                        hasResume
                );

            } catch (Exception e) {
                log.warn("AI scoring failed for emp {}: {}", emp.getEmployeeId(), e.getMessage());
             
                return buildDto(
                        emp,
                        ruleScore,
                        "Fallback: AI unavailable, using rule-based score (skills + resume).",
                        "Rule-Based",
                        intersectingSkills,
                        hasResume
                );
            }

        } catch (Exception e) {
            log.warn("Error processing employee {}: {}", emp.getEmployeeId(), e.getMessage());
            return null;
        }
    }

    private MatchResponseDto buildDto(Employee emp,
                                      int score,
                                      String reasoning,
                                      String source,
                                      List<String> skills,
                                      boolean hasResume) {
        return MatchResponseDto.builder()
                .employeeId(emp.getEmployeeId())
                .firstName(emp.getFirstName())
                .lastName(emp.getLastName())
                .email(emp.getEmail())
                .jobTitle(emp.getJobTitle())
                .location(emp.getLocation())
                .experienceYears(emp.getExperienceYears())
                .employmentType(emp.getEmploymentType())
                .status(emp.getStatus())
                .matchScore(score)
                .matchReasoning(reasoning)
                .scoringSource(source)
                .matchingSkills(skills)
                .hasResume(hasResume)
                .build();
    }

    private int calculateRuleBasedScore(List<String> demandSkills,
                                        List<String> empSkills,
                                        Integer empExp,
                                        String resumeText) {

        if (demandSkills == null || demandSkills.isEmpty()) {
            double expScore = (empExp != null && empExp > 3) ? 100.0 : 50.0;
            return (int) Math.round(expScore);
        }

        
        long matchCount = empSkills.stream()
                .filter(s -> demandSkills.stream().anyMatch(ds -> ds.equalsIgnoreCase(s)))
                .count();
        double skillScore = (matchCount * 100.0) / demandSkills.size();

        double resumeSkillScore = 0.0;
        if (resumeText != null && !resumeText.isBlank()) {
            String lower = resumeText.toLowerCase(Locale.ROOT);
            long resumeHits = demandSkills.stream()
                    .filter(ds -> lower.contains(ds.toLowerCase(Locale.ROOT)))
                    .count();
            resumeSkillScore = (resumeHits * 100.0) / demandSkills.size();
        }

       
        double expScore;
        if (empExp == null) {
            expScore = 50.0;
        } else if (empExp >= 5) {
            expScore = 100.0;
        } else if (empExp >= 3) {
            expScore = 80.0;
        } else {
            expScore = 60.0;
        }

        double finalScore =
                0.45 * skillScore +      
                0.35 * resumeSkillScore + 
                0.20 * expScore;         

        return (int) Math.round(finalScore);
    }

    private List<String> findIntersectingSkills(List<String> demandSkills, List<String> empSkills) {
        return empSkills.stream()
                .filter(es -> demandSkills.stream().anyMatch(ds -> ds.equalsIgnoreCase(es)))
                .collect(Collectors.toList());
    }

    private List<String> getEmployeeSkills(Long empId) {
        List<Long> ids = empSkillRepo.findAllByEmployeeId(empId).stream()
                .map(EmployeeSkill::getSkillId)
                .toList();
        return resolveSkillNames(ids);
    }

    private List<String> resolveSkillNames(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return Collections.emptyList();
        return skillRepo.findAllById(ids).stream()
                .map(Skill::getSkillName)
                .toList();
    }

    private String extractJson(String raw) {
        if (raw == null)
            return "{}";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            int end = cleaned.lastIndexOf("```");
            if (end > 7) {
                return cleaned.substring(7, end).trim();
            }
        } else if (cleaned.startsWith("```")) {
            int end = cleaned.lastIndexOf("```");
            if (end > 3) {
                return cleaned.substring(3, end).trim();
            }
        }

        try {
            JsonNode outer = objectMapper.readTree(cleaned);
            if (outer.has("choices") && outer.get("choices").isArray() && outer.get("choices").size() > 0) {
                JsonNode message = outer.get("choices").get(0).path("message");
                if (message.has("content")) {
                    String content = message.get("content").asText();
                    if (content.trim().startsWith("```"))
                        return extractJson(content);
                    return content;
                }
            }
        } catch (Exception e) {
            
        }
        return cleaned;
    }
}
