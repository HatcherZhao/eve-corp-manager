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

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.continew.admin.tenant.mapper.PackageMapper;
import top.continew.admin.tenant.mapper.TenantMapper;
import top.continew.admin.tenant.model.entity.TenantDO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户缓存事务边界测试。
 *
 * @author zhaoyuqing
 */
class TenantApiImplTest {

    private TenantApiImpl service;

    /** 创建可隔离 Redis 副作用的服务替身。 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantMapper tenantMapper = mock(TenantMapper.class);
        LambdaUpdateChainWrapper<TenantDO> updateWrapper = mock(LambdaUpdateChainWrapper.class);
        when(tenantMapper.lambdaUpdate()).thenReturn(updateWrapper);
        when(updateWrapper.set(any(), any())).thenReturn(updateWrapper);
        when(updateWrapper.eq(any(), any())).thenReturn(updateWrapper);
        when(updateWrapper.update()).thenReturn(true);
        service = spy(new TenantApiImpl(tenantMapper, mock(PackageMapper.class), mock(IdGeneratorProvider.class), mock(ObjectProvider.class)));
        doNothing().when(service).refreshTenantCache(10L);
    }

    /** 清理测试手工建立的事务同步状态。 */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /** 真实事务提交前不刷新缓存，提交后才执行。 */
    @Test
    void shouldRefreshTenantCacheAfterCommit() {
        beginTransactionSynchronization();

        service.bindAdminUser(10L, 20L);

        verify(service, never()).refreshTenantCache(10L);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(service).refreshTenantCache(10L);
    }

    /** 事务回滚时不得写入已回滚的租户缓存。 */
    @Test
    void shouldNotRefreshTenantCacheAfterRollback() {
        beginTransactionSynchronization();

        service.bindAdminUser(10L, 20L);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(service, never()).refreshTenantCache(10L);
    }

    /** 无事务调用时保持立即刷新缓存的原有行为。 */
    @Test
    void shouldRefreshTenantCacheImmediatelyWithoutTransaction() {
        service.bindAdminUser(10L, 20L);

        verify(service).refreshTenantCache(10L);
    }

    /** 模拟 Spring 已开启的真实事务同步上下文。 */
    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
