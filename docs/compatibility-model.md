# ContinuumLib compatibility model

Current direction: preserve existing mod class bodies and resolve exact JVM API identities. The early same-environment block capability described below has been removed because it did not validate real symbols. The active `ClassAdapter` supports explicit descriptor-preserving member and class renames; signature changes and receiver adaptation require semantic bridges. Full target linkage validation remains pending.

Every recognized Minecraft operation receives one resolution status: `DIRECT`, `MAPPED`, `ADAPTED`, `GENERATED`, `DEVELOPER_HOOK`, `UNSUPPORTED`, or `AMBIGUOUS`.

The build can proceed only when all relevant operations have executable statuses. Unsupported and ambiguous operations remain visible and are never dropped from the resolution plan.

The first implemented capability recognizes Forge 1.18.2 block registry declarations and custom block registrations. Same-environment resolution is `DIRECT`; other targets remain explicitly unsupported until their native capability providers and compilation tests are implemented.
