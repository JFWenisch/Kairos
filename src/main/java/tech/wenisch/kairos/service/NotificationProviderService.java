package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.repository.NotificationPolicyRepository;
import tech.wenisch.kairos.repository.NotificationProviderRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProviderService {

    private final NotificationProviderRepository notificationProviderRepository;
    private final NotificationPolicyRepository notificationPolicyRepository;
    private final NotificationDispatchService notificationDispatchService;

    public List<NotificationProvider> findAll() {
        return notificationProviderRepository.findAll();
    }

    public Optional<NotificationProvider> findById(Long id) {
        return notificationProviderRepository.findById(id);
    }

    public NotificationProvider save(NotificationProvider provider) {
        return notificationProviderRepository.save(provider);
    }

    @Transactional
    public void delete(Long id) {
        notificationProviderRepository.findById(id).ifPresent(provider -> {
            notificationPolicyRepository.deleteByProvider(provider);
            notificationProviderRepository.delete(provider);
            log.info("Deleted notification provider '{}' (id={}) and all associated policies", provider.getName(), id);
        });
    }

    public void test(Long id) {
        NotificationProvider provider = notificationProviderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
        notificationDispatchService.testProvider(provider);
    }
}
