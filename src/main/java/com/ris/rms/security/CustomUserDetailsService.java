package com.ris.rms.security;

import com.ris.rms.entity.Role;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.portal.RisPortalEmployeeService;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RisPortalEmployeeService risPortalEmployeeService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var localOpt = userAccountRepository.findByEmailIgnoreCase(email);
        if (localOpt.isPresent()) {
            UserAccount userAccount = localOpt.get();
            if (userAccount.getIsActive() == null || !userAccount.getIsActive()) {
                throw new UsernameNotFoundException("User is inactive");
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            List<Long> roleIds = userAccount.getRoleIds();
            if (roleIds != null && !roleIds.isEmpty()) {
                List<Role> roles = roleRepository.findAllById(roleIds);
                for (Role role : roles) {
                    if (role.getRoleName() != null) {
                        authorities.add(new SimpleGrantedAuthority(role.getRoleName()));
                    }
                }
            }

            return User.builder()
                    .username(userAccount.getEmail())
                    .password(userAccount.getPasswordHash())
                    .authorities(authorities)
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .disabled(!userAccount.getIsActive())
                    .build();
        }

        // Fall back directly to RIS Portal identity (no DB writes into rms.user_account/employee)
        var portalOpt = risPortalEmployeeService.findUserAuth(email)
                .filter(u -> !Boolean.FALSE.equals(u.isActive()) && !Boolean.TRUE.equals(u.isLocked()) && u.employeeId() != null);
        if (portalOpt.isEmpty()) {
            throw new UsernameNotFoundException("User not found in RIS Portal: " + email);
        }
        var portalUser = portalOpt.get();
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (portalUser.roles() != null) {
            portalUser.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        }

        return User.builder()
                .username(portalUser.email())
                .password(portalUser.passwordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
