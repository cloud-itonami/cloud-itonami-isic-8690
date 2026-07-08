# cloud-itonami-isic-8690

Open Business Blueprint for **ISIC Rev.5 8690**: Other human health
activities.

This repository publishes an allied-health actor -- encounter intake,
allied-health regulatory assessment, practitioner-credential screening
and treatment-session administration -- as an OSS business that any
qualified, licensed provider can fork, deploy, run, improve and sell,
so a community or independent practice never surrenders patient data
and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419),
[`7310`](https://github.com/cloud-itonami/cloud-itonami-isic-7310),
[`7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320),
[`7210`](https://github.com/cloud-itonami/cloud-itonami-isic-7210),
[`7410`](https://github.com/cloud-itonami/cloud-itonami-isic-7410),
[`8710`](https://github.com/cloud-itonami/cloud-itonami-isic-8710),
[`8541`](https://github.com/cloud-itonami/cloud-itonami-isic-8541)) --
here it is **AlliedHealth-LLM ⊣ Allied Health Governor**.

> **Why an actor layer at all?** An LLM is great at drafting an
> encounter-intake summary, normalizing records, and checking whether
> a proposed treatment actually stays within a practitioner's own
> recorded scope of practice -- but it has **no notion of which
> jurisdiction's allied-health-licensing law is official, no license
> to administer a real treatment session, and no way to know on its
> own whether a practitioner's own credential has actually stayed
> current**. Letting it administer a treatment session directly
> invites fabricated regulatory citations, a treatment being delivered
> outside the practitioner's own certified scope, and a lapsed
> credential being quietly overlooked -- and liability, and patient-
> safety risk, for whoever runs it. This project seals the
> AlliedHealth-LLM into a single node and wraps it with an independent
> **Allied Health Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers encounter intake through allied-health regulatory
assessment, practitioner-credential screening and treatment-session
administration. It does **not**, by itself, hold any license required
to operate as an allied-health practice in a given jurisdiction, and
it does not claim to. It also does not model a real practice-
management system, the actual physical treatment itself, or clinical
judgment -- `alliedhealth.registry/treatment-outside-scope-of-
practice?` is a pure ground-truth set-membership recompute against
the encounter's own recorded fields, not a clinical assessment.
Whoever deploys and operates a live instance (a licensed allied-health
practice) supplies any jurisdiction-specific license, the real
practitioner workforce and the real practice-management-system
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
practice does not have to build the compliance layer from scratch.

### Actuation

**Administering a real treatment session is never autonomous, at any
phase, by construction.** Two independent layers enforce this
(`alliedhealth.governor`'s `:actuation/administer-treatment-session`
high-stakes gate and `alliedhealth.phase`'s phase table, which never
puts `:actuation/administer-treatment-session` in any phase's `:auto`
set) -- see `alliedhealth.phase`'s docstring and `test/alliedhealth/
phase_test.clj`'s `administer-treatment-session-never-auto-at-any-
phase`. The actor may draft, check and recommend; a human licensed
allied-health professional is always the one who actually administers
a treatment session. Matching `leasing`'s/`underwriting`'s/`testlab`'s/
`clinic`'s/`veterinary`'s/`funeral`'s/`parksafety`'s/`salon`'s/
`entertainment`'s/`facility`'s/`consulting`'s/`advertising`'s/
`polling`'s/`research`'s/`design`'s/`sports`'s single-actuation shape,
grounded directly in this blueprint's own README text ("No automated
proposal, by itself, can complete the following without governor
approval and audit evidence: administering a treatment session") -- a
POSITIVE actuation (finalizing a real record), matching this fleet's
majority actuation shape (`3600`/`6190` are the fleet's two NEGATIVE-
actuation exceptions).

## The core contract

```
encounter intake + jurisdiction facts (alliedhealth.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ AlliedHealth │ ─────────────▶ │ Allied Health                 │  (independent system)
   │ -LLM (sealed)│  + citations    │ Governor:                    │
   └──────────────┘                 │ spec-basis · evidence-       │
          │                 commit ◀┼ incomplete · treatment-outside │
          │                         │ -scope-of-practice (set-        │
    record + ledger        escalate ┼ membership) · credential-not-    │
          │              (ALWAYS for│ current (unconditional) ·         │
          │               :actuation│ already-treated                     │
          │               /administer└───────────────────────┘
          ▼               -treatment-
      human approval      session)
```

**The AlliedHealth-LLM never administers a treatment session the
Allied Health Governor would reject, and never does so without a
human sign-off.** Hard violations (fabricated regulatory requirements;
unsupported evidence; a treatment outside the practitioner's own
recorded scope of practice; a not-current credential; a double
administration) force **hold** and *cannot* be approved past; a clean
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean single-actuation lifecycle + four HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a mobility-assist robot
supports physical patient transfer/therapy exercises under the actor,
gated by the independent **Allied Health Governor**. The governor
never dispatches hardware itself; `:high`/`:safety-critical` actions
require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Allied Health Governor, treatment-session draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8690`). This vertical's care/case records are practice-specific
rather than a shared cross-operator data contract, so `alliedhealth.*`
runs on the generic robotics/identity/forms/dmn/bpmn/audit-ledger
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/alliedhealth/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + treatment-session history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded encounter, and the double-actuation guard checks a dedicated `:treated?` boolean rather than a `:status` value |
| `src/alliedhealth/registry.cljc` | Treatment-session draft records, plus `treatment-outside-scope-of-practice?` -- a GENUINELY NEW concept (grep-verified absent from every prior sibling), the FIFTH instance of this fleet's set-membership/conflict check family and the FIRST 'absence-from-allowed-set' polarity instance (`clinic`/`veterinary`/`entertainment`/`nursing` established the first four, all 'presence-in-forbidden-set') |
| `src/alliedhealth/facts.cljc` | Per-jurisdiction allied-health catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/alliedhealth/alliedhealthadvisor.cljc` | **AlliedHealth-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment-verification/credential-screening/treatment-session proposals |
| `src/alliedhealth/governor.cljc` | **Allied Health Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · treatment-outside-scope-of-practice, ground-truth set-membership recompute · credential-not-current, unconditional evaluation, a literal reuse already established by `clinic` and ~18 other siblings, the 44th grounding overall) + already-treated guard + 1 soft (confidence/actuation gate) |
| `src/alliedhealth/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (treatment-session administration always human; encounter intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/alliedhealth/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/alliedhealth/sim.cljc` | demo driver |
| `test/alliedhealth/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers encounter intake through allied-health regulatory
assessment, practitioner-credential screening and treatment-session
administration -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Encounter intake + per-jurisdiction allied-health checklisting, HARD-gated on an official spec-basis citation (`:encounter/intake`/`:assessment/verify`) | Real practice-management-system integration, real physical treatment itself (see `alliedhealth.facts`'s docstring) |
| Practitioner-credential screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:credential/screen`) | Any clinical judgment itself -- deliberately outside this actor's competence |
| Treatment-session administration, HARD-gated on full evidence, the encounter's own scope-of-practice ground truth and a current credential, plus a double-administration guard (`:actuation/administer-treatment-session`) | |
| Immutable audit ledger for every intake/verification/screening/administration decision | |

Extending coverage is additive: add the next gate (e.g. a referral-
requirement check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`alliedhealth.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `alliedhealth.facts/catalog`
-- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `alliedhealth.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `AlliedHealth-LLM` + `Allied Health Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the fifty-
nine prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
