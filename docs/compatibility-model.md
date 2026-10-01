# ContinuumLib compatibility model

Every recognized Minecraft operation receives one resolution status: `DIRECT`, `MAPPED`, `ADAPTED`, `GENERATED`, `DEVELOPER_HOOK`, `UNSUPPORTED`, or `AMBIGUOUS`.

The build can proceed only when all relevant operations have executable statuses. Unsupported and ambiguous operations remain visible and are never dropped from the resolution plan.

The first implemented capability recognizes Forge 1.18.2 block registry declarations and custom block registrations. Same-environment resolution is `DIRECT`; other targets remain explicitly unsupported until their native capability providers and compilation tests are implemented.
