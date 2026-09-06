/*
 * Copyright 2026 EVE Corp Manager contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package top.continew.admin.eve.model;

import java.util.List;

/** Workspace capabilities; no game data or credentials are exposed. */
public record WorkspaceResp(String server, String authorizationStatus, List<Capability> capabilities) {
    public record Capability(String key, String title, String description, String status) {
    }
}
