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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.eve.mapper.EveDataSyncStatusMapper;
import top.continew.admin.eve.model.EveDataFreshnessResp;
import top.continew.admin.eve.model.entity.EveDataSyncStatusDO;
import top.continew.admin.eve.model.enums.EveDataFreshnessStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** EVE 数据新鲜度统一状态判定测试。 */
class EveDataFreshnessServiceTest {

    /** 最近失败优先于旧快照有效期；没有成功快照时必须明确为无数据。 */
    @Test
    void shouldClassifyFreshStaleFailedAndNoData() {
        EveDataSyncStatusMapper mapper = mock(EveDataSyncStatusMapper.class);
        EveDataFreshnessService service = new EveDataFreshnessService(mapper);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(record(EveDataFreshnessService.ASSETS, now
            .minusMinutes(5), now.plusMinutes(5), null), record(EveDataFreshnessService.STRUCTURES, now
                .minusHours(2), now.minusMinutes(1), null), record(EveDataFreshnessService.MOON_EXTRACTIONS, now
                    .minusHours(2), now.plusMinutes(5), now.minusMinutes(1))));

        Map<String, EveDataFreshnessResp> result = service.list(10L, 20L)
            .stream()
            .collect(Collectors.toMap(EveDataFreshnessResp::module, item -> item));

        assertThat(result.get(EveDataFreshnessService.ASSETS).status()).isEqualTo(EveDataFreshnessStatus.FRESH);
        assertThat(result.get(EveDataFreshnessService.STRUCTURES).status()).isEqualTo(EveDataFreshnessStatus.STALE);
        assertThat(result.get(EveDataFreshnessService.MOON_EXTRACTIONS).status())
            .isEqualTo(EveDataFreshnessStatus.SYNC_FAILED);
        assertThat(result.get(EveDataFreshnessService.MINING_LEDGER).status())
            .isEqualTo(EveDataFreshnessStatus.NO_DATA);
    }

    /** 构造只包含状态判定所需时间字段的同步状态。 */
    private static EveDataSyncStatusDO record(String module,
                                              LocalDateTime successfulAt,
                                              LocalDateTime expiresAt,
                                              LocalDateTime failureAt) {
        EveDataSyncStatusDO record = new EveDataSyncStatusDO();
        record.setModule(module);
        record.setLastSuccessfulAt(successfulAt);
        record.setSourceExpiresAt(expiresAt);
        record.setLastFailureAt(failureAt);
        record.setFailureCode(failureAt == null ? null : "TRANSIENT");
        return record;
    }
}
