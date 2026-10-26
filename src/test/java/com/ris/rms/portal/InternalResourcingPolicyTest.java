package com.ris.rms.portal;

import com.ris.rms.repository.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalResourcingPolicyTest {
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    @Test void staffAccountAccessDoesNotRequireResourcingEligibility() {
        var properties = new PortalAccessProperties(); properties.setEnabled(true);
        var policy = new InternalResourcingPolicy(properties, mock(PortalAccessClient.class), mock(EmployeeRepository.class));
        var request = new MockHttpServletRequest(); request.addParameter("purpose", "RMS_ACCOUNT");
        request.setAttribute(PortalAccessClient.Access.class.getName(), new PortalAccessClient.Access(1, 2, Set.of("RMS_ACCESS", "RMS_ADMIN"), Set.of(77L)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(policy.filterDirectory()).isFalse();
        assertThat(policy.enabled()).isTrue();
        request.setAttribute(PortalAccessClient.Access.class.getName(), new PortalAccessClient.Access(1, 2, Set.of("RMS_ACCESS"), Set.of(77L)));
        assertThat(policy.filterDirectory()).isTrue();
        request.removeParameter("purpose");
        assertThat(policy.filterDirectory()).isTrue();
    }
}
