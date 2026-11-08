/*
 * Copyright 2026 Adrmodtt contributors
 * https://github.com/SysAdminDoc/adrmodtt
 */
package com.ss.android.ugc.aweme.compliance.api.model;

/** Stands in for the user details TikTok's compliance settings save, isMinor among them. */
public final class UserDetailsInfoBean {
    public final Boolean isMinor;

    public UserDetailsInfoBean(Boolean isMinor) {
        this.isMinor = isMinor;
    }
}
