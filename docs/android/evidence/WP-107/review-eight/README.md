# Eight independently reviewed session repairs

**Repair in progress; final positive protocol/root proof is not yet claimed.**
The coordinator's read-only review was statically pinned to historical
`ce93bfaa22b438aed7e8b7a903a611270a985cd3` /0394. It did not execute tests
or approve the source. The same code survived the dc15 rebase; actual
pre-repair execution is recorded below rather than inferred from that review.

| Finding | Scoped root-cause repair | Executable family |
| --- | --- | --- |
| R1: cancelled owning job prevented physical teardown | Only the retained bounded teardown Deferred is independent of the cancelled owning Job. Ordinary generation jobs remain structured/cancellable. Stop awaits the real physical close/release before another session acquires. | Explicit owning-job cancel/join, stop from an active caller, exact disconnect count and a real new handshake on the same transport |
| R2: public arbitrary matcher quarantined only a synthetic custom family | Unresolved arbitrary response context quarantines every exchange, including fire-and-forget sends; arbitrary matchers also cannot consume an unresolved typed family. Confirmed arbitrary matches remain reusable. | Late1111 battery rejected by typed successor, no new write, reverse typed-to-arbitrary admission and confirmed1111/2222 independent exchanges |
| R3: contact-stream send failure/cancellation lost uncertainty | Record each possible transport write at its actual invocation boundary, quarantine contacts and untagged errors on every unconfirmed path, and remove/join owned pending state. A consumed explicit terminal device rejection remains reusable. | Actual GET_CONTACTS write followed by failure or cancellation, late complete orphan stream, and a separate confirmed-rejection/fresh-fetch scenario |
| R4: eager contactsEnd advanced incomplete synchronization cursor | Receive tracking never commits completeness. Only a validated current-generation fetch can advance the cursor; a full baseline/header/count and unchanged invalidation generation are required. Incremental completion needs the matching valid prior baseline. Incomplete/late endings preserve dirty state and force a full fetch. | Original missing unchanged C is recovered, late/unsolicited end cannot clean, valid incremental cursor, unknown incremental baseline and concurrent invalidation |
| R5: possibly-written binary failure/cancellation omitted error quarantine | All unconfirmed possible attempts, including failed resends, retain messageSent plus untagged-error uncertainty. Confirmed pre-messageSent firmware rejection is terminal and reusable. | Actual status write failure/cancellation against both following setter/message poll, confirmed rejection and a possibly-written failed resend with an earlier genuine routed reply |
| R6: error quarantine blocked persistent temporary-route rollback before send | A bounded rollback-only exchange admits resetPath and accepts only bare OK when old errors are ambiguous. Ambiguous errors are diagnosed, not attributed to restore; primary failure and quarantine survive. Unambiguous restore errors remain typed. | Exact update/anonymous/reset sequence despite missing send receipt, old error before real bare OK, bounded ambiguous-error expiry and caller cancellation before/during rollback |
| R7: failed logical restart overwrote the physical close receipt | Physical ownership/close receipt is retained independently of the most recent logical generation until confirmed disconnection/release; stop handles both logical and physical owners. | Actual receive-child failure while still connected, repeated refused restarts, exact old close count and a fresh session on the same transport |
| R8: invalid constructor inputs leaked inaccessible parented jobs | Validate every timeout before any parented lifecycle/generation Job allocation. No connection or cleanup callback is needed to recover invalid construction. | Ten real zero/negative/NaN/infinite configurations caught in a bounded structured scope with no residual children or transport calls |

`ReviewedSessionRegressionTest` retains all22 original failing assertions and
adds12 positive/edge contracts, for34 concrete regression cases. The original
85 catalog IDs and all4,445 baseline assertions are untouched. Two existing
supplementary cache tests now establish a clean baseline through an actual
complete GET_CONTACTS exchange, not an unsolicited contactsEnd test shortcut.
No original expected packet, fixture, source pin, skipped test or policy was
changed to conceal a finding.

## Actual pre-repair execution

Meaningful regression commit:
`0a94e1386f41b7b35dcb150574add0964af79a21`.
Normal protocol run **37212608599**, attempt **1**, executed the actual
candidate on independent Windows/Linux hosted runners. Each host discovered
**4,696 cases /57 suites**: all4,674 historical cases passed, and all22
new finding regressions **failed**;0 errors/skips. The complete actual
uploaded reports and full22-case regression XML were downloaded and parsed.
Every R1 through R8 has concrete failing execution, not a manufactured
expected counter or an assertion-bearing filename alone.

| Actual pre-repair artifact | Artifact ID | GitHub upload digest |
| --- | ---: | --- |
| protocol-linux-37212608599-1 |11307670446|`2c12ddc66f93b576ff0a32a8b40dd0a1adb1e082cc764846b351bb4a6a3d2eab`|
| protocol-windows-37212608599-1 |11307481192|`bd8a9a91f8a372c6ab56760b2ee6d48727847b69fd9283e55b7cd605147d3db0`|

The Linux ZIP digest was independently matched to downloaded ZIP bytes.
Both raw regression XML files were independently parsed, including all22
actual failed outcomes and their full stack/log nodes. The Windows ZIP
verification was interrupted by the host's native process-start OOM; its
upload digest is an actual GitHub metadata fact, not a claim that the missing
independent byte verification succeeded. Full raw bundles/failure logs are
retained in the owning session's artifacts. Remaining digest/positive proof
must be completed before final handoff.

## Resource hold and separate root repair

The initial local pre-repair compiler failed to allocate1MiB of native
memory **before** test discovery; it is not counted as executing regressions.
The coordinator then imposed a local resource hold. No new local
Gradle/JVM build is launched under that hold. A read-only probe found own
private Gradle daemon PID52608 still in JVM shutdown; its own log recorded
stop/address removal. The authorized own-private `--stop` reported
`No Gradle daemons are running`. No global daemon, other PID/worktree/cache,
process-name kill, dependency pin or memory-policy change was used.
A subsequent PID/GitHub CLI probe itself failed for OS paging exhaustion;
it cannot establish that the last observed PID had exited.

The same coordinator is arranging an independently scoped WP-201
collector-integration repair for the actual root37210533774 failure.
The WP-107 worker does not change or bypass that unowned collector/hook,
shared CI/build/dependencies, reference or protected manifest. Before final
root proof, only the owning PR19 branch will reconcile to the coordinator's
verified actually merged repair base, with exact live old-tip protection if
a rebase requires it.

Final acceptance requires an actual repaired head, deterministic positive
regressions, full both-host protocol and schema2 root/source-fingerprinted
module/raw-unit/lint/graph/runtime/APK proof, and the coordinator's retained-
context independent review. Neither the resource hold nor the reproduced
failures is a completion, review approval, hardware/license/signing claim
or permission to start another work package.
