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

package top.continew.admin.eve.model;

import java.time.LocalDateTime;

/**
 * EVE 静态资料导入结果。
 *
 * @param sourceFileName  来源文件名
 * @param sourceUpdatedAt 来源资料更新时间
 * @param typeCount       类型记录数
 * @param locationCount   位置记录数
 * @param imported        是否实际执行了导入
 * @author zhaoyuqing
 */
public record EveStaticReferenceImportResp(String sourceFileName, LocalDateTime sourceUpdatedAt, int typeCount,
                                           int locationCount, boolean imported) {
}
