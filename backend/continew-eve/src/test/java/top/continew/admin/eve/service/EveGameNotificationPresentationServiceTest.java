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

import org.junit.jupiter.api.Test;
import top.continew.admin.eve.model.EveGameNotificationPresentation;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 游戏通知中文展示转换测试。
 *
 * @author zhaoyuqing
 */
class EveGameNotificationPresentationServiceTest {

    private final EveGameNotificationPresentationService service = new EveGameNotificationPresentationService(new EveStaticNameReference());

    /** 月矿报文应显示建筑、星系、矿物和时间，不能暴露各类内部数字 ID。 */
    @Test
    void shouldPresentMoonMiningNotificationAsChineseDetails() {
        String content = """
            autoTime: 134341020613742429
            moonID: 40090362
            oreVolumeByType:
              45492: 1756367.2880514162
              45493: 3275499.3786152513
            readyTime: 134340912613742429
            solarSystemID: 30001421
            startedByLink: \"<a href=\\\"showinfo:1383//2112331875\\\">小小小果冻</a>\"
            structureName: \"奥塔列托 - 天 权\"
            """;

        EveGameNotificationPresentation result = service.present("MoonminingExtractionStarted", content, Map.of());

        assertThat(result.summary()).isEqualTo("建筑「奥塔列托 - 天 权」已开始提取");
        assertThat(result.details()).extracting(item -> item.label() + "=" + item.value())
            .contains("建筑=奥塔列托 - 天 权", "所在星系=奥塔列托", "操作人=小小小果冻", "矿物=沥青 1,756,367.29 m³；柯石英 3,275,499.38 m³", "预计可开采时间=2026年9月17日 04:01");
        assertThat(result.details().toString()).doesNotContain("45492", "45493", "30001421", "2112331875");
    }

    /** 未知通知也只展示可解释内容，不能把 YAML 键值或内部 ID 当作用户正文。 */
    @Test
    void shouldHideInternalIdsForUnknownNotification() {
        EveGameNotificationPresentation result = service.present("unknown notification type (6040)", """
            solarsystemID: 30001421
            structureID: &id001 1019984659987
            structureTypeID: 35835
            """, Map.of());

        assertThat(result.summary()).isEqualTo("收到一条建筑相关游戏通知");
        assertThat(result.details()).extracting(item -> item.label() + "=" + item.value())
            .contains("建筑类型=阿塔诺", "所在星系=奥塔列托");
        assertThat(result.details().toString()).doesNotContain("30001421", "1019984659987", "35835");
    }
}
