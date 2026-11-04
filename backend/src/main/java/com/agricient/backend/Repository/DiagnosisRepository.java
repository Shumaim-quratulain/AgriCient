package com.agricient.backend.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.Diagnosis;

import java.time.LocalDateTime;
import java.util.List;
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    List<Diagnosis> findAllByOrderByCreatedAtDesc();
    List<Diagnosis> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    void deleteByOwnerId(Long ownerId);
    List<Diagnosis> findByOwnerIdAndLandIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long ownerId, Long landId, LocalDateTime after);
    List<Diagnosis> findByTopLabelStartingWithAndCreatedAtAfterOrderByCreatedAtDesc(
        String prefix, LocalDateTime after);
}