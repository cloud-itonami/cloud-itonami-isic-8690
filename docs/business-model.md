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
