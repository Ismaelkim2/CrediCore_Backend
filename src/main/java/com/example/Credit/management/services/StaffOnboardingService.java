
package com.example.Credit.management.services;

import com.example.Credit.management.Model.CompleteInvitationRequest;
import com.example.Credit.management.Model.StaffInvitationRequest;

import java.util.List;
import java.util.Map;

public interface StaffOnboardingService {

    // Create a staff login, profile and invitation
    Map<String, Object> createStaffInvitation(
            StaffInvitationRequest request
    );

    // Validate an invitation before registration
    Map<String, Object> validateInvitation(String token);

    // Complete staff registration and set password
    Map<String, Object> completeInvitation(
            CompleteInvitationRequest request
    );

    // Retrieve staff directory with linked user information
    List<Map<String, Object>> getAllStaff();

    // Generate a fresh invitation and invalidate the previous one
    Map<String, Object> regenerateStaffInvitation(
            Long userId,
            boolean sendEmail
    );
}
