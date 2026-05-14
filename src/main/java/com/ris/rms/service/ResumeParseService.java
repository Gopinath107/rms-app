package com.ris.rms.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final LlmClientService llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "doc", "docx");

    public Map<String, Object> parse(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a resume file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 5MB.");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.') + 1) : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Please upload a PDF, DOC, or DOCX file.");
        }

        String rawText = extractText(file.getInputStream(), file.getContentType(), originalName);
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Could not extract text from the resume. The file may be image-based or corrupted.");
        }

        // Trim to ~10000 chars to stay within LLM token limits
        String trimmedText = rawText.length() > 10000 ? rawText.substring(0, 10000) : rawText;

        Map<String, Object> parsed;
        if (llmClient.isEnabled()) {
            try {
                parsed = parseWithLlm(trimmedText);
                log.info("Resume parsed via LLM successfully.");
            } catch (Exception e) {
                log.warn("LLM parsing failed, falling back to regex. Error: {}", e.getMessage());
                parsed = parseWithRegex(trimmedText);
            }
        } else {
            log.info("LLM disabled. Using regex-based resume parsing.");
            parsed = parseWithRegex(trimmedText);
        }

        return parsed;
    }

    // -------------------------------------------------------------------------
    // Text Extraction
    // -------------------------------------------------------------------------

    private String extractText(InputStream inputStream, String contentType, String fileName) {
        try {
            BodyContentHandler handler = new BodyContentHandler(200 * 1024); // 200KB text limit
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream");
            metadata.set("resourceName", fileName);

            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();
            context.set(AutoDetectParser.class, parser);

            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();
        } catch (Exception e) {
            log.error("Tika text extraction failed: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // LLM-based Parsing
    // -------------------------------------------------------------------------

    private Map<String, Object> parseWithLlm(String resumeText) throws Exception {
        String systemPrompt = """
                You are an expert HR recruiter and resume parser. Your job is to extract ALL possible structured information from the resume text below.

                Return ONLY a single valid JSON object with EXACTLY these keys (no extra keys, no explanations, no markdown):
                {
                  "firstName": "",
                  "middleName": "",
                  "lastName": "",
                  "email": "",
                  "personalEmail": "",
                  "phoneNumber": "",
                  "secondaryPhone": "",
                  "location": "",
                  "city": "",
                  "state": "",
                  "country": "",
                  "zipCode": "",
                  "street": "",
                  "dateOfBirth": "",
                  "gender": "",
                  "countryOfCitizenship": "",
                  "visa": "",
                  "visaType": "",
                  "experienceYears": null,
                  "role": "",
                  "employmentType": "",
                  "currentCompany": "",
                  "highestQualification": "",
                  "universityName": "",
                  "degrees": "",
                  "specialization": "",
                  "yearOfPassing": null,
                  "usaDegree": "",
                  "dateOfQualification": "",
                  "profileSummary": "",
                  "trainingSummary": "",
                  "certificationSummary": "",
                  "linkedIn": "",
                  "github": "",
                  "portfolio": "",
                  "leetcode": "",
                  "hackerrank": "",
                  "skills": [],
                  "secondarySkills": [],
                  "suggestedKeywords": ""
                }

                EXTRACTION RULES (follow strictly):
                - firstName: If name has 2 words, this is the 1st word. If 3+ words, this is the 1st word.
                - middleName: If name has 3+ words, this is all middle words. Else "".
                - lastName: If name has 2 words, this is the 2nd word. If 3+ words, this is the last word.
                - email: Primary WORK/BUSINESS email. If only one email is found and it is a business email, put it here. If only one email is found and it's personal (gmail/yahoo/outlook), put it in personalEmail AND copy it here as a fallback if workEmail is required.
                - personalEmail: Any gmail.com, yahoo.com, outlook.com, hotmail.com, icloud.com, rediffmail.com etc. email.
                - phoneNumber: Primary phone number. If it looks like a 10-digit Indian number without country code, prefix with "+91". Do not put inside wrong fields.
                - secondaryPhone: Second phone number if present, else "". If 10-digit Indian, prefix "+91".
                - location: City and State/Country combined.
                - city: City name only. If resume contains India/Indian city, map it.
                - state: State or province only.
                - country: Country name only (e.g. "India", "United States"). If Indian city/state found, set to "India".
                - zipCode: Postal / ZIP code if found, else "".
                - street: Street or address line if found, else "".
                - dateOfBirth: Date of birth in YYYY-MM-DD format if found, else "".
                - gender: "Male" or "Female" if determinable, otherwise "".
                - countryOfCitizenship: Country of citizenship if mentioned, else "".
                - visa: "Yes" if any visa is mentioned, "No" if explicitly stated no visa needed, else "".
                - visaType: Visa type (e.g. H1B, L1, OPT, Student) if mentioned, else "".
                - experienceYears: Total years of professional experience as an INTEGER. Calculate from work history (e.g., "2+ years" => 2). Return null only if truly unknown.
                - role: Current Job Title (from headline, current role, latest experience). Do not take random fragments.
                - employmentType: "Regular", "Contract", "C2C", "W2", "Full Time", "Part Time", or "Internship" — infer from context.
                - currentCompany: Most recent employer name.
                - highestQualification: Map to one of: Bachelor's Degree, Master's Degree, PhD, Diploma, Certification, Other.
                - universityName: Name of university/college for highest degree.
                - degrees: Abbreviation of highest degree (e.g. B.Tech, MCA, MBA).
                - specialization: Field of study/major.
                - yearOfPassing: 4-digit year when highest degree was completed, else null.
                - usaDegree: "Yes" if degree is from USA, "No" otherwise.
                - dateOfQualification: Exact date of qualification if found (YYYY-MM-DD), else "".
                - profileSummary: Candidate's professional summary. If none exists, generate a short factual summary from resume content without hallucinating.
                - trainingSummary: Training, workshops, internships mentioned.
                - certificationSummary: All certifications as comma-separated list.
                - linkedIn: LinkedIn profile URL if found (e.g. linkedin.com/in/...), else "".
                - github: GitHub profile URL if found (e.g. github.com/...), else "".
                - portfolio: Personal website, LeetCode, HackerRank, or portfolio URL if found, else "".
                - skills: JSON array of primary/core technical skills. Keep them as short, concise tags (e.g. "Java", "Spring Boot", "React"). No sentences. Deduplicate.
                - secondarySkills: JSON array of secondary tools/methodologies (e.g. "Agile", "Jira", "Git"). Keep them as short concise tags. Deduplicate.
                - suggestedKeywords: Comma-separated list of 5-10 SEO-friendly technical keywords for this candidate (e.g. "Full Stack, Cloud Native, Microservices").
                - leetcode: LeetCode profile URL if found, else "".
                - hackerrank: HackerRank profile URL if found, else "".

                Return ONLY the JSON object. No preamble, no explanation, no markdown fences.
                """;

        String userPrompt = "Parse this resume:\n\n" + resumeText;
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        String llmResponse = llmClient.getMatchAnalysis(fullPrompt);

        return extractJsonFromLlmResponse(llmResponse);
    }

    private Map<String, Object> extractJsonFromLlmResponse(String llmResponse) throws Exception {
        String cleaned = llmResponse;

        // Unwrap OpenRouter API response wrapper if present
        try {
            JsonNode root = objectMapper.readTree(llmResponse);
            if (root.has("choices")) {
                JsonNode content = root.path("choices").get(0).path("message").path("content");
                cleaned = content.asText();
            }
        } catch (Exception ignored) {}

        // Strip markdown code fences
        cleaned = cleaned.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        // Extract JSON object boundaries
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        JsonNode node = objectMapper.readTree(cleaned);
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("firstName", safeString(node, "firstName"));
        result.put("middleName", safeString(node, "middleName"));
        result.put("lastName", safeString(node, "lastName"));
        result.put("email", safeString(node, "email"));
        result.put("personalEmail", safeString(node, "personalEmail"));
        result.put("phoneNumber", safeString(node, "phoneNumber"));
        result.put("secondaryPhone", safeString(node, "secondaryPhone"));
        result.put("location", safeString(node, "location"));
        result.put("city", safeString(node, "city"));
        result.put("state", safeString(node, "state"));
        result.put("country", safeString(node, "country"));
        result.put("zipCode", safeString(node, "zipCode"));
        result.put("street", safeString(node, "street"));
        result.put("dateOfBirth", safeString(node, "dateOfBirth"));
        result.put("gender", safeString(node, "gender"));
        result.put("countryOfCitizenship", safeString(node, "countryOfCitizenship"));
        result.put("visa", safeString(node, "visa"));
        result.put("visaType", safeString(node, "visaType"));
        result.put("experienceYears", safeInteger(node, "experienceYears"));
        result.put("role", safeString(node, "role"));
        result.put("employmentType", safeString(node, "employmentType"));
        result.put("currentCompany", safeString(node, "currentCompany"));
        result.put("highestQualification", safeString(node, "highestQualification"));
        result.put("universityName", safeString(node, "universityName"));
        result.put("degrees", safeString(node, "degrees"));
        result.put("specialization", safeString(node, "specialization"));
        result.put("yearOfPassing", safeInteger(node, "yearOfPassing"));
        result.put("usaDegree", safeString(node, "usaDegree"));
        result.put("dateOfQualification", safeString(node, "dateOfQualification"));
        result.put("profileSummary", safeString(node, "profileSummary"));
        result.put("trainingSummary", safeString(node, "trainingSummary"));
        result.put("certificationSummary", safeString(node, "certificationSummary"));
        result.put("linkedIn", safeString(node, "linkedIn"));
        result.put("github", safeString(node, "github"));
        result.put("portfolio", safeString(node, "portfolio"));
        result.put("leetcode", safeString(node, "leetcode"));
        result.put("hackerrank", safeString(node, "hackerrank"));
        result.put("suggestedKeywords", safeString(node, "suggestedKeywords"));

        // Skills array
        List<String> skills = new ArrayList<>();
        JsonNode skillsNode = node.get("skills");
        if (skillsNode != null && skillsNode.isArray()) {
            for (JsonNode s : skillsNode) {
                String skill = s.asText().trim();
                if (!skill.isEmpty() && skill.length() < 60) skills.add(skill);
            }
        } else if (skillsNode != null && skillsNode.isTextual()) {
            for (String s : skillsNode.asText().split("[,;\\n]")) {
                String skill = s.trim();
                if (!skill.isEmpty() && skill.length() < 60) skills.add(skill);
            }
        }
        result.put("skills", skills);

        // Secondary Skills array
        List<String> secondarySkills = new ArrayList<>();
        JsonNode secondarySkillsNode = node.get("secondarySkills");
        if (secondarySkillsNode != null && secondarySkillsNode.isArray()) {
            for (JsonNode s : secondarySkillsNode) {
                String skill = s.asText().trim();
                if (!skill.isEmpty() && skill.length() < 60) secondarySkills.add(skill);
            }
        } else if (secondarySkillsNode != null && secondarySkillsNode.isTextual()) {
            for (String s : secondarySkillsNode.asText().split("[,;\\n]")) {
                String skill = s.trim();
                if (!skill.isEmpty() && skill.length() < 60) secondarySkills.add(skill);
            }
        }
        result.put("secondarySkills", secondarySkills);

        return result;
    }

    // -------------------------------------------------------------------------
    // Regex Fallback Parsing
    // -------------------------------------------------------------------------

    private Map<String, Object> parseWithRegex(String text) {
        Map<String, Object> result = new LinkedHashMap<>();

        String[] emails = extractAllEmails(text);
        String workEmail = "";
        String personalEmail = "";
        
        if (emails.length > 0) {
            for (String em : emails) {
                if (isPersonalEmail(em)) {
                    if (personalEmail.isEmpty()) personalEmail = em;
                } else {
                    if (workEmail.isEmpty()) workEmail = em;
                }
            }
            if (workEmail.isEmpty() && !personalEmail.isEmpty()) {
                workEmail = personalEmail; // fallback
            }
        }

        result.put("firstName", extractFirstName(text));
        result.put("middleName", extractMiddleName(text));
        result.put("lastName", extractLastName(text));
        result.put("email", workEmail);
        result.put("personalEmail", personalEmail);
        
        String[] phones = extractAllPhones(text);
        result.put("phoneNumber", phones.length > 0 ? phones[0] : "");
        result.put("secondaryPhone", phones.length > 1 ? phones[1] : "");
        
        result.put("location", extractLocation(text));
        
        String locationLower = result.get("location").toString().toLowerCase();
        if (locationLower.contains("india") || locationLower.contains("chennai") || locationLower.contains("bangalore") || locationLower.contains("mumbai") || locationLower.contains("delhi")) {
            result.put("country", "India");
        } else {
            result.put("country", "");
        }
        
        result.put("gender", "");
        result.put("experienceYears", extractExperienceYears(text));
        result.put("role", extractRole(text));
        result.put("employmentType", "Regular");
        result.put("currentCompany", extractCurrentCompany(text));
        result.put("degrees", extractDegree(text));
        result.put("specialization", extractSpecialization(text));
        result.put("yearOfPassing", extractYearOfPassing(text));
        result.put("profileSummary", extractSummary(text));
        result.put("trainingSummary", extractSection(text, "training|internship|workshop"));
        result.put("certificationSummary", extractSection(text, "certif|credential|license|accreditation"));
        result.put("skills", extractSkills(text));

        return result;
    }

    private boolean isPersonalEmail(String email) {
        String lower = email.toLowerCase();
        return lower.endsWith("@gmail.com") || lower.endsWith("@yahoo.com") || 
               lower.endsWith("@outlook.com") || lower.endsWith("@hotmail.com") || 
               lower.endsWith("@icloud.com") || lower.endsWith("@rediffmail.com");
    }

    // -------------------------------------------------------------------------
    // Regex Helpers
    // -------------------------------------------------------------------------

    private String[] extractAllEmails(String text) {
        List<String> emails = new ArrayList<>();
        Matcher m = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").matcher(text);
        while (m.find()) {
            String email = m.group().trim();
            if (!emails.contains(email)) emails.add(email);
        }
        return emails.toArray(new String[0]);
    }

    private String[] extractAllPhones(String text) {
        List<String> phones = new ArrayList<>();
        Matcher m = Pattern.compile("(?:(?:\\+91|0)[\\s\\-]?)?[6-9]\\d{9}|(?:\\+\\d{1,3}[\\s\\-]?)?(?:\\(\\d+\\)[\\s\\-]?)?\\d[\\d\\s\\-]{7,14}\\d").matcher(text);
        while (m.find()) {
            String p = m.group().replaceAll("[\\s\\-]", "").trim();
            // Defaulting Indian phones to +91
            if (p.length() == 10 && p.matches("[6-9]\\d{9}")) {
                p = "+91" + p;
            }
            if (!phones.contains(p)) phones.add(p);
        }
        return phones.toArray(new String[0]);
    }

    private String extractFirstName(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 2 && line.length() < 50 && line.matches("[A-Z][a-zA-Z]+(?:\\s+[A-Za-z]+)*")) {
                return line.split("\\s+")[0];
            }
        }
        return "";
    }

    private String extractMiddleName(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 2 && line.length() < 50 && line.matches("[A-Z][a-zA-Z]+(?:\\s+[A-Za-z]+)*")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 2) {
                    StringBuilder mn = new StringBuilder();
                    for (int i = 1; i < parts.length - 1; i++) {
                        if (i > 1) mn.append(" ");
                        mn.append(parts[i]);
                    }
                    return mn.toString();
                }
            }
        }
        return "";
    }

    private String extractLastName(String text) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 2 && line.length() < 50 && line.matches("[A-Z][a-zA-Z]+(?:\\s+[A-Za-z]+)*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) return parts[parts.length - 1];
            }
        }
        return "";
    }

    private String extractLocation(String text) {
        // Match common city/state patterns
        Matcher m = Pattern.compile("(?i)(?:location|address|city|residing|based in)\\s*:?\\s*([A-Za-z ]+(?:,\\s*[A-Za-z ]+)?)").matcher(text);
        if (m.find()) return m.group(1).trim();
        // Try to match known Indian cities
        Matcher cityM = Pattern.compile("\\b(Chennai|Bangalore|Bengaluru|Hyderabad|Mumbai|Delhi|Pune|Kolkata|Ahmedabad|Noida|Gurgaon)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (cityM.find()) return cityM.group(1);
        return "";
    }

    private Integer extractExperienceYears(String text) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:\\+\\s*)?(?:years?|yrs?)\\s*(?:of\\s*)?(?:experience|exp)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try { return (int) Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String extractRole(String text) {
        Matcher m = Pattern.compile("(?i)(?:designation|position|title|role)\\s*:?\\s*([A-Za-z ]+)").matcher(text);
        if (m.find()) return m.group(1).trim();
        // Common roles
        Matcher rm = Pattern.compile("\\b(Software Engineer|Senior Engineer|Java Developer|Full Stack|Frontend|Backend|DevOps|Data Analyst|Data Scientist|Project Manager|Business Analyst|QA Engineer|Tech Lead|Architect)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (rm.find()) return rm.group(1);
        return "";
    }

    private String extractCurrentCompany(String text) {
        Matcher m = Pattern.compile("(?i)(?:currently|present|working at|employer)\\s*:?\\s*([A-Za-z &.,]+)").matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private String extractDegree(String text) {
        if (Pattern.compile("\\bB\\.?Tech\\b|\\bBachelor\\s+of\\s+Technology\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "B.Tech";
        if (Pattern.compile("\\bM\\.?Tech\\b|\\bMaster\\s+of\\s+Technology\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "M.Tech";
        if (Pattern.compile("\\bMBA\\b|\\bMaster\\s+of\\s+Business\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "MBA";
        if (Pattern.compile("\\bBSc\\b|\\bB\\.Sc\\b|\\bBachelor\\s+of\\s+Science\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "B.Sc";
        if (Pattern.compile("\\bMSc\\b|\\bM\\.Sc\\b|\\bMaster\\s+of\\s+Science\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "M.Sc";
        if (Pattern.compile("\\bBCA\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "BCA";
        if (Pattern.compile("\\bMCA\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "MCA";
        if (Pattern.compile("\\bBE\\b|\\bBachelor\\s+of\\s+Engineering\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "BE";
        if (Pattern.compile("\\bB\\.Com\\b|\\bBachelor\\s+of\\s+Commerce\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "B.Com";
        return "";
    }

    private String extractSpecialization(String text) {
        Matcher m = Pattern.compile("(?i)(?:specialization|major|branch|stream|field)\\s*:?\\s*([A-Za-z ]+)").matcher(text);
        if (m.find()) return m.group(1).trim();
        if (Pattern.compile("Computer Science|CSE", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Computer Science";
        if (Pattern.compile("Information Technology|IT", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Information Technology";
        if (Pattern.compile("Electronics|ECE", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Electronics";
        if (Pattern.compile("Mechanical|ME|Mech", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Mechanical Engineering";
        if (Pattern.compile("Civil Engineering", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Civil Engineering";
        if (Pattern.compile("Finance|Accounting", Pattern.CASE_INSENSITIVE).matcher(text).find()) return "Finance";
        return "";
    }

    private Integer extractYearOfPassing(String text) {
        Matcher m = Pattern.compile("(?:passed?|graduated?|batch|(?:20|19)\\d{2})\\D{0,30}((?:20|19)\\d{2})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        int latest = 0;
        Matcher ym = Pattern.compile("\\b((?:20|19)\\d{2})\\b").matcher(text);
        while (ym.find()) {
            try {
                int yr = Integer.parseInt(ym.group(1));
                if (yr > latest && yr <= 2030) latest = yr;
            } catch (NumberFormatException ignored) {}
        }
        return latest > 0 ? latest : null;
    }

    private String extractSummary(String text) {
        Pattern p = Pattern.compile("(?i)(?:summary|objective|profile|about me|career objective)\\s*:?\\s*\\n([\\s\\S]{30,600}?)(?:\\n\\n|\\n[A-Z]|$)");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim().replaceAll("\\s+", " ");
        return "";
    }

    private String extractSection(String text, String keywords) {
        Pattern p = Pattern.compile("(?i)(?:" + keywords + ")\\s*:?\\s*\\n([\\s\\S]{10,400}?)(?:\\n\\n|\\n[A-Z]|$)");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim().replaceAll("\\s+", " ");
        return "";
    }

    private List<String> extractSkills(String text) {
        List<String> skills = new ArrayList<>();
        Pattern p = Pattern.compile("(?i)(?:technical skills?|skills?|key skills?|core competencies|expertise)\\s*:?\\s*\\n([\\s\\S]{10,800}?)(?:\\n\\n|\\n[A-Z]|$)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String skillBlock = m.group(1);
            for (String s : skillBlock.split("[,\\n•\\|\\t]+")) {
                String skill = s.trim().replaceAll("[^a-zA-Z0-9.#+\\-/() ]", "").trim();
                if (!skill.isEmpty() && skill.length() > 1 && skill.length() < 50) {
                    skills.add(skill);
                }
            }
        }
        return skills.size() > 40 ? skills.subList(0, 40) : skills;
    }

    // -------------------------------------------------------------------------
    // JSON Node Helpers
    // -------------------------------------------------------------------------

    private String safeString(JsonNode node, String key) {
        JsonNode n = node.get(key);
        if (n == null || n.isNull()) return "";
        return n.asText("").trim();
    }

    private Integer safeInteger(JsonNode node, String key) {
        JsonNode n = node.get(key);
        if (n == null || n.isNull()) return null;
        try { return n.asInt(); } catch (Exception e) { return null; }
    }
}
