package com.ris.rms.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalAccessFilterTest {
    private final PortalAccessProperties properties = new PortalAccessProperties();
    private final PortalAccessClient client = mock(PortalAccessClient.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final InternalResourcingPolicy policy = mock(InternalResourcingPolicy.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PortalCompanyScope scope = mock(PortalCompanyScope.class);
    private final PortalAccessFilter filter = new PortalAccessFilter(properties, client, users, policy, new ObjectMapper(), jdbc, scope);
    private UserAccount user;

    @BeforeEach void setup() {
        properties.setEnabled(true);
        properties.setIntegrationKey("test-secret");
        user = new UserAccount(); user.setUserId(4L); user.setCompanyId(77L); user.setEmail("staff@example.com"); user.setIsActive(true);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(client.access(user)).thenReturn(new PortalAccessClient.Access(1, 2, Set.of("RMS_ACCESS", "RMS_EMPLOYEE_READ", "RMS_RESUME_SHARE", "RMS_ALLOCATION_WRITE"), Set.of(77L)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void revokedPortalAccessBlocksAnExistingRmsSession() throws Exception {
        when(client.access(user)).thenThrow(new AccessDeniedException("Access removed"));
        var response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/portal/access"), response, (request, result) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();
    }

    @Test void internalProfileMustBeAvailableBeforeResumeSubmission() throws Exception {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(42L))).thenReturn(List.of(77L));
        doThrow(new AccessDeniedException("Not available")).when(policy).requireAvailable(42L);
        var request = json("/api/employees/share-resume", "{\"employeeId\":42,\"demandId\":3}");
        var response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();
        verify(policy).requireAvailable(42L);
    }

    @Test void externalCandidateBodyAndParametersArePreservedWithoutInternalEligibilityChecks() throws Exception {
        String body = "{\"candidateId\":9,\"resourceType\":\"EXTERNAL\",\"resume\":\"keep existing\"}";
        var request = json("/api/candidates/share-resume", body);
        var response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
            assertThat(req.getParameter("companyId")).isNull();
            called.set(true);
        });
        assertThat(called).isTrue();
        verifyNoInteractions(policy, jdbc, scope);
    }

    @Test void externalAllocationDoesNotRequireAnInternalEmployeeLink() throws Exception {
        var request = json("/api/allocations/create", "{\"candidateId\":9,\"resourceType\":\"EXTERNAL\"}");
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called).isTrue();
        verifyNoInteractions(policy);
    }

    @Test void forgedCompanyAndMissingActionPermissionAreRejected() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/employees/list"); request.addParameter("companyId", "88");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Must not reach controller"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test void integrationKeyIsReadOnlyAndCannotAuthorizeAWritingRequest() throws Exception {
        SecurityContextHolder.clearContext();
        var read = new MockHttpServletRequest("GET", "/api/demands/list"); read.addHeader("X-RIS-Integration-Key", "test-secret");
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(read, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called).isTrue();
        SecurityContextHolder.clearContext();
        var write = new MockHttpServletRequest("POST", "/api/demands/create"); write.addHeader("X-RIS-Integration-Key", "test-secret");
        var response = new MockHttpServletResponse();
        filter.doFilter(write, response, (req, res) -> fail("Read key cannot write"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest json(String path, String body) {
        var request = new MockHttpServletRequest("POST", path); request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8)); return request;
    }
}
