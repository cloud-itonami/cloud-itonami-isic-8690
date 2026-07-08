# ADR-0001: AlliedHealth-LLM ⊣ Allied Health Governor architecture

## Status

Accepted. `cloud-itonami-isic-8690` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-8690` publishes an OSS business blueprint for
other human health activities: allied-health services (physiotherapy,
chiropractic, optometry, ambulance and paramedical services) not
classified as hospital or physician practice. Like every prior actor
in this fleet, the blueprint alone is not an implementation: this ADR
records the governed-actor architecture that promotes it to real,
tested code, following the same langgraph-clj StateGraph + independent
Governor + Phase 0→3 rollout pattern established by `cloud-itonami-
isic-6511` (life insurance) and applied across fifty-nine prior
siblings, most recently `cloud-itonami-isic-8541` (sports and
recreation education).

## Decision

### Decision 1: single-actuation shape

This blueprint's own README, business-model.md and operator-guide.md
consistently name only ONE real-world act: "administering a treatment
session." Matching `leasing`/`underwriting`/`testlab`/`clinic`/
`veterinary`/`funeral`/`parksafety`/`salon`/`entertainment`/
`facility`/`consulting`/`advertising`/`polling`/`research`/`design`/
`sports`'s single-actuation shape, `high-stakes` here is a one-member
set, `#{:actuation/administer-treatment-session}`.

### Decision 2: entity and op shape

The primary entity is an `encounter`, matching `clinic.store`'s own
naming precedent for the same actor shape. Four ops: `:encounter/
intake` (directory upsert, no capital risk), `:assessment/verify`
(per-jurisdiction allied-health evidence checklist, never auto),
`:credential/screen` (practitioner-credential-currency screening,
unconditional-evaluation discipline, never auto), and `:actuation/
administer-treatment-session` (POSITIVE, high-stakes -- administering
a real treatment session). Named distinctly from `clinic`'s own
`:treatment/administer` op (an earlier, less consistent naming
convention in this fleet) to match the LATER `:actuation/verb-noun`
convention every recent sibling (`sports`/8541, `nursing`/8710,
`design`/7410) uses uniformly for both `:op` and `:stake`.

### Decision 3: `treatment-outside-scope-of-practice?` -- a genuinely new check, the 5th set-membership/conflict instance and 1st inverse-polarity instance

Before writing this check, every prior sibling's governor/registry
namespaces were grepped for "scope-of-practice", "scope-of-license"
and "out-of-scope" -- zero hits, confirming this is a genuinely new
concept, avoiding the false-precedent-claim risk `leasing`'s ADR-0001
documents. It reuses `clinic.registry/treatment-contraindicated?`'s
set-membership/conflict SHAPE (single item vs. a set, no arithmetic
comparison) but with the OPPOSITE polarity: `contraindicated?` checks
for PRESENCE in a forbidden set (bad if present); `treatment-outside-
scope-of-practice?` checks for ABSENCE from an allowed set (bad if
absent). The FIFTH instance of the family overall (`clinic`/
`veterinary`/`entertainment`/`nursing` established the first four,
all 'presence-in-forbidden-set'), and the FIRST 'absence-from-allowed-
set' instance. Gates only `:actuation/administer-treatment-session`.

### Decision 4: `credential-not-current-violations` -- honestly reused (not new) as the 44th unconditional-evaluation grounding

This exact concept was already established by `clinic.governor/
credential-not-current-violations` and has since been reused literally
by `hospital`/`eldercare`/`veterinary`/`conservation`/`museum`/
`salon`/`entertainment`/`funeral`/`repairshop`/`registrar`/`wagering`/
`facility`/`casework`/`parksafety`/`nursing` and others (~18 siblings,
grep-verified). Not claimed as new; the 44th distinct application of
the unconditional-evaluation discipline overall (continuing the count
from `sports.governor/background-check-not-cleared-violations` at
43rd), and the most natural, honest choice for this vertical since
`clinic`/8620 -- the nearest-neighbor sibling to allied health --
already established it. Gates `:credential/screen` and the actuation.

### Decision 5: dedicated double-actuation-guard boolean

`:treated?` is a dedicated boolean on the `encounter` record, never a
single `:status` value -- the same discipline every prior sibling
governor's guards establish, informed by `cloud-itonami-isic-6492`'s
real status-lifecycle bug (ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`alliedhealth.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in `test/
alliedhealth/store_contract_test.clj` -- the same seam every sibling
actor uses so swapping the SSoT backend is a configuration change, not
a rewrite. The protocol's per-entity accessor is named `encounter`
directly -- not a Clojure special form, so no `-of` suffix workaround
was needed.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:encounter/intake` (no
capital risk). `:assessment/verify` and `:credential/screen` are
never auto-eligible at any phase (matching every sibling's screening-
op posture), and `:actuation/administer-treatment-session` is
permanently excluded from every phase's `:auto` set -- a structural
fact, not a rollout milestone, enforced by BOTH `alliedhealth.phase`
and `alliedhealth.governor`'s `high-stakes` set independently.

### Decision 8: no bespoke domain capability lib as a code dependency

This blueprint's own `:itonami.blueprint/required-technologies` names
no domain-specific capability beyond the generic robotics/identity/
forms/dmn/bpmn/audit-ledger stack -- there was no capability-lib
decision to make at all.

### Decision 9: mock + LLM advisor pair

`alliedhealth.alliedhealthadvisor` provides `mock-advisor`
(deterministic, default everywhere -- the actor graph and governor
contract run offline) and `llm-advisor` (backed by `langchain.model/
ChatModel`, with a defensive EDN-proposal parser so a malformed LLM
response degrades to a safe low-confidence noop rather than ever
auto-administering a treatment session).

### Decision 10: no `blueprint.edn` field-sync fixes needed

Matching `advertising`/7310's, `polling`/7320's, `research`/7210's,
`design`/7410's, `nursing`/8710's and `sports`/8541's own experience,
this repo's `blueprint.edn` already had the correct `isic-` prefixed
`:id` and correctly populated `:required-technologies`/`:optional-
technologies` matching the `kotoba-lang/industry` registry's own
entry for `"8690"` exactly -- only the `:maturity` field itself
needed adding.

## Alternatives considered

- **A dual-actuation shape** (e.g. splitting "assessment finalization"
  and "treatment administration" into two actuations). Rejected: the
  blueprint's own text consistently names only ONE real-world act;
  inventing a second would not be grounded in the blueprint's own
  text.
- **Reusing `treatment-contraindicated?` instead of inventing scope-
  of-practice.** Rejected: contraindication is a patient-specific
  medical-conflict concern (already well-established); scope-of-
  practice is a distinct regulatory-compliance concern (can this
  practitioner legally perform this modality at all) -- conflating
  them would lose the ability to test/report each failure mode
  independently.
- **Reusing `clinic`'s exact `:treatment/administer` op name.**
  Rejected: this fleet's naming convention has since converged on
  `:actuation/verb-noun` for both `:op` and `:stake` (see Decision 2)
  -- matching the newer convention keeps this build consistent with
  its immediate predecessors rather than an older, now-superseded
  style.

## Consequences

- Sixtieth actor in this fleet (59 implemented before this build).
- Confirms the set-membership/conflict check family generalizes to a
  5th instance, and establishes its first inverse ('absence-from-
  allowed-set') polarity.
- Establishes a genuinely NEW check concept (scope-of-practice),
  grep-verified absent from every prior sibling.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/alliedhealth/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- `blueprint.edn` required no field-sync fixes this time (already
  correct) -- only the `:maturity` flip itself.

## References

- `orgs/cloud-itonami/cloud-itonami-isic-8690/README.md`
- `orgs/cloud-itonami/cloud-itonami-isic-8690/docs/business-model.md`
- `orgs/kotoba-lang/industry/resources/kotoba/industry/registry.edn` (entry `"8690"`)
