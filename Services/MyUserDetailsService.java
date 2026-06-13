package com.smart_complaint_service.project.Services;
import com.smart_complaint_service.project.Entities.User;
import com.smart_complaint_service.project.Principals.UserPrincipal;
import com.smart_complaint_service.project.Repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MyUserDetailsService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailId(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email: " + email)
                );
        return new UserPrincipal(user);
    }
}