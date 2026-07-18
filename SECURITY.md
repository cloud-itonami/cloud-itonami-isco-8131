# Security Policy

This project handles chemical products plant and machine operators
chemical-plant scheduling/logistics coordination workflows. Treat
vulnerabilities as potentially high impact even when the demo data is
synthetic — Chemical Products Plant and Machine Operators run
industrial chemical processing plants, where errors can cause toxic
release, fire, explosion or reaction hazards, categorically
higher-stakes than ordinary workshop trades.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real client, crew or operator data exposure
- authorization bypass
- ChemCoordGovernor bypass
- op-allowlist widening toward chemical-process-execution-decision finalization, plant-safety-clearance-decision finalization, or plant-safety-officer-authority override
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on client/crew data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real client/crew/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
