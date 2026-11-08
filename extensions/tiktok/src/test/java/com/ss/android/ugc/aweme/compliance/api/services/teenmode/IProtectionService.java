/*
 * Copyright 2026 Adrmodtt contributors
 * https://github.com/SysAdminDoc/adrmodtt
 */
package com.ss.android.ugc.aweme.compliance.api.services.teenmode;

import com.ss.android.ugc.aweme.compliance.api.model.UserDetailsInfoBean;

/** Stands in for TikTok's protection service: one method hands out the saved user details. */
public interface IProtectionService {
    UserDetailsInfoBean details();

    boolean sleepHourEnabled();
}
