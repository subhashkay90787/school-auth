package com.school.auth.controller;

import com.school.auth.dto.DirectoryDto.*;
import com.school.auth.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Called by school-app-server with a client_credentials token (scope directory.read):
 * nightly refresh of its thin directory cache, and "who holds role X in branch Y" for approval routing.
 */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalDirectoryController {

    private final DirectoryService directoryService;

    @GetMapping("/directory/schools")
    public SchoolsPage schools(@RequestParam List<Long> ids) {
        return directoryService.schools(ids);
    }

    @GetMapping("/directory/schools/{schoolId}/users")
    public UsersPage users(@PathVariable Long schoolId) {
        return directoryService.users(schoolId);
    }

    @GetMapping("/schools/{schoolId}/role-holders")
    public List<RoleHolder> roleHolders(@PathVariable Long schoolId, @RequestParam String roleCode) {
        return directoryService.roleHolders(schoolId, roleCode);
    }
}
