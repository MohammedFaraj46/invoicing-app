# CLAUDE.md

Project context and working agreements for Claude Code. Read this before making changes.

## Project overview
A web-based invoicing application for Dutch freelancers (zzp'ers). A user manages customers, creates invoices with line items, generates a PDF, sends them, and tracks payment status. Also supports credit notes.

Scope: **NL-first** (KvK number, VAT number, Dutch invoicing rules), designed to be **extensible to EU** later (notably reverse-charge VAT). Do **not** try to make it EU tax-compliant across countries — that is explicitly out of scope.

The goal is a strong junior-level portfolio project with an emphasis on **backend depth**.

## Tech stack
- **Backend:** Java + Spring Boot (Spring Web, Spring Data JPA, Spring Security, Validation)
- **Database:** PostgreSQL, schema managed by **Flyway**
- **Frontend:** React + TypeScript (Vite)
- **PDF:** HTML template rendered to PDF (Thymeleaf + OpenHTMLtoPDF)
- **DevOps:** Docker, Docker Compose, GitHub Actions (CI), VPS + Nginx + HTTPS

## Architecture & conventions
- Layered backend: controller → service → repository. Keep controllers thin; business logic lives in services.
- Use DTOs for API input/output; do not expose JPA entities directly.
- Money calculations live in a dedicated `InvoiceCalculator` service, not scattered across controllers/entities.
- Code and identifiers in English. Comments in English.
- Write tests alongside the code, not afterwards.

## Non-negotiable design rules
These reflect deliberate decisions. Follow them unless explicitly told otherwise.

1. **Money:** always `BigDecimal`, never `double`/`float`. Set an explicit `RoundingMode`. The rounding strategy (per line vs. on total) must be consistent and covered by tests.
2. **VAT:** stored **per line** (`vatRate` + `vatType`) as a historical snapshot — never derived live from a central config. Multiple VAT rates on one invoice are allowed. `VatType`: `STANDARD` (21%), `REDUCED` (9%), `ZERO` (0%), `REVERSE_CHARGE`.
3. **Invoice numbering:** a definitive number is assigned **only on the DRAFT → SENT transition**, never when a draft is created. Numbers are unique and sequential per company per year, and are never reused. Allocation is **concurrency-safe** via the `NumberSequence` table (lock the row + increment within a transaction). Credit notes use their own sequence (`documentType = CREDIT_NOTE`).
4. **Immutability:** once an invoice is `SENT`, its lines, amounts, and VAT are immutable. The service layer must block edits. Store computed totals as a snapshot on the invoice at send time.
5. **Credit notes:** stored with **negative amounts** (`quantity` and `unitPrice` stay positive; the computed line amounts are negative). Always linked to the original invoice. Support **partial and full** crediting. Validate that total credited never exceeds the invoice's outstanding (not-yet-credited) amount.
6. **Auth:** JWT stored in an **httpOnly cookie** with `SameSite`, plus a **CSRF token** on state-changing requests.
7. **Data ownership:** every query is filtered by owner (company/user). Enforced on the **backend** — never rely on the frontend for access control. Tests must verify that user A cannot access user B's data (expect 403/404).
8. **Database changes:** every schema change is a **new Flyway migration** (`V2`, `V3`, …). Never edit a migration that has already been applied.

## Domain model (entities)
`User` (1—1) `Company` (1—*) `Customer`; `Company`/`Customer` (1—*) `Invoice` (1—*) `InvoiceLine`; `Invoice` (1—*) `CreditNote` (1—*) `CreditNoteLine`; `Company` (1—*) `NumberSequence`.

- `InvoiceStatus`: `DRAFT`, `SENT`, `PAID`, `OVERDUE`
- `CreditNoteStatus`: `DRAFT`, `SENT`
- `InvoiceLine` and `CreditNoteLine` are separate tables (clean, enforceable FKs); share a Java `@MappedSuperclass` to avoid duplication.

(If present, see `docs/` for the full MoSCoW scope and the detailed data model.)

## Testing
- Unit tests for the critical logic: money/rounding (e.g. 3 × €19.99), status transitions (valid and invalid), number allocation.
- Integration + security tests for critical API flows, using **Testcontainers** (real PostgreSQL, not H2).
- Ownership tests across all domains.

## Commands
Adjust to match the final project layout (e.g. `backend/` and `frontend/`).

```bash
# Database
docker compose up -d

# Backend (from backend/)
./mvnw spring-boot:run
./mvnw test

# Frontend (from frontend/)
npm install
npm run dev
npm run build
```

## What NOT to do
- Do not use `double`/`float` for money.
- Do not assign an invoice number to a draft.
- Do not allow edits to a sent invoice.
- Do not enforce access control only in React.
- Do not edit an already-applied Flyway migration.
- Do not pull VAT rates live onto historical invoices.
