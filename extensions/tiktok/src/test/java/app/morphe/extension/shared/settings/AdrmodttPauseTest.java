/*
 * Copyright 2026 Adrmodtt contributors
 * https://github.com/SysAdminDoc/adrmodtt
 */
package app.morphe.extension.shared.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Looper;

import app.morphe.extension.shared.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivityManager;
import org.robolectric.shadows.ShadowSystemClock;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Three crashed starts in a row, the marker file and the way back, for Pause Adrmodtt. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdrmodttPauseTest {
    private Context context;
    private File record;
    private File streak;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        AdrmodttPause.resetForTests();
        record = new File(context.getFilesDir(), AdrmodttPause.START_RECORD_NAME);
        streak = new File(context.getFilesDir(), AdrmodttPause.CRASH_STREAK_NAME);
        record.delete();
        streak.delete();
        File marker = AdrmodttPause.markerFile(context);
        if (marker != null) marker.delete();
        BaseSettings.SAFE_MODE.save(false);
        BaseSettings.PAUSED.save(false);
    }

    @After public void tearDown() {
        setUp();
        AdrmodttPause.resetForTests();
    }

    /** One start that ended the way [lastRecord] says, then this start. */
    private void start(String lastRecord) {
        AdrmodttPause.resetForTests();
        if (lastRecord == null) record.delete();
        else AdrmodttPause.write(record, lastRecord);
        AdrmodttPause.onProcessStart(context);
    }

    @Test public void threeStartsThatCrashedInTheirFirstMinuteTurnSafeModeOn() {
        start(null);
        assertFalse(Setting.isPaused());

        start("4101 1000 crashed");
        assertEquals("1", AdrmodttPause.read(streak));
        start("4102 2000 crashed");
        assertEquals("2", AdrmodttPause.read(streak));
        assertFalse("two crashes are not yet a loop", Setting.isPaused());

        start("4103 3000 crashed");
        assertTrue("the fourth start runs in safe mode", Setting.isPaused());
        assertEquals(AdrmodttPause.Reason.CRASH_LOOP, AdrmodttPause.reason());
        assertTrue(BaseSettings.SAFE_MODE.savedValue());
        assertEquals("the count starts again once safe mode is on", "0", AdrmodttPause.read(streak));

        // Safe mode holds for every later start until someone turns Adrmodtt back on.
        start(null);
        assertEquals(AdrmodttPause.Reason.CRASH_LOOP, AdrmodttPause.reason());
    }

    @Test public void aStartThatDiedWithoutACrashEndsTheStreak() {
        start("4101 1000 crashed");
        start("4102 2000 crashed");
        assertEquals("2", AdrmodttPause.read(streak));

        // Swiped away inside its first minute: no crash mark, and the streak is over.
        start("4103 3000");
        assertEquals("0", AdrmodttPause.read(streak));
        start("4104 4000 crashed");
        assertEquals("1", AdrmodttPause.read(streak));
        assertFalse(Setting.isPaused());
    }

    @Test public void aStartThatRanForAMinuteClearsItsRecordAndTheStreak() {
        start("4101 1000 crashed");
        assertTrue("this start left its own record", record.exists());
        shadowOf(Looper.getMainLooper()).idleFor(AdrmodttPause.START_WINDOW_MS + 1, TimeUnit.MILLISECONDS);
        assertFalse("the record of a start that survived is gone", record.exists());
        assertEquals("0", AdrmodttPause.read(streak));
    }

    @Test public void aCrashAndroidHoldsOpenPastTheMinuteStillCounts() {
        start("4101 1000 crashed");
        start("4102 2000 crashed");
        assertEquals("2", AdrmodttPause.read(streak));

        // This start crashes off the main thread, and Android holds it open behind "keeps
        // stopping" while the main looper runs on past the minute.
        AdrmodttPause.markCrash();
        AdrmodttPause.crashState = () -> true;
        shadowOf(Looper.getMainLooper()).idleFor(AdrmodttPause.START_WINDOW_MS + 1, TimeUnit.MILLISECONDS);
        assertTrue("the minute cleared the record of a start that crashed", record.exists());
        assertEquals("2", AdrmodttPause.read(streak));

        // The next start judges the record this one left: the third crash in a row.
        AdrmodttPause.resetForTests();
        AdrmodttPause.onProcessStart(context);
        assertEquals(AdrmodttPause.Reason.CRASH_LOOP, AdrmodttPause.reason());
    }

    @Test public void anExceptionAHandlerSwallowedDoesNotEndTheStart() {
        start("4101 1000 crashed");
        assertEquals("1", AdrmodttPause.read(streak));
        AdrmodttPause.markCrash();
        AdrmodttPause.crashState = () -> false;
        shadowOf(Looper.getMainLooper()).idleFor(AdrmodttPause.START_WINDOW_MS + 1, TimeUnit.MILLISECONDS);
        assertFalse("a start still running after the minute kept its record", record.exists());
        assertEquals("0", AdrmodttPause.read(streak));
    }

    @Test public void onlyACrashInsideTheFirstMinuteMarksTheStart() {
        start(null);
        ShadowSystemClock.advanceBy(AdrmodttPause.START_WINDOW_MS - 1000, TimeUnit.MILLISECONDS);
        AdrmodttPause.markCrash();
        assertTrue("a crash inside the minute was not marked",
                AdrmodttPause.read(record).trim().endsWith("crashed"));

        // The clock runs past the minute before the looper gets to clear the record: a crash
        // this late is not a start crash.
        start(null);
        ShadowSystemClock.advanceBy(AdrmodttPause.START_WINDOW_MS + 1000, TimeUnit.MILLISECONDS);
        AdrmodttPause.markCrash();
        assertTrue(record.exists());
        assertFalse("a crash after the minute was marked",
                AdrmodttPause.read(record).trim().endsWith("crashed"));
    }

    /** An exit Android wrote down for the pid at the wall-clock moment [at]. */
    private void exited(int pid, int reason, long at) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        shadowOf(manager).addApplicationExitInfo(ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setProcessName(context.getPackageName())
                .setPid(pid)
                .setReason(reason)
                .setStatus(reason == ApplicationExitInfo.REASON_CRASH ? 1 : 0)
                .setTimestamp(at)
                .build());
    }

    @Test @Config(sdk = 30)
    public void android11CountsCrashesNativeCrashesAndHangsButNotBeingSwipedAway() {
        exited(5101, ApplicationExitInfo.REASON_CRASH, 11_000);
        exited(5102, ApplicationExitInfo.REASON_CRASH_NATIVE, 12_000);
        exited(5103, ApplicationExitInfo.REASON_ANR, 13_000);
        exited(5104, ApplicationExitInfo.REASON_USER_REQUESTED, 14_000);
        exited(5105, ApplicationExitInfo.REASON_LOW_MEMORY, 15_000);

        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "5101 1000"));
        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "5102 1000"));
        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "5103 1000"));
        // Android's own reason wins over the handler's mark: a force stop is not a crash.
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "5104 1000 crashed"));
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "5105 1000"));
        // No reason on record for the process, so the handler's mark decides.
        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "5199 1000 crashed"));
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "5199 1000"));
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "not a pid"));
    }

    @Test @Config(sdk = 30)
    public void android11ReadsTheCrashOffTheFirstRecordSinceTheStart() {
        // Held open behind "keeps stopping": Android wrote the crash down at 30 s and the kill
        // that ended the dialog five minutes later. The first record is the crash.
        exited(6101, ApplicationExitInfo.REASON_CRASH, 1000 + 30_000);
        exited(6101, ApplicationExitInfo.REASON_CRASH, 1000 + 330_000);
        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "6101 1000"));

        // The device slept before the process saw a minute of uptime, so the record was still
        // there when it crashed two minutes of wall time after its start: not a young crash.
        exited(6102, ApplicationExitInfo.REASON_CRASH, 1000 + 120_000);
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "6102 1000 crashed"));

        // A pid handed out again: the crash on record is older than this start, so it belonged
        // to another process, and only the handler's mark can speak for this one.
        exited(6103, ApplicationExitInfo.REASON_CRASH, 500);
        assertFalse(AdrmodttPause.diedYoungFromACrash(context, "6103 1000"));
        assertTrue(AdrmodttPause.diedYoungFromACrash(context, "6103 1000 crashed"));
    }

    @Test public void theMarkerFilePausesEveryStartUntilItIsGone() throws Exception {
        File marker = AdrmodttPause.markerFile(context);
        assertTrue(marker.getParentFile().exists() || marker.getParentFile().mkdirs());
        assertTrue(marker.createNewFile());

        start(null);
        assertEquals(AdrmodttPause.Reason.MARKER_FILE, AdrmodttPause.reason());
        assertTrue(Setting.isPaused());

        assertTrue("turning back on could not remove the marker", AdrmodttPause.turnBackOn(context));
        assertFalse(marker.exists());
        start(null);
        assertEquals(AdrmodttPause.Reason.NONE, AdrmodttPause.reason());
    }

    @Test public void thePauseSwitchPausesTheNextStartAndTurningBackOnUndoesEverything() {
        BaseSettings.PAUSED.save(true);
        start(null);
        assertEquals(AdrmodttPause.Reason.SWITCH, AdrmodttPause.reason());

        BaseSettings.SAFE_MODE.save(true);
        AdrmodttPause.write(streak, "2");
        assertTrue(AdrmodttPause.turnBackOn(context));
        assertFalse(BaseSettings.PAUSED.savedValue());
        assertFalse(BaseSettings.SAFE_MODE.savedValue());
        assertEquals("0", AdrmodttPause.read(streak));
        start(null);
        assertFalse(Setting.isPaused());
    }

    @Test public void theStreakFileSurvivesNonsense() {
        AdrmodttPause.write(streak, "not a number");
        assertEquals(0, AdrmodttPause.parseCount(AdrmodttPause.read(streak)));
        assertEquals(0, AdrmodttPause.parseCount("-4"));
        assertEquals(0, AdrmodttPause.parseCount(null));
        assertNull(AdrmodttPause.read(new File(context.getFilesDir(), "never-written")));
    }
}
