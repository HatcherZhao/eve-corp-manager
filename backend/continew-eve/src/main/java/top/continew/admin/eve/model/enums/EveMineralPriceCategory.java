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

package top.continew.admin.eve.model.enums;

/**
 * 矿物价格目录的固定分类。
 *
 * @author zhaoyuqing
 */
public enum EveMineralPriceCategory {
    /** 当前军团月矿观察者账本中出现过的月矿品类。 */
    CORPORATION_MOON,
    /** evedata 标注为标准矿石的全部普通矿物。 */
    ORE,
    /** evedata 标注为冰矿的全部冰矿。 */
    ICE,
    /** evedata 标注为卫星矿石的全部月矿。 */
    MOON
}
