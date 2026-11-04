package com.agricient.backend.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.Land;

public interface LandRepository extends JpaRepository<Land, Long> {
    List<Land> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Land> findByIdAndOwnerId(Long id, Long ownerId);
}
