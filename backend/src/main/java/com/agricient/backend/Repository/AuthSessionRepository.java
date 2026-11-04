package com.agricient.backend.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agricient.backend.Model.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
}
