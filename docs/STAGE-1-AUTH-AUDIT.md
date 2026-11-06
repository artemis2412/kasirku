# KASIRKU — Stage 1: Authentication & PIN Audit

Status: audit checkpoint; no production authentication behavior changed in this commit.

## Scope inspected
- `index.html` script loading order
- `auth.js` local PIN setup, login, registration, session restore, idle lock, role-based navigation
- `cloud-session.js` Cloud session restore and membership validation
- `cloud-bridge.js` local PIN / Cloud identity bridge
- `supabase/schema.sql` initial multi-tenant schema and RLS policies

## Findings

### P0 — Do not treat local PIN as a server security boundary
Local PIN identities, roles, hashes, and sessions are stored in browser storage. Hashing with a per-user salt is better than storing a plain PIN, but a six-digit PIN hash can be brute-forced offline and browser storage can be modified by a device owner. Local role checks are therefore suitable for a single-device workflow only; they cannot authorize cross-device or Cloud data access.

**Required before production multi-user rollout:** every privileged Cloud operation must be authorized by Supabase Auth + RLS (and server-side RPC where atomic business rules are needed). Never trust a role, store ID, or branch ID supplied only by local storage.

### P1 — PIN login currently has no effective attempt throttling
The login handler records failed attempts in an audit log, but it does not enforce a cooldown or progressive delay. Add a bounded, user-friendly delay and clear feedback. Treat client-side throttling as friction only; it is not a replacement for server-side authentication controls.

### P1 — Cloud registration and local PIN registration are separate flows
The PIN registration flow verifies a Cloud account and active store membership, then saves a local PIN identity and an identity-link record. The welcome screen's Cloud entry is a preview route. Keep this separation explicit until Cloud login, identity linking, branch selection, and data migration have been validated together.

### P1 — Cloud role vocabulary is inconsistent
The database schema permits `supervisor`, while current Cloud session validation accepts only `owner`, `admin`, and `cashier`. The local reader maps legacy `supervisor` records to `cashier`. Define one canonical role matrix before enabling Supervisor accounts.

### P1 — Session timeout is client-side
The local and Cloud idle timeout is enforced in browser code. This improves shared-device hygiene but does not revoke a stolen token or protect data already accessible to a browser session. Cloud authorization must continue to rely on verified Supabase sessions and RLS.

### P2 — PIN change workflow needs regression tests
The settings flow supports Owner/Admin PIN changes and preserves the previous hash to show a helpful message if an old PIN is entered. Verify account creation, role boundaries, duplicate PIN rejection, lockout behavior, and audit history before modifying this workflow.

## Stage 1 acceptance checklist
- [ ] Login layout works at 360px, 390px, tablet, and desktop widths.
- [ ] PIN accepts exactly six digits; paste and Android numeric keyboard behave correctly.
- [ ] Wrong PIN feedback is clear; repeated failures trigger a bounded cooldown.
- [ ] Inactive accounts cannot log in.
- [ ] Owner/Admin/Cashier page access matches the approved role matrix.
- [ ] Idle lock and explicit logout clear the correct local and Cloud sessions.
- [ ] Cloud session restore rejects inactive memberships and unauthorized branches.
- [ ] RLS tests prove one store cannot read or mutate another store's records.
- [ ] No service-role/secret key is shipped to the browser.
- [ ] Existing local data and PIN identities survive the upgrade.

## Safe implementation order
1. Agree and document the canonical role matrix.
2. Add client-side PIN attempt cooldown without changing stored PIN hashes or existing user records.
3. Test login, restore, logout, idle lock, and role access on a staging deployment.
4. Only then proceed to Cloud authentication and tenant isolation tests.

## Explicit non-goals for this checkpoint
- No changes to `auth.js` behavior.
- No localStorage data migration or deletion.
- No change to Supabase schema or RLS.
- No change to the production/default branch.
