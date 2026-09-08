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

package top.continew.admin.system.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.api.system.EveAccountApi;
import top.continew.admin.common.context.RoleContext;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.context.UserExtraContext;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.GenderEnum;
import top.continew.admin.common.model.dto.EveAccountCreateDTO;
import top.continew.admin.common.model.dto.EveLoginSessionDTO;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.system.mapper.DeptMapper;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.auth.enums.AuthTypeEnum;
import top.continew.admin.system.model.entity.DeptDO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.admin.system.model.resp.ClientResp;
import top.continew.admin.system.service.ClientService;
import top.continew.admin.system.service.OptionService;
import top.continew.admin.system.service.RoleService;
import top.continew.starter.core.util.ServletUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static top.continew.admin.system.enums.PasswordPolicyEnum.PASSWORD_EXPIRATION_DAYS;

/**
 * EVE 注册流程的本站账号与会话实现。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveAccountApiForSystemImpl implements EveAccountApi {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final RoleService roleService;
    private final OptionService optionService;
    private final ClientService clientService;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMember(EveAccountCreateDTO request) {
        ValidationUtils.throwIfNull(request, "EVE 成员账号信息不能为空");
        AtomicReference<Long> result = new AtomicReference<>();
        TenantUtils.execute(request.tenantId(), () -> {
            ValidationUtils.throwIf(userMapper.lambdaQuery()
                .eq(UserDO::getUsername, request.username())
                .exists(), "本站用户名已存在");
            DeptDO dept = deptMapper.lambdaQuery().orderByAsc(DeptDO::getId).one();
            ValidationUtils.throwIfNull(dept, "军团租户部门尚未初始化");
            UserDO user = new UserDO();
            user.setUsername(request.username());
            user.setNickname(request.nickname());
            user.setPassword(SecureUtils.decryptPasswordByRsaPrivateKey(request.encryptedPassword(), "密码解密失败", true));
            user.setGender(GenderEnum.UNKNOWN);
            user.setDescription("通过 EVE 国服身份验证加入军团");
            user.setStatus(DisEnableStatusEnum.ENABLE);
            user.setIsSystem(false);
            user.setPwdResetTime(LocalDateTime.now());
            user.setDeptId(dept.getId());
            user.setCreateUser(1L);
            user.setDeleted(0L);
            userMapper.insert(user);
            result.set(user.getId());
        });
        return result.get();
    }

    /** {@inheritDoc} */
    @Override
    public EveLoginSessionDTO openSession(Long tenantId, Long userId, String clientId) {
        ClientResp client = clientService.getByClientId(clientId);
        ValidationUtils.throwIfNull(client, "客户端不存在");
        ValidationUtils.throwIf(DisEnableStatusEnum.DISABLE.equals(client.getStatus()), "客户端已禁用");
        ValidationUtils.throwIf(!client.getAuthType().contains(AuthTypeEnum.ACCOUNT.getValue()), "该客户端未启用账号认证");
        AtomicReference<EveLoginSessionDTO> result = new AtomicReference<>();
        TenantUtils.execute(tenantId, () -> {
            UserDO user = userMapper.selectById(userId);
            ValidationUtils.throwIfNull(user, "本站账号不存在");
            Set<String> permissions = roleService.listPermissionByUserId(userId);
            Set<RoleContext> roles = roleService.listByUserId(userId);
            UserContext context = new UserContext(permissions, roles, optionService
                .getValueByCode2Int(PASSWORD_EXPIRATION_DAYS.name()));
            BeanUtil.copyProperties(user, context);
            context.setClientId(client.getClientId());
            context.setClientType(client.getClientType());
            context.setTenantId(tenantId);

            SaLoginParameter parameter = new SaLoginParameter();
            parameter.setActiveTimeout(client.getActiveTimeout());
            parameter.setTimeout(client.getTimeout());
            parameter.setDeviceType(client.getClientType());
            parameter.setExtra("clientId", client.getClientId());
            StpUtil.login(userId, parameter.setExtraData(BeanUtil.beanToMap(new UserExtraContext(ServletUtils
                .getRequest()))));
            UserContextHolder.setContext(context);
            result.set(new EveLoginSessionDTO(StpUtil.getTokenValue(), tenantId));
        });
        return result.get();
    }

}
