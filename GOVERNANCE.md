# Governance

`cloud-itonami-8690` is an OSS open-business blueprint for other human health activities -- allied health services (physiotherapy, chiropractic, optometry, ambulance and paramedical services) not classified as hospital or physician practice.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Allied Health Governor remains independent of the advisor.
- hard policy violations (fabricated assessment, incomplete records) cannot be
  overridden by human approval.
- administering a treatment session always escalates to a human -- never automated.
- every hold, approval and care-action path is auditable.
- patient/resident and client data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Allied Health Governor's policy checks
- mishandling patient/resident/client data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
