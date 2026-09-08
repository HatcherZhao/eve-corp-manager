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

package top.continew.admin.controller.eve;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import top.continew.admin.eve.model.EveAuthorizationRevocationResp;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVE 权限树 Controller 契约测试。
 *
 * @author zhaoyuqing
 */
class EvePermissionControllerTest {

    /** 权限树只暴露无参数 GET 查询，不提供修改或授予接口。 */
    @Test
    void shouldExposeReadOnlyPermissionTree() throws Exception {
        Method tree = EvePermissionController.class.getDeclaredMethod("tree");

        assertThat(tree.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(tree.getAnnotation(GetMapping.class).value()).containsExactly("/tree");
        assertThat(tree.getAnnotation(PostMapping.class)).isNull();
        assertThat(tree.getParameterCount()).isZero();
        assertThat(Arrays.stream(EvePermissionController.class.getDeclaredMethods())
            .filter(method -> method.getName().toLowerCase().contains("tree"))).allMatch(method -> method
                .getAnnotation(PostMapping.class) == null);
    }

    /** 主动撤销仅接收路径授权 ID，实际归属由服务端会话校验。 */
    @Test
    void shouldExposeOwnedAuthorizationRevocation() throws Exception {
        Method revoke = EvePermissionController.class.getDeclaredMethod("revoke", Long.class);

        assertThat(revoke.getAnnotation(PostMapping.class).value()).containsExactly("/{authorizationId}/revoke");
        assertThat(revoke.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        assertThat(revoke.getReturnType()).isEqualTo(EveAuthorizationRevocationResp.class);
    }
}
