RIS Portal access controls for internal staff
==========================================

Configure both API servers with the same secret, supplied through server configuration only:

Portal API:
```
APP_RMS_INTEGRATION_KEY=<shared secret>
APP_RMS_WEB_URL=http://16.112.94.107:3001/
```

RMS API:
```
APP_PORTAL_ENABLED=true
APP_PORTAL_API_URL=<reachable Portal API base URL including /api>
APP_PORTAL_INTEGRATION_KEY=<same shared secret>
```

The Portal website is http://16.112.94.107:3005/. Its `/api` paths currently return frontend HTML. The API responds at http://16.112.94.107:8085/api; use that verified API address for the bridge, or a verified internal backend address when both services share a host. Use HTTPS for connections leaving the trusted server network.

Rollout order: apply Portal migration V257; deploy the Portal API; configure selected roles/users with RMS_ACCESS and their required RMS action permissions; deploy/configure the RMS API; then deploy the UI. Do not enable the bridge before the Portal API and shared secret are ready. No grants or employee eligibility are enabled automatically, including for Portal administrators.

The Portal `/rms` page opens the configured RMS URL and, for RMS_INTERNAL_MANAGE holders, links internal employee IDs and availability. Official email and company must match. Existing RMS IDs, resumes, documents, interview history and allocations remain in place. Map internal profiles before staff begin new resourcing activity. The independent RMS login remains; Portal approval is additionally required for internal staff, including existing sessions.

External candidate tables, fields, resumes, imports and recruitment data are not migrated or synchronized. Candidate requests bypass internal eligibility rules. Staff access controls still protect the RMS application. Internal interview viewers see assigned/requested interviews unless explicitly granted RMS_INTERVIEW_ALL.

RMS rechecks Portal authorization with a maximum five-second cache and the UI refreshes access every 15 seconds. After a permission/account change, existing RMS bearer tokens cannot bypass the next check. An unavailable Portal fails closed. The shared secret authorizes only four read endpoints used by the Portal integration, never write operations. Keep it out of browsers and source control.
