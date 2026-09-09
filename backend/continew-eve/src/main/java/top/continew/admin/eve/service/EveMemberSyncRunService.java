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

package top.continew.admin.eve.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.mapper.EveMemberSyncRunMapper;
import top.continew.admin.eve.model.entity.EveMemberSyncRunDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 独立持久化成员同步资源的结果，使部分失败可诊断且不影响已发布名册。
 *
 * @author zhaoyuqing
 */
@Service
@RequiredArgsConstructor
public class EveMemberSyncRunService {

    private final EveMemberSyncRunMapper syncRunMapper;

    /** 在独立事务内记录资源同步结果，主同步事务回滚时仍保留失败分类。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long tenantId,
                       Long corporationRefId,
                       String resource,
                       String status,
                       int recordCount,
                       LocalDateTime sourceExpiresAt,
                       String failureCode,
                       LocalDateTime startedAt) {
        EveMemberSyncRunDO run = new EveMemberSyncRunDO();
        run.setTenantId(tenantId);
        run.setCorporationRefId(corporationRefId);
        run.setResource(resource);
        run.setStatus(status);
        run.setRecordCount(recordCount);
        run.setSourceExpiresAt(sourceExpiresAt);
        run.setFailureCode(failureCode);
        run.setStartedAt(startedAt);
        run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        run.setCreateUser(1L);
        run.setDeleted(0L);
        syncRunMapper.insert(run);
    }
}
