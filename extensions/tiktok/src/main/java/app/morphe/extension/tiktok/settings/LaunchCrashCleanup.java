/*
 * Copyright 2026 Adrmodtt contributors
 * https://github.com/SysAdminDoc/adrmodtt
 */
package app.morphe.extension.tiktok.settings;

import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import app.morphe.extension.tiktok.privacy.DeviceIdentity;
import app.morphe.extension.tiktok.settings.preference.ReleaseNotes;

import java.util.Arrays;

/**
 * TikTok's own recovery from crashes at launch. On the third launch crash in a row it deletes
 * every preferences file whose name isn't on its keep-list, and Adrmodtt's weren't on it: a crash
 * loop erased every setting and the Lab's rules one start before Adrmodtt's safe mode could
 * pause anything.
 *
 * <p>This runs where TikTok builds that list, which can be inside its crash handler and before
 * Adrmodtt has a context, so it touches nothing but the array it is given. The names are
 * compile-time constants, and reading them loads none of the classes they come from.
 */
public final class LaunchCrashCleanup {
    /** Every preferences file Adrmodtt writes. A new one belongs here as well. */
    static final String[] ADRMODTT_FILES = {
            Setting.PREFERENCES_NAME,
            FeatureGateLabStore.PREFS_NAME,
            ReleaseNotes.PREFS_NAME,
            DeviceIdentity.PREFERENCES,
            app.morphe.extension.tiktok.inbox.GroupChats.PREFS_NAME,
            app.morphe.extension.tiktok.comment.StickerRecents.PREFS_NAME,
            app.morphe.extension.tiktok.settings.preference.NewSettingDots.FILE,
    };

    private LaunchCrashCleanup() {
    }

    /** TikTok's keep-list with Adrmodtt's files after it. */
    public static String[] keepAdrmodttFiles(String[] kept) {
        if (kept == null) return ADRMODTT_FILES.clone();
        String[] joined = Arrays.copyOf(kept, kept.length + ADRMODTT_FILES.length);
        System.arraycopy(ADRMODTT_FILES, 0, joined, kept.length, ADRMODTT_FILES.length);
        return joined;
    }
}
