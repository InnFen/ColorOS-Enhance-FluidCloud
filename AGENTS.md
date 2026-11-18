# Module instructions

- If the parent workspace contains `AGENTS.md`, read and follow it. Before Android work, read the shared Android instructions from `CODEX_ANDROID_ROOT/AGENTS.md` when configured, or from `../../codex-android/AGENTS.md` when that local environment exists. A configured but missing environment is an error; do not silently replace it.
- This is an independent Git repository. Keep all edits, tests, reports and generated files here. Source integrations are authorized; do not modify the source modules.
- Public behavior and publication boundaries are described in `README.md`, `docs/PUBLISHING.md` and `docs/PRIVACY.md`. Historical device notes under `docs/` are private and ignored; do not force-add them.
- Do not commit, push or change device state without explicit authorization for that operation.
- Use the checked-in Wrapper and shared environment; run offline first. Target ColorOS 16 / Android 16 and ColorOS 17 / Android 17; preserve original native rendering, authentication and single PIN attempt guarantees. Keep firmware-specific adapters capability-checked and retain the older interface path.
- Credentials, signing material and device captures belong only in ignored module-specific locations. Never print PINs or signing secrets. Never share the old modules' credential storage or keystore aliases.
- Run `scripts/Audit-Publish.ps1` before staging or publishing. It checks candidate Git files without staging them. Author name Howard and the existing package namespaces are deliberately public; device identifiers, private paths, credentials and diagnostic captures are not.

## Release approval workflow

- Feature additions, bug fixes and refactors default to implementation, appropriate checks and acceptance verification. Completing that work, building an APK or receiving permission to install/test it does not authorize a public release.
- After acceptance is complete, summarize the changes and verification results, state the proposed version, release channel (stable or prerelease) and destination repositories, and ask the user whether to publish this update. Wait for an explicit answer; silence or acceptance of the feature is not release approval.
- Without approval for this update, do not automatically create a new public version, increment versions solely for publication, create/push release tags, create draft/prerelease/stable Releases, upload/replace public APK assets, or change an existing release's channel/Latest status. Do not push changes that would trigger automatic publication. Local test builds remain separate from public releases, and device operations still require their own applicable authorization.
- Approval applies only to the confirmed update/version and destinations. A previous instruction to publish a version is not standing authorization to publish later changes. If the user has already explicitly authorized publication of this same update, proceed within that scope without asking again.
- Once approved, follow `docs/PUBLISHING.md`: verify the version, signature, privacy audit and release artifacts; publish only to the agreed repositories. Keep source code in the author's repository and documentation/downloads in the LSPosed module repository. Verify the public download and checksum, and distinguish successful publication from later website/index visibility.
