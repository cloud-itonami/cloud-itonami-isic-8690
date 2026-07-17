# Operator Quickstart

## Who This Is For

This guide is for **licensed allied-health practices** (physiotherapy clinics, chiropractic offices, optometry practices, ambulance services, or paramedical providers) who want to operate their own encounter intake, assessment verification, practitioner screening, and treatment-session administration system without surrendering patient data to a third-party SaaS. You'll need Clojure CLI installed and some comfort reading through code to understand how the Governor enforces your compliance gates.

## Prerequisites

- **Clojure 1.11+** with `clojure` CLI tool installed
- **Monorepo context** (optional, only if running inside the workspace):
  - This repo is forkable as a standalone repository. Inside the workspace, local dependency resolution via `:local/root` in `deps.edn` is active.
  - Outside the workspace, dependencies resolve via git coordinates instead.

## Run Tests

The project uses Cognitect's test runner to verify the governor contract, phase invariants, store parity, registry conformance, and facts coverage.

```bash
clojure -M:dev:test
```

**What it verifies:**
- **Governor contract**: HARD checks (spec-basis, evidence-completeness, scope-of-practice, credential-currency) work as specified
- **Phase invariants**: state machine transitions are correct and `:actuation/administer-treatment-session` is never auto-gated
- **Store parity**: MemStore and DatomicStore implementations match
- **Registry conformance**: treatment-session records match spec
- **Facts coverage**: jurisdiction catalog citations are present and honest

## Run the Demo

Drive a single clean encounter through the actor and observe four HARD-hold cases:

```bash
clojure -M:dev:run
```

This walks the operation actor (`src/alliedhealth/operation.cljc`) through:
1. A clean single-actuation lifecycle (encounter intake → assessment → treatment administration)
2. Four escalation scenarios (spec-basis missing, evidence incomplete, treatment outside scope, credential expired)

## Explore the Governor

The **Allied Health Governor** is your safety layer. The full implementation is at [`src/alliedhealth/governor.cljc`](../src/alliedhealth/governor.cljc) and enforces the core contract:

```
AlliedHealth-LLM → Allied Health Governor → hold, proceed, or escalate
```

**Key logic:**
- `:actuation/administer-treatment-session` — the real-world act; gated on four HARD checks
- All high-stakes actions route to `alliedhealth.governor/evaluate` before commitment
- A human sign-off is always required; the governor never auto-commits treatment administration

**Hard checks:**
- `spec-basis?` — jurisdiction requirements must cite an official source (see `alliedhealth.facts/catalog`)
- `evidence-complete?` — intake must have required fields; assessment must have required fields
- `treatment-outside-scope-of-practice?` — treatment must be in the practitioner's recorded scope (set-membership recompute)
- `credential-not-current?` — practitioner's credential expiry must be in the future

**Soft gate:**
- confidence threshold + actuation-type policy (high-stakes actions always escalate)

See `test/alliedhealth/phase_test.clj` for the invariant test: `administer-treatment-session-never-auto-at-any-phase`.

## Static Analysis

Run clj-kondo to check for errors:

```bash
clojure -M:lint
```

This is required to pass CI.

## Repository Structure

| File | Role |
|---|---|
| `src/alliedhealth/governor.cljc` | **Allied Health Governor** — four HARD checks + escalation logic |
| `src/alliedhealth/phase.cljc` | **Phase 0→3** — state machine; encounter intake only auto-eligible, treatment administration never auto |
| `src/alliedhealth/operation.cljc` | **OperationActor** — langgraph-clj StateGraph that orchestrates the flow |
| `src/alliedhealth/alliedhealthadvisor.cljc` | **AlliedHealth-LLM** — LLM proposal layer (mock or real) |
| `src/alliedhealth/registry.cljc` | Treatment-session draft records + scope-of-practice checks |
| `src/alliedhealth/facts.cljc` | Jurisdiction catalog (JPN, USA, GBR, DEU) with official spec-basis citations |
| `src/alliedhealth/store.cljc` | **Store protocol** — MemStore ‖ DatomicStore + audit ledger |
| `test/alliedhealth/*_test.clj` | Governor contract, phase invariants, store parity, registry conformance |

## Next Steps

1. **Read the full architecture**: [`docs/adr/0001-architecture.md`](adr/0001-architecture.md)
2. **Understand the business model**: [`docs/business-model.md`](business-model.md)
3. **Plan your deployment**: [`docs/operator-guide.md`](operator-guide.md)
4. **Fork and customize**: Add your jurisdiction's facts to `alliedhealth.facts/catalog`, wire your own practice-management integration to `alliedhealth.store`, and deploy

## Support

- Open an issue in the repository for bugs or feature requests
- AGPL-3.0-or-later: attribution required if you redistribute
- For commercial licensing or managed hosting, visit https://itonami.cloud
