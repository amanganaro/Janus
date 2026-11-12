# Janus – backend

Spring Boot REST service exposing the Janus assessment workflows. The calculation logic comes
from the original desktop application kept in `legacy/`: the domain classes (`JanusEngine`, the
seven workflows, the EAWAG metabolism module) were copied over unchanged, while the Swing GUI,
the batch CLI, the file writers and the old `insilico.janus.api` experiment were left behind.

The service is meant to run locally, bundled in an Electron desktop application together with
the Angular frontend, for a single user: **it has no authentication and no authorization on
purpose**, and must not be exposed on a network.

## Build and run

Requires a JDK 21 or later and Maven, with access to the ProtoQSAR Nexus (credentials for
`protoqsar-maven-public` in `~/.m2/settings.xml`).

```
mvn -f backend/pom.xml clean package
java -Xmx6g -jar backend/target/janus-backend-1.0.0-SNAPSHOT.jar
```

The jar is large (~700 MB): it embeds every VEGA model. Loading the models takes a few seconds
and happens in the background, so the HTTP port answers immediately while
`GET /api/health` still reports `{"ready": false}`. Jobs submitted before the engine is ready
are accepted and wait in the queue.

Give the JVM enough heap (`-Xmx6g`); with less, the models can fail with an out of memory error
in the middle of a workflow.

## The API contract (OpenAPI)

The contract is the single source of truth shared with the frontend. It is **generated from the
code** — the controllers, the DTO records, their Bean Validation constraints and their Javadoc —
so it cannot drift away from what the service actually does:

- live, while the service runs: `http://localhost:8080/v3/api-docs` and the browsable
  `http://localhost:8080/swagger-ui.html`;
- committed, at [`api/openapi.json`](../api/openapi.json): `OpenApiContractTest` rewrites it on
  every `mvn test`, so a change to an endpoint or to a DTO shows up as a change of the contract
  in the same commit, and the test fails if an endpoint or a schema went missing.

The descriptions come from the Javadoc through the therapi annotation processor configured in
`pom.xml`: document a record component or a controller method and the text lands in the
contract. The contract is emitted as OpenAPI 3.0 (`springdoc.api-docs.version` in
`application.yml`) rather than the 3.1 default, because the Angular client generators support
3.0 best.

### Generating the frontend client

The Angular services are generated from the committed contract, never written by hand:

```
cd frontend
npx ng-openapi-gen --input ../api/openapi.json --output src/app/api
```

This produces the typed models (`JanusJobRequest`, `JobDto`, `MoleculeResultDto`, …) and the
`HttpClient` services for every endpoint. Regenerate after every backend change that touches the
contract; since the file is committed, the diff of `api/openapi.json` shows exactly what the
frontend has to adapt to.

## API

Base path `/api`, everything is JSON. A calculation is always a job: the request is accepted
immediately and the results are fetched afterwards.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/jobs` | submit a calculation, returns `202` and the job id |
| `GET` | `/api/jobs/{id}` | state of the job, with the results once `COMPLETED` |
| `GET` | `/api/jobs/{id}/events` | progress as server-sent events |
| `DELETE` | `/api/jobs/{id}` | cancel the job and forget it |
| `GET` | `/api/jobs` | all the jobs of this session, without results |
| `GET` | `/api/metadata` | available workflows and experimental value keys |
| `GET` | `/api/health` | whether the engine finished loading |

### Submitting a calculation

```
POST /api/jobs
{
  "molecules": [
    { "id": "benzene", "smiles": "c1ccccc1",
      "experimentalValues": { "logp": 2.13, "bcf": 0.5 } }
  ],
  "metabolism": false,
  "workflows": ["PERSISTENCE", "BIOACCUMULATION", "TOXICITY", "CARCINOGENICITY",
                "MUTAGENICITY", "REPRO_TOXICITY", "ENDOCRINE"]
}
```

- assessing a single SMILES is just a job with one molecule;
- `id` is optional and defaults to `Mol n`;
- `experimentalValues` is optional; the accepted keys are listed by `GET /api/metadata`. When a
  workflow finds an experimental value for its property it uses it instead of its prediction and
  reports a reliability of `1.0`;
- `metabolism` defaults to `false`, see below;
- `workflows` is optional: when omitted all seven run. A workflow that was not requested comes
  back empty (`predictionClass` `"-"` and no numbers).

A molecule whose SMILES cannot be parsed is dropped, and the reason is reported in the `log` of
the job; if no molecule survives, the request is rejected with `400`.

### Reading the results

`GET /api/jobs/{id}` returns the state and, once `COMPLETED`, one result per molecule:

```json
{
  "num": 1, "id": "benzene", "smiles": "c1ccccc1", "valid": true,
  "metabolite": false, "metabolites": [], "notes": "",
  "persistence": {
    "predictionClass": "nP", "prediction": 0.59, "reliability": 1.0, "score": 0.227,
    "additionalValues": [{ "name": "Ready biodegradability model", "value": "Not classifiable" }]
  },
  "bioaccumulation": { ... }, "toxicity": { ... }, "carcinogenicity": { ... },
  "mutagenicity": { ... }, "reproToxicity": { ... }, "endocrine": { ... },
  "scoreVPvB": 0.183, "scorePBT": 0.269, "scoreSVHC": 0.569
}
```

Differences from the legacy output worth knowing:

- a value the engine could not produce is `null`; the `-999999` sentinel of `JanusResult`
  never reaches the API;
- numbers are not rounded. The legacy `DecimalFormat` patterns were presentation, and belong to
  the frontend;
- `additionalValues` is an **ordered list**, not an object: the order the workflow produced the
  values in is meaningful.

### Following the progress

`GET /api/jobs/{id}/events` streams `progress`, `log` and `status` events carrying the state of
the job. The engine runs one workflow at a time over the whole dataset, so `progress` is
relative to the phase it names (`"PERSISTENCE workflow"`, `"BCF workflow"`, …) and the molecule
counter restarts at each phase.

## One job at a time

`JanusEngine` is **not thread-safe**: the metabolism flag, the progress listener and the
per-molecule state of the workflows are shared mutable fields. The service therefore runs jobs
on a single worker thread; a job submitted while another one runs stays `QUEUED`. This is the
right trade-off for a desktop application, and it is also why the engine is built once at
startup (`JanusEngineHolder`) instead of per request.

Cancelling a job that is already running does not interrupt the calculation — the engine never
checks for interruption — it only marks the job as `CANCELLED` and discards its results.

Jobs are kept in memory and discarded an hour after they finish.

## Metabolism

`metabolism: true` makes the engine generate the metabolites of the input molecules with the
EAWAG-BBD biodegradation rules and assess them too, as additional results linked to their parent
through `parentNum` / `metabolites`.

**This currently fails for most molecules.** The rules are applied through SMIRKS reactions of
the Ambit library (`ambit:ambit2-smarts:4.1.0-SNAPSHOT`), and:

- the artifact is published neither on Maven Central nor on the ProtoQSAR Nexus, only as a
  snapshot on the external ideaconsult Nexus, which is not declared in `pom.xml`: **the build
  works only because the jars are in the local `~/.m2` cache**, and fails on a clean machine;
- its transitive dependencies resolve to much more recent snapshots than `ambit2-smarts`
  itself, and that mix is the likely cause of the failure: any molecule with an aromatic ring
  stops with `Aromaticity Normalization: some aromatic rings can not be correctly recognized`.
  The job then ends as `FAILED` with that message.

The fix is to drop Ambit and use the SMIRKS support CDK has had since 2.10
(`org.openscience.cdk.smirks`). The Ambit usage is confined to `eawag/EawagBlock.java` and
`eawag/EawagRule.java`, so the code change is small, but a single application cannot mix CDK
versions: `InSilico:Core` and the `VegaModels-*` modules are built on CDK 2.3, so the whole
stack has to move to CDK ≥ 2.10 first. See `legacy/README.md` for the full plan.

## Dependency notes

- The `InSilico:*` and CDK versions are copied verbatim from `legacy/pom.xml`; they are pinned
  to what the ProtoQSAR Nexus actually publishes and should not be bumped casually.
- **Two JAXB namespaces coexist on purpose.** JAXB renamed its packages: up to 2.3 it is
  `javax.xml.bind`, from 3 on it is `jakarta.xml.bind` — different classes, not two versions of
  the same ones. The VEGA models read their PMML through jpmml 1.5.6, compiled against the
  `javax` namespace, while springdoc needs the `jakarta` annotations. So:
  - `jakarta.xml.bind:jakarta.xml.bind-api` is left at the version Spring Boot manages (4.x),
    which is what springdoc reads;
  - `javax.xml.bind:jaxb-api:2.3.1` is added as a direct dependency, which is what jpmml reads;
  - `org.glassfish.jaxb:jaxb-runtime` is pinned to **2.3.1** in `dependencyManagement`, together
    with `txw2` and `istack-commons-runtime`. 2.3.1 is the last release that depends on the
    `javax.xml.bind` coordinates; from 2.3.2 it moves to the `jakarta` ones and the conflict
    comes back.

  A child `dependencyManagement` overrides the one inherited from `spring-boot-starter-parent`,
  which is what makes the pin stick. There is a single JAXB runtime, the `javax` one, and
  springdoc only needs the `jakarta` annotation classes, not a runtime of its own.

  Get one half wrong and it shows immediately: without the `javax` side the engine fails to start
  with `NoClassDefFoundError: javax/xml/bind/JAXBContext`, without the `jakarta` side
  `/v3/api-docs` answers 500 with `NoClassDefFoundError: jakarta/xml/bind/annotation/XmlElement`.

  Remove all of it when `InSilico:Core-Aux` moves to jpmml 1.6 or later, which uses the
  `jakarta` namespace.

## Verifying against the legacy application

The backend must reproduce the legacy results exactly. To check it, run the legacy CLI and the
API on the same molecules and compare:

```
java -cp "legacy/target/Janus-1.0.jar;legacy/target/dependency/*" \
     insilico.janus.launcher.Launcher -source=mols.txt -dest=out -metabolism=no
```

then submit the same SMILES as a job and compare `predictionClass`, `prediction`, `reliability`
and `score` of the seven workflows plus the three indices against
`out/janus_run_*/janus_txt_main_results.txt`, allowing for the rounding the TSV applies.
