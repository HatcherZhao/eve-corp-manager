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

package top.continew.admin.config.exception;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.extra.spring.SpringUtil;
import com.feiniaojin.gracefulresponse.api.ResponseStatusFactory;
import com.feiniaojin.gracefulresponse.defaults.DefaultResponseStatusFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import top.continew.admin.common.config.exception.GlobalSaTokenExceptionHandler;
import top.continew.starter.web.model.R;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sa-Token 全局异常处理器 HTTP 状态测试。
 *
 * @author zhaoyuqing
 */
class GlobalSaTokenExceptionHandlerTest {

    private static GenericApplicationContext applicationContext;

    private final GlobalSaTokenExceptionHandler handler = new GlobalSaTokenExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/protected");

    /** 初始化响应模型依赖的最小 Spring 容器。 */
    @BeforeAll
    static void setUpApplicationContext() {
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ResponseStatusFactory.class, DefaultResponseStatusFactoryImpl::new);
        applicationContext.refresh();
        new SpringUtil().postProcessBeanFactory(applicationContext.getBeanFactory());
    }

    /** 关闭测试容器。 */
    @AfterAll
    static void closeApplicationContext() {
        applicationContext.close();
    }

    /** 权限不足必须同时返回真实 HTTP 403 和业务码 403。 */
    @Test
    void shouldReturnHttpForbiddenForMissingPermission() {
        ResponseEntity<R> response = handler
            .handleNotPermissionException(new NotPermissionException("eve:test:view"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("403");
    }

    /** 角色不足必须同时返回真实 HTTP 403 和业务码 403。 */
    @Test
    void shouldReturnHttpForbiddenForMissingRole() {
        ResponseEntity<R> response = handler.handleNotRoleException(new NotRoleException("corp_admin"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("403");
    }
}
