package com.ris.rms.security;

import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

       
        if (userAccount.getIsActive() == null || !userAccount.getIsActive()) {
            throw new UsernameNotFoundException("User is inactive");
        }

        return User.builder()
                .username(userAccount.getEmail())
                .password(userAccount.getPasswordHash()) 
                .authorities(new ArrayList<>()) 
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!userAccount.getIsActive())
                .build();
    }
}
