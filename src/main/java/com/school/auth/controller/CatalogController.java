package com.school.auth.controller;

import com.school.auth.dto.AppDto.AppConfigResponse;
import com.school.auth.dto.RoleDto.PermissionResponse;
import com.school.auth.service.DedicatedAppService;
import com.school.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final RoleService roleService;
    private final DedicatedAppService appService;

    /** Permission catalog, for the custom-role editor. */
    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return roleService.permissionCatalog();
    }

    /** Public start-up config of a dedicated app (branding, pinned branches, minimum version). */
    @GetMapping("/apps/{clientId}/config")
    public ResponseEntity<AppConfigResponse> appConfig(@PathVariable String clientId) {
        return ResponseEntity.of(appService.config(clientId));
    }
}
