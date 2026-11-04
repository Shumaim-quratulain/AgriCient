package com.agricient.backend.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByAlertIdAndChannel(Long alertId, String channel);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}