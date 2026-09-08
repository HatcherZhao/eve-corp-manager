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

package top.continew.admin.eve.registration;

/**
 * 表示注册授权后的角色归属或管理资格已经变化。
 *
 * @author zhaoyuqing
 */
public class RegistrationQualificationChangedException extends IllegalStateException {

    /** 创建不包含敏感上游数据的资格变化异常。 */
    public RegistrationQualificationChangedException(String message) {
        super(message);
    }
}
