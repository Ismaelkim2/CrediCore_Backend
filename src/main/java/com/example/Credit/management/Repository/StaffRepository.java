
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    // Find a staff profile linked to a central login user
    Optional<Staff> findByUser_Id(Long userId);

    // Ensure one user cannot have multiple staff profiles
    boolean existsByUser_Id(Long userId);

    // Check for an existing staff email
    boolean existsByEmailIgnoreCase(String email);
}
