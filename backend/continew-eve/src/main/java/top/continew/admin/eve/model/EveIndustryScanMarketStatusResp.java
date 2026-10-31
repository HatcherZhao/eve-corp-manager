/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package top.continew.admin.eve.model;

/** 工业机会扫描所需市场行情的准备进度。 */
public record EveIndustryScanMarketStatusResp(int totalTypeCount,
                                               int readyTypeCount,
                                               int pendingTypeCount,
                                               int failedTypeCount,
                                               boolean ready) {
}
