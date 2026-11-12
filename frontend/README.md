# Janus – frontend

The frontend will be an Angular application, bundled with the backend into a single Electron
desktop application.

## Talking to the backend

The HTTP services are **generated from the API contract**, not written by hand. The contract is
produced by the backend build and committed at [`api/openapi.json`](../api/openapi.json):

```
npx ng-openapi-gen --input ../api/openapi.json --output src/app/api
```

Regenerate whenever `api/openapi.json` changes. The backend README describes the endpoints and
the shape of the results; the important points for the frontend are:

- a calculation is a job: `POST /api/jobs` returns immediately, the progress arrives on
  `GET /api/jobs/{id}/events` (server-sent events) and the results come from
  `GET /api/jobs/{id}`;
- the backend runs one job at a time, so a job may legitimately sit in `QUEUED`;
- `GET /api/health` tells whether the models finished loading: at startup the service answers
  but is not ready yet;
- a value a workflow could not produce is absent from the JSON, so every numeric field is
  optional in the generated models;
- numbers are not rounded by the API: formatting is the frontend's job.
