# Bounded native acquisition evidence

This exporter bridges actual canonical receipts to the T19 report. It is not a release gate or a replacement for the full corpus. The initial supported subset is exactly two immutable Athletics 0→1 / DEFAULT / NONE / EXISTING / FIXED cases, at creation and after creation. Their monetary expectations come from the independent corpus, not runtime quotes.

It exercises the real Desktop file store, service, codec and Undo. A new store instance reopens the saved purchase and inverse. This is same-process reopening, not process restart, crash durability, Android/browser UI, or server acceptance.

## Reproduce

Use an otherwise unused output directory and run from the repository root. The standard project JDK, Android SDK and Gradle prerequisites still apply.

```sh
python3 -m tools.rulebook.report_acquisition_coverage --prepare-native-probe build/native-probe-input.json
FURY_NATIVE_INPUT="$PWD/build/native-probe-input.json" \
FURY_NATIVE_OUTPUT="$PWD/build/native-probe-evidence/native.json" \
./gradlew :shared:desktopTest -I tools/tests/acquisition_native_probe.init.gradle \
  --tests '*AcquisitionNativeEvidenceProbeTest*'
python3 -m tools.rulebook.report_acquisition_coverage \
  --evidence-dir build/native-probe-evidence \
  --output build/native-probe-report.json
```

The input binding records the current commit and source digests. The exporter verifies them before and after execution and refuses to overwrite existing evidence. The consumer rejects stale commits or source, missing/duplicated/unexpected cases, unknown producers and malformed records. It derives normalized receipt and Undo identities from the native envelopes; a summary claiming PASS is not evidence.

All 83,818 report obligations remain present. A successful probe can remove only its two Shared execution omissions. The runtime gate is PARTIAL, every platform interaction stage stays missing, and releaseReady remains false. Unknown requirements, other transitions, discounts, custom instances and production gates are not inferred from these two cases.

Current automated contracts include strict native receipt/ledger/reopen/Undo mutation checks, source-bound probe rejection tests and full-denominator report checks. Synthetic negative fixtures are not accepted as real execution evidence.
