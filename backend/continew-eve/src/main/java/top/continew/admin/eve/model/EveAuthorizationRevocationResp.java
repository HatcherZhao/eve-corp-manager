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

import top.continew.admin.eve.client.OAuthFailureCode;

/**
 * EVE 授权撤销结果，本地令牌始终已清除且仅暴露脱敏上游失败分类。
 *
 * @author zhaoyuqing
 */
public record EveAuthorizationRevocationResp(Status status, OAuthFailureCode failureCode) {

    /** 撤销完成状态。 */
    public enum Status {
        /** 上游与本地均已确认撤销。 */
        UPSTREAM_REVOKED,
        /** 本地已撤销，但上游撤销结果未确认。 */
        LOCAL_REVOKED_UPSTREAM_UNCONFIRMED
    }

    /** 构造上游已确认撤销结果。 */
    public static EveAuthorizationRevocationResp upstreamRevoked() {
        return new EveAuthorizationRevocationResp(Status.UPSTREAM_REVOKED, null);
    }

    /** 构造本地已撤销但上游未确认结果。 */
    public static EveAuthorizationRevocationResp upstreamUnconfirmed(OAuthFailureCode failureCode) {
        return new EveAuthorizationRevocationResp(Status.LOCAL_REVOKED_UPSTREAM_UNCONFIRMED, failureCode);
    }
}
