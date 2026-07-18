# Operator Guide

## First Deployment

1. Define the operator's plant roster and operator-certification/registration process.
2. Define consent and purpose categories for logged production-run/batch/progress records.
3. Run synthetic coordination cases (production-run/batch/progress record logging, scheduling, safety-concern flags, supply orders).
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions — every safety-concern flag, no exceptions ever, and every above-threshold supply order.
5. Measure coordination outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path (process-anomaly, leak-risk and equipment-condition concerns ALWAYS reach a human — no exceptions, ever)
- provenance for all plants and operators before any coordination action
- human review for high-risk cases
- audit export for all gated actions

## Scope Boundary (Mandatory)

This actor coordinates chemical-plant scheduling and logistics ONLY.
Operators must not wire this actor's output into any system that
would let it directly finalize a chemical-process-execution decision
(authorizing a process/reaction to proceed), finalize a plant-safety-
clearance decision, or override a plant safety officer's judgment —
the governor's closed op-allowlist and scope-exclusion rule are the
last line of defense, not the only one; operator-side integrations
must not create a path around them. This actor never operates
chemical-processing equipment itself: `:coordinate-supply-order`
covers plant-equipment/administrative-supply procurement only, never
process chemicals or reagents themselves — chemical handling is
entirely out of scope for this administrative-coordination actor.

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans unconditionally, and that no integration allows this actor to
finalize a chemical-process-execution decision, finalize a plant-
safety-clearance decision, or override plant-safety-officer authority.
