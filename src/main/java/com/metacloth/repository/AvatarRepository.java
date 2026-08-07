package com.metacloth.repository;

import com.metacloth.domain.entity.Avatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {
    List<Avatar> findByActiveTrueOrderByIdAsc();
}
