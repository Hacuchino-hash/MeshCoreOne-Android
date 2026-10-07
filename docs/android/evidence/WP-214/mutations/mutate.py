import os, subprocess, sys, glob, xml.etree.ElementTree as ET
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../../../android')
SRC=ROOT+'/core/services/src/main/kotlin/com/meshcoreone/android/core/services/sync/'
MUT=[
 ("backoff", "SyncRetryController.kt", "fun channelRetryDelay(attempt: Int): Duration = maxOf(CHANNEL_RETRY_INITIAL_DELAY, (2 shl (attempt - 1)).seconds)", "fun channelRetryDelay(attempt: Int): Duration = CHANNEL_RETRY_INITIAL_DELAY"),
 ("skip-window-esp32", "DevicePlatformSyncThrottling.kt", "DevicePlatform.ESP32 -> 30.seconds", "DevicePlatform.ESP32 -> Duration.ZERO"),
 ("channel-skip-attempted", "SyncCoordinatorChannels.kt", "return isRecent(config.lastCleanChannelSync) || isRecent(config.lastAttemptedChannelSync)", "return isRecent(config.lastCleanChannelSync)"),
 ("watermark-overlap", "SyncCoordinatorContacts.kt", "private const val INCREMENTAL_SINCE_OVERLAP_SECONDS = 1L", "private const val INCREMENTAL_SINCE_OVERLAP_SECONDS = 0L"),
 ("watermark-recovery-latch", "SyncCoordinatorContacts.kt", "    if (ranInvalidWatermarkRecovery) locked { invalidWatermarkRecoveryRadioID = radioId }\n    return SyncAdvertContactSyncOutcome.SYNCED", "    return SyncAdvertContactSyncOutcome.SYNCED"),
 ("timestamp-future-boundary", "SyncCoordinator.kt", "timestampSeconds > receiveSeconds + TIMESTAMP_TOLERANCE_FUTURE_SECONDS", "timestampSeconds >= receiveSeconds + TIMESTAMP_TOLERANCE_FUTURE_SECONDS - 1"),
 ("resync-max-attempts", "SyncRetryController.kt", "if (attempt >= MAX_RESYNC_ATTEMPTS) {", "if (attempt >= MAX_RESYNC_ATTEMPTS + 100) {"),
 ("resync-identity-fence", "SyncRetryController.kt", "host.connectionIntent.wantsConnection && host.connectionState.isOperational && host.currentServices === services", "host.connectionIntent.wantsConnection && host.connectionState.isOperational"),
 ("resync-bracket-catchall", "SyncRetryController.kt", "if (bracketOpen) withContext(NonCancellable) { coordinator.endResyncActivity(false) }", "Unit"),
 ("message-dedup", "SyncCoordinatorMessageHandlers.kt", "dependencies.dataStore.fetchMessage(deduplicationKey, radioId) ?: return false", "dependencies.dataStore.fetchMessage(deduplicationKey + \"x\", radioId) ?: return false"),
 ("claim-generation", "SyncCoordinatorSync.kt", "if (syncClaimGeneration == claim) isSyncInProgress = false", "isSyncInProgress = false"),
 ("advert-wait", "SyncCoordinatorSync.kt", "): FullSyncResult {\n    waitForAdvertContactSync()\n    val claim = tryClaimSync()", "): FullSyncResult {\n    val claim = tryClaimSync()"),
 ("foreign-cancellation", "SyncCoordinatorSync.kt", "        if (isCallerCancellation(failure)) {\n            setState(SyncState.Idle)", "        if (failure is CancellationException) {\n            setState(SyncState.Idle)"),
 ("disconnect-noncancellable", "SyncCoordinatorSync.kt", "withContext(NonCancellable) { resetForDisconnect(notificationService) }", "resetForDisconnect(notificationService)"),
 ("retry-channels-cancel-bracket", "SyncCoordinatorChannels.kt", "if (bracketOpen) withContext(NonCancellable) { callSyncActivityEnded(false) }", "Unit"),
]
def run():
    r=subprocess.run("timeout 900 ./gradlew :core:services:test --rerun --console=plain -q", shell=True, cwd=ROOT, capture_output=True, text=True)
    fails=[]
    for p in glob.glob(ROOT+'/core/services/build/test-results/test/TEST-*.xml'):
        for tc in ET.parse(p).getroot().findall('testcase'):
            if tc.find('failure') is not None or tc.find('error') is not None: fails.append(tc.get('name'))
    return r.returncode, fails, ('e: ' in r.stdout+r.stderr)
only=sys.argv[1:]
for name,f,old,new in MUT:
    if only and name not in only: continue
    path=SRC+f; orig=open(path).read()
    assert orig.count(old)==1, (name, orig.count(old))
    open(path,'w').write(orig.replace(old,new))
    try: rc,fails,ce=run()
    finally: open(path,'w').write(orig)
    print(f"MUTATION {name}: rc={rc} compile_error={ce} failing={len(fails)}")
    for n in fails[:6]: print("   -", n)
    sys.stdout.flush()
