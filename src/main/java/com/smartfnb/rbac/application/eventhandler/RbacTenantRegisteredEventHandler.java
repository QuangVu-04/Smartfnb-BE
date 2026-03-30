package com.smartfnb.rbac.application.eventhandler;

import com.smartfnb.auth.domain.event.TenantRegisteredEvent;
import com.smartfnb.rbac.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xử lý sự kiện TenantRegisteredEvent từ module Auth.
 * Khi một Tenant mới được tạo, tự động khởi tạo Role OWNER với TẤT CẢ các quyền hệ thống.
 * Gán Role OWNER đó cho user tạo Tenant.
 *
 * @author SmartF&B Team
 * @since 2026-03-27
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacTenantRegisteredEventHandler {

    private final RoleJpaRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionJpaRepository rolePermissionRepository;
    private final UserRoleJpaRepository userRoleRepository;

    /**
     * Lắng nghe đồng bộ (synchronous) để đảm bảo Role OWNER được tạo ngay trong cùng transaction
     * của quá trình RegisterTenantCommandHandler.
     *
     * @param event sự kiện chứa thông tin Tenant mới.
     */
    @EventListener
    @Transactional
    public void onTenantRegistered(TenantRegisteredEvent event) {
        log.info("RBAC Module đang khởi tạo Role OWNER cho tenantId={}", event.tenantId());

        // 1. Lấy tất cả quyền hệ thống (được seed từ V1)
        List<PermissionJpaEntity> allPermissions = permissionRepository.findAll();
        if (allPermissions.isEmpty()) {
            log.warn("CẢNH BÁO: Bảng permissions đang trống! Vui lòng kiểm tra Flyway.");
        }

        // 2. Tạo Role OWNER (isSystem = true)
        RoleJpaEntity ownerRole = RoleJpaEntity.builder()
                .tenantId(event.tenantId())
                .name("OWNER")
                .description("Chủ cửa hàng (Quản trị viên toàn quyền)")
                .isSystem(true)
                .build();
        ownerRole = roleRepository.save(ownerRole);

        // 3. Gắn tất cả quyền cho Role OWNER
        UUID roleId = ownerRole.getId();
        List<RolePermissionJpaEntity> rolePermissions = allPermissions.stream()
                .map(p -> new RolePermissionJpaEntity(roleId, p.getId()))
                .collect(Collectors.toList());
        rolePermissionRepository.saveAll(rolePermissions);

        // 4. Gắn Role OWNER cho User chủ cửa hàng vừa được tạo
        UserRoleJpaEntity userRole = new UserRoleJpaEntity(
                event.ownerUserId(), 
                roleId, 
                event.ownerUserId(), // tự cấp cho mình
                LocalDateTime.now() 
        );
        userRoleRepository.save(userRole);

        log.info("Khởi tạo thành công Role OWNER (kèm {} quyền) cho userId={} của tenantId={}", 
                rolePermissions.size(), event.ownerUserId(), event.tenantId());
    }
}
