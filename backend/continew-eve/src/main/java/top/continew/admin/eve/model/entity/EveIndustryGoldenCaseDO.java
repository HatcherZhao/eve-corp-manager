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

package top.continew.admin.eve.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.BaseDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 使用国服游戏内真实任务结果校验工业公式的 Golden Case。 */
@Data
@TableName("eve_industry_golden_case")
public class EveIndustryGoldenCaseDO extends BaseDO {
    /** 租户 ID。 */
    private Long tenantId;
    /** 样例名称。 */
    private String name;
    /** 蓝图活动记录 ID。 */
    private Long blueprintId;
    /** 静态工业数据版本 ID。 */
    private Long staticVersionId;
    /** 生产配置 ID。 */
    private Long configId;
    /** 设施配置 ID。 */
    private Long facilityId;
    /** 任务流程数。 */
    private Integer runs;
    /** 蓝图 ME。 */
    private Integer materialEfficiency;
    /** 蓝图 TE。 */
    private Integer timeEfficiency;
    /** 校验时全部可变输入快照。 */
    private String inputSnapshotJson;
    /** 游戏内实际材料数量。 */
    private String expectedMaterialsJson;
    /** 游戏内实际任务时长秒数。 */
    private BigDecimal expectedTimeSeconds;
    /** 游戏内实际安装费。 */
    private BigDecimal expectedInstallCost;
    /** 系统计算材料数量。 */
    private String calculatedMaterialsJson;
    /** 系统计算任务时长秒数。 */
    private BigDecimal calculatedTimeSeconds;
    /** 系统计算安装费。 */
    private BigDecimal calculatedInstallCost;
    /** 单项材料数量容差。 */
    private Long materialTolerance;
    /** 时间容差秒数。 */
    private BigDecimal timeToleranceSeconds;
    /** 安装费容差。 */
    private BigDecimal installCostTolerance;
    /** PENDING、PASSED 或 FAILED。 */
    private String validationStatus;
    /** 逐项差异与失败原因。 */
    private String validationDetailJson;
    /** 最近校验时间。 */
    private LocalDateTime validatedAt;
}
