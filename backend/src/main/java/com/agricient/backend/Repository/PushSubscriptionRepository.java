package com.agricient.backend.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    void deleteByEndpointHashAndUserId(String endpointHash, Long userId);
}
