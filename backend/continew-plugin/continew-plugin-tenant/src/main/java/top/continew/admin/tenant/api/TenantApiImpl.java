/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.tenant.api;

import lombok.RequiredArgsConstructor;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.common.api.tenant.TenantApi;
import top.continew.admin.common.api.tenant.TenantDataApi;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.dto.EveTenantCreateDTO;
import top.continew.admin.common.model.dto.EveTenantCreateResultDTO;
import top.continew.admin.common.model.dto.TenantDTO;
import top.continew.admin.tenant.constant.TenantCacheConstants;
import top.continew.admin.tenant.constant.TenantConstants;
import top.continew.admin.tenant.mapper.TenantMapper;
import top.continew.admin.tenant.mapper.PackageMapper;
import top.continew.admin.tenant.model.entity.PackageDO;
import top.continew.admin.tenant.model.entity.TenantDO;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.util.Objects;

/**
 * 租户业务 API 实现
 *
 * @author Charles7c
 * @since 2025/7/23 21:13
 */
@Service
@RequiredArgsConstructor
public class TenantApiImpl implements TenantApi {

    private final TenantMapper baseMapper;
    private final PackageMapper packageMapper;
    private final IdGeneratorProvider idGeneratorProvider;
    private final ObjectProvider<TenantDataApi> tenantDataApis;

    @Override
    public void bindAdminUser(Long tenantId, Long userId) {
        baseMapper.lambdaUpdate().set(TenantDO::getAdminUser, userId).eq(BaseIdDO::getId, tenantId).update();
        refreshTenantCacheAfterCommit(tenantId);
    }

    /** 仅在数据库事务成功提交后刷新租户缓存，无事务时立即执行。 */
    void refreshTenantCacheAfterCommit(Long tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager
            .isSynchronizationActive()) {
            refreshTenantCache(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshTenantCache(tenantId);
            }
        });
    }

    /** 从数据库重新读取租户并写入缓存。 */
    void refreshTenantCache(Long tenantId) {
        TenantDO entity = baseMapper.selectById(tenantId);
        RedisUtils.set(TenantCacheConstants.TENANT_KEY_PREFIX + tenantId, entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EveTenantCreateResultDTO createEveTenant(EveTenantCreateDTO request) {
        ValidationUtils.throwIfNull(request, "EVE 军团租户创建信息不能为空");
        ValidationUtils.throwIfBlank(request.name(), "EVE 军团名称不能为空");
        ValidationUtils.throwIfBlank(request.adminUsername(), "管理员用户名不能为空");
        ValidationUtils.throwIfBlank(request.encryptedPassword(), "管理员密码不能为空");
        ValidationUtils.throwIfNull(request.packageId(), "EVE 租户套餐不能为空");
        PackageDO tenantPackage = packageMapper.selectById(request.packageId());
        ValidationUtils.throwIfNull(tenantPackage, "EVE 租户套餐不存在");
        ValidationUtils.throwIf(DisEnableStatusEnum.DISABLE.equals(tenantPackage.getStatus()), "EVE 租户套餐已禁用");

        TenantDO tenant = new TenantDO();
        tenant.setName(request.name());
        tenant.setCode(generateCode());
        tenant.setDescription("由 EVE 国服军团认领流程创建");
        tenant.setStatus(DisEnableStatusEnum.ENABLE);
        tenant.setAdminUsername(request.adminUsername());
        tenant.setPackageId(request.packageId());
        tenant.setCreateUser(1L);
        tenant.setDeleted(0L);
        baseMapper.insert(tenant);

        TenantDTO dto = new TenantDTO();
        dto.setId(tenant.getId());
        dto.setName(request.name());
        dto.setAdminUsername(request.adminUsername());
        dto.setAdminPassword(request.encryptedPassword());
        dto.setAdminNickname(request.adminNickname());
        dto.setPackageId(request.packageId());
        tenantDataApis.orderedStream().forEach(api -> api.init(dto));

        TenantDO initialized = baseMapper.selectById(tenant.getId());
        Long userId = initialized == null ? null : initialized.getAdminUser();
        ValidationUtils.throwIfNull(userId, "EVE 军团租户管理员初始化失败");
        return new EveTenantCreateResultDTO(tenant.getId(), userId);
    }

    /** 生成唯一租户编码。 */
    private String generateCode() {
        String code;
        do {
            code = idGeneratorProvider.getRequired(TenantConstants.CODE_GENERATOR_KEY).generateAsString();
        } while (baseMapper.lambdaQuery().eq(TenantDO::getCode, code).exists());
        return Objects.requireNonNull(code);
    }
}
