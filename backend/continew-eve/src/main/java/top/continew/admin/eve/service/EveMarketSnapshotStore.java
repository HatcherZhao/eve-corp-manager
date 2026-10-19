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
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.eve.mapper.EveMarketSnapshotMapper;
import top.continew.admin.eve.model.entity.EveMarketSnapshotDO;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 将已完整读取的市场订单原子发布；网络请求不进入数据库事务。 */
@Service
@RequiredArgsConstructor
public class EveMarketSnapshotStore {

    private final EveMarketSnapshotMapper snapshotMapper;

    /** 分批合并本轮订单并清空消失物品；任意一批失败时整轮回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceOrders(List<EveMarketSnapshotDO> snapshots, LocalDateTime now) {
        List<EveMarketSnapshotDO> sorted = snapshots.stream()
            .sorted(Comparator.comparing(EveMarketSnapshotDO::getTypeId))
            .toList();
        snapshotMapper.clearMissingOrders(sorted.stream().map(EveMarketSnapshotDO::getTypeId).toList(), now);
        for (int start = 0; start < sorted.size(); start += 500) {
            snapshotMapper.upsertOrders(sorted.subList(start, Math.min(start + 500, sorted.size())));
        }
    }
}
