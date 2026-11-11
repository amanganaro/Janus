# Janus – legacy application

Original Janus desktop application (Swing GUI + CLI), imported from the GitLab export of the
`cheminformatics` project (branch `master`, last commit `3570638e`, 2021-12-29).
It is kept here as a working reference while the new application (Spring Boot backend +
Angular frontend) is developed in `backend/` and `frontend/`.

## Build and run

Requires a JDK (11 or later) and Maven, with access to the ProtoQSAR Nexus
(credentials for `protoqsar-maven-public` in `~/.m2/settings.xml`).

```
mvn -f legacy/pom.xml clean package
java -cp "legacy/target/Janus-1.0.jar;legacy/target/dependency/*" insilico.janus.launcher.Launcher
```

Main class: `insilico.janus.launcher.Launcher`

- no arguments: Swing GUI
- `-adv`: Swing GUI with advanced options
- `-source=<input file> -dest=<output dir> -metabolism=yes|no`: batch CLI
  (input: one SMILES per line, optionally followed by tab-separated experimental values)

## Changes from the original code

- Maven repositories replaced with the standard ProtoQSAR ones (the old Kode Nexus is gone).
- Dependency versions pinned to those available on the ProtoQSAR Nexus:
  `InSilico:Core` `RELEASE` → `1.3.21`, and a patch-level bump for 17 `VegaModels-*` modules
  whose 2021 versions are no longer published.
- Added `InSilico:Core-Aux:1.0`: the PMML model classes (`insilico.core.pmml`) were moved out of
  `Core` starting from 1.3.20 and are required by several models.
- Removed unused dependencies `org.openjfx:javafx-swing` (early access build) and
  `insilico:additionalui`, and an unused `org.dmg.pmml` import.

Status: the build succeeds, the GUI starts, and all seven workflows (persistence, BCF, toxicity,
carcinogenicity, mutagenicity, reproductive toxicity, endocrine disruption) produce results
from the CLI. The metabolism calculation does not work (see below).

## Known issue: SMIRKS / Ambit dependency

The metabolism module (`insilico.janus.eawag`, EAWAG-BBD biodegradation rules, used by
`JanusEngine`) applies SMIRKS reactions through the Ambit library
(`ambit:ambit2-smarts:4.1.0-SNAPSHOT`, classes `SMIRKSManager` and `SMIRKSReaction`).
At the time the code was written, CDK had no SMIRKS support.

Problems:

- `ambit2-smarts` is not on Maven Central or on the ProtoQSAR Nexus. It is published only as a
  snapshot on the external ideaconsult Nexus
  (`https://nexus.ideaconsult.net/content/repositories/snapshots`), which is **not** declared in
  `pom.xml`.
- **The build currently works only because the Ambit jars are in the local `~/.m2` cache.**
  On a clean machine (or with an empty cache) dependency resolution fails.
- The `ambit2-smarts` POM declares the ideaconsult repository itself, so its transitive
  dependencies (`ambit2-base`, `ambit2-core`, `ambit2-rules`) resolve to recent snapshots
  (2026), while `ambit2-smarts` is a 2023 snapshot. This mix is the most likely cause of the
  metabolism failure: with `-metabolism=yes` processing stops on the first molecule with
  `Aromaticity Normalization: some aromatic rings can not be correctly recognized`.
  Run the CLI with `-metabolism=no`.

## Planned solution: move to a recent CDK

CDK supports SMIRKS natively since **2.10** (`org.openscience.cdk.smirks` in `cdk-smarts`;
the latest release at the time of writing is 2.14.1). The main API is:

- `Smirks.compile(String)` → `SmirksTransform`;
- `SmirksTransform.apply(IAtomContainer)` or `apply(IAtomContainer, Transform.Mode)` to get
  all products;
- `Smirks.parse(Transform, String, SmirksOption...)` for options such as `DAYLIGHT`, `RDKIT`,
  `IGNORE_IMPL_H`.

The Ambit usage in Janus is limited to `eawag/EawagBlock.java` and `eawag/EawagRule.java`
(`SMIRKSManager.parse()` and `SMIRKSManager.applyTransformation()`), so the code change itself
is small. **However, a single application cannot mix CDK versions**: `InSilico:Core` and the
`VegaModels-*` modules are built on CDK 2.3, so the whole stack must move to CDK ≥ 2.10 first.

TODO:

1. Prepare regression tests for `Core` and the VEGA models (descriptors and predictions) on a
   reference dataset, using the current CDK 2.3 results as the baseline.
2. Upgrade `Core` and the models to the new CDK and check that the results do not change.
3. Rewrite the SMIRKS handling in `insilico.janus.eawag` with the CDK `Smirks` API, remove
   the Ambit dependency, and verify the metabolism results against the EAWAG-BBD rules
   (`src/main/resources/resources/bbd.txt`).
