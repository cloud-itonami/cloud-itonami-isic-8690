# Business Model: Other human health activities

## Classification

- Repository: `cloud-itonami-isic-8690`
- ISIC Rev.5: `8690`
- Activity: other human health activities -- allied health services (physiotherapy, chiropractic, optometry, ambulance and paramedical services) not classified as hospital or physician practice
- Social impact: care quality, data sovereignty, transparent audit

## Customer

- independent allied-health practices
- cooperative therapy clinics
- community paramedical/ambulance operators

## Offer

- patient intake
- assessment/care-plan proposal
- treatment-session proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per practice
- support: monthly retainer with SLA
- migration: import from an incumbent allied-health system
- per-session fee

## Trust Controls

- no treatment session is administered without human sign-off (a licensed allied-health professional)
- a fabricated assessment forces a hold, not an override
- every treatment path is auditable
- patient health data stays outside Git
- emergency manual override paths remain outside LLM control
- a treatment falling outside the treating practitioner's own recorded
  scope of practice, or a not-current practitioner credential, forces a
  hold, not an override
- treatment-session administration is logged and escalated, and cannot
  be finalized twice for the same encounter: a double-administration
  attempt is held off this actor's own encounter facts alone, with no
  upstream comparison needed

## Allied Health Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:allied-
health-governor` -- this is not a generic "review step," it is the
one gate the ONE real-world act this business performs (administering
a treatment session) must pass. The governor sits between the
AlliedHealth-LLM and execution, per the README's Core Contract:

```text
AlliedHealth-LLM -> Allied Health Governor -> hold, proceed, or human approval
```

**Approves**: routine allied-health actions proposed against an
encounter that already has a consented assessment on file, a proposed
treatment within the treating practitioner's own recorded scope of
practice, and a current practitioner credential. These proceed
straight to the care ledger.

**Rejects or escalates**: the governor refuses to let the advisor
administer a treatment session on its own authority when any of the
following hold -- a fabricated jurisdiction spec-basis; incomplete
evidence; a treatment outside the practitioner's own recorded scope
of practice; a not-current credential. A clean administration proposal
still always routes to a human -- `:actuation/administer-treatment-
session` is never auto-committed, at any rollout phase.
