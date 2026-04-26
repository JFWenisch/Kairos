package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.wenisch.kairos.entity.AppUser;
import tech.wenisch.kairos.entity.AuthProvider;
import tech.wenisch.kairos.entity.UserRole;
import tech.wenisch.kairos.repository.AppUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    // ── loadUserByUsername ─────────────────────────────────────────────────

    @Test
    void loadUserByUsernameReturnsUserDetailsForKnownUser() {
        AppUser user = AppUser.builder()
                .email("admin@example.com")
                .passwordHash("$2a$10$hashed")
                .role(UserRole.ADMIN)
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("admin@example.com");

        assertThat(details.getUsername()).isEqualTo("admin@example.com");
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsernameThrowsForUnknownUser() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsernameReturnsUserRoleForRegularUser() {
        AppUser user = AppUser.builder()
                .email("user@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("user@example.com");

        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    // ── createUser ─────────────────────────────────────────────────────────

    @Test
    void createUserEncodesPasswordAndPersists() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = userService.createUser("user@example.com", "plaintext", UserRole.USER);

        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(passwordEncoder.matches("plaintext", result.getPasswordHash())).isTrue();
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void createUserDoesNotStoreRawPassword() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = userService.createUser("user@example.com", "secret123", UserRole.USER);

        assertThat(result.getPasswordHash()).doesNotContain("secret123");
    }

    // ── createOidcUser ─────────────────────────────────────────────────────

    @Test
    void createOidcUserCreatesNewUserWhenNotExists() {
        when(userRepository.findByEmail("oidc@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = userService.createOidcUser("oidc@example.com");

        assertThat(result.getEmail()).isEqualTo("oidc@example.com");
        assertThat(result.getProvider()).isEqualTo(AuthProvider.OIDC);
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getPasswordHash()).isEmpty();
        assertThat(result.getLastLoginAt()).isNotNull();
    }

    @Test
    void createOidcUserUpdatesLastLoginWhenUserExists() {
        AppUser existing = AppUser.builder()
                .email("oidc@example.com")
                .provider(AuthProvider.OIDC)
                .role(UserRole.USER)
                .build();
        when(userRepository.findByEmail("oidc@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = userService.createOidcUser("oidc@example.com");

        assertThat(result.getLastLoginAt()).isNotNull();
    }

    // ── updateLastLogin ─────────────────────────────────────────────────────

    @Test
    void updateLastLoginSetsTimestampForKnownUser() {
        AppUser user = AppUser.builder().email("user@example.com").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateLastLogin("user@example.com");

        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void updateLastLoginDoesNothingForUnknownUser() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        userService.updateLastLogin("ghost@example.com");

        verify(userRepository, never()).save(any());
    }

    // ── deleteUser / findAll / findById / countUsers ───────────────────────

    @Test
    void deleteUserDelegatesToRepository() {
        userService.deleteUser(10L);

        verify(userRepository).deleteById(10L);
    }

    @Test
    void findAllDelegatesToRepository() {
        AppUser user = AppUser.builder().email("a@b.com").build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<AppUser> result = userService.findAll();

        assertThat(result).containsExactly(user);
    }

    @Test
    void findByIdReturnsFromRepository() {
        AppUser user = AppUser.builder().id(5L).email("a@b.com").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        Optional<AppUser> result = userService.findById(5L);

        assertThat(result).contains(user);
    }

    @Test
    void countUsersDelegatesToRepository() {
        when(userRepository.count()).thenReturn(42L);

        long count = userService.countUsers();

        assertThat(count).isEqualTo(42L);
    }
}
