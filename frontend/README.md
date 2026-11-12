# Janus – frontend

Angular application for the Janus assessment, talking to the REST backend in `backend/`. It
will be bundled with the backend into a single Electron desktop application.

This is the first step: the structure, the routing and the calls to the API are in place, the
layout is deliberately plain. Styling and design come later.

## Run it

The backend has to be running first, on port 8080:

```
java -Xmx6g -jar ../backend/target/janus-backend-1.0.0-SNAPSHOT.jar
```

then:

```
npm install
npm start          # http://localhost:4200
```

`ng serve` proxies `/api`, `/v3` and `/swagger-ui` to `http://localhost:8080`
(`proxy.conf.json`), so the browser only ever talks to its own origin and the backend needs no
CORS configuration. The same will hold in the packaged application, where the backend serves the
built files.

## The API client is generated

`src/app/api/` is **generated from the contract** and must not be edited by hand. The contract
is `api/openapi.json` at the root of the repository, which the backend build produces.

```
npm run api        # regenerates src/app/api from ../api/openapi.json
```

Regenerate it after every backend change that touches the contract. The only call written by
hand is the progress stream: `HttpClient` cannot consume `text/event-stream`, so
`core/job-watcher.service.ts` uses the native `EventSource` — with the generated `JobDto` as its
payload type, and a polling fallback so the result never depends on the stream.

## How it is laid out

```
src/app/
  api/                      generated client, do not edit
  core/
    janus.service.ts        health, metadata, submit, read and cancel a job
    job-watcher.service.ts  progress over SSE, with a polling fallback
    smiles-input.parser.ts  reads the legacy input format
    result-format.ts        rounding, missing values, colour thresholds, derived labels
    results-export.ts       the tab separated export of the summary table
  pages/
    input-page/             molecules and options
    running-page/           progress
    results-page/           summary table
    molecule-detail-page/   one molecule in full
```

| Route | Page |
|---|---|
| `/` | input |
| `/jobs/:jobId` | progress; redirects to the results when the job completes |
| `/jobs/:jobId/results` | summary table |
| `/jobs/:jobId/results/:num` | detail of one molecule |

The job id is part of the URL and every page rebuilds itself from the backend, so a reload or a
bookmarked link keeps working for as long as the backend holds the job — one hour.

## Things worth knowing

- **The input format is the legacy one**: one molecule per line, the SMILES first, then any
  experimental value as `key:value`, separated by tabs. The accepted keys come from
  `GET /api/metadata`; an unknown one is a warning, not an error. A file and the textarea go
  through the same parser.
- **The engine takes a while to start**: `GET /api/health` reports `ready: false` until the
  models are loaded, and the submit button stays disabled meanwhile.
- **One job at a time**: the backend is single threaded by design, so a job can legitimately sit
  in `QUEUED`.
- **The progress bar is per phase**: the engine runs the seven workflows one after the other over
  the whole dataset and its counter restarts at each one.
- **The formatting lives in the frontend**: the API sends raw numbers and omits what it could not
  calculate. `result-format.ts` applies the roundings the legacy applied in Java, shows a dash for
  a missing value, and reproduces the colour thresholds and the derived labels (`NC`, `M`/`NM`,
  `T`/`NT`, `A`/`NA`) of the legacy summary page — including its quirk of colouring
  carcinogenicity on the score and the other three properties on their assessment.
- **No 2D structure yet**: the legacy detail page drew the molecule from a pre-generated PNG; the
  API does not expose one. A `Depiction` based endpoint can be added later.
- **Metabolism is broken in the backend**: the option is there, with a warning, but the job fails
  for most molecules. See `backend/README.md`.
