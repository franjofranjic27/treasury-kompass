-- Schema treasury (treasury-service). Beträge in Mio., Zeitstempel in UTC.

create table client (
    id             uuid         primary key,
    name           varchar(200) not null,
    industry       varchar(200),
    employee_count int,
    fiscal_year    int,
    short_label    varchar(60),
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null
);
create unique index client_name_lower_uq on client (lower(name));

create table document (
    id                uuid         primary key,
    client_id         uuid         not null references client (id),
    filename          varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null,
    sha256            char(64)     not null,
    page_count        int          not null,
    status            varchar(20)  not null
        constraint document_status_ck check (status in ('UPLOADED', 'TEXT_EXTRACTED', 'INDEXED', 'EXTRACTED', 'FAILED')),
    ingest_id         uuid         not null,
    chunk_count       int,
    error_code        varchar(40),
    error_message     varchar(500),
    uploaded_at       timestamptz  not null,
    uploaded_by       varchar(100) not null,
    status_changed_at timestamptz  not null,
    constraint document_client_sha256_uq unique (client_id, sha256)
);
create index document_client_uploaded_idx on document (client_id, uploaded_at desc);

create table document_content (
    document_id uuid  primary key references document (id) on delete cascade,
    bytes       bytea not null
);

create table answer_proposal (
    id                uuid         primary key,
    client_id         uuid         not null references client (id),
    question_key      varchar(64)  not null,
    value             jsonb        not null,
    source            varchar(16)  not null
        constraint answer_proposal_source_ck check (source in ('DOCUMENT', 'RULE', 'AGENT')),
    status            varchar(16)  not null
        constraint answer_proposal_status_ck check (status in ('OPEN', 'APPLIED', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    rejection_reason  varchar(40),
    -- Dokumente dürfen gelöscht werden, Vorschläge bleiben (Fundstelle behält document_name).
    document_id       uuid         references document (id) on delete set null,
    document_name     varchar(255),
    page              int,
    location_label    varchar(200),
    excerpt           varchar(600),
    citation_verified boolean,
    confidence        varchar(8)
        constraint answer_proposal_confidence_ck check (confidence in ('HIGH', 'MEDIUM', 'LOW')),
    rationale         varchar(1000),
    conversation_id   uuid,
    created_at        timestamptz  not null,
    decided_at        timestamptz,
    decided_by        varchar(100)
);
create index answer_proposal_client_status_idx on answer_proposal (client_id, status);
create index answer_proposal_client_key_idx on answer_proposal (client_id, question_key);

create table answer (
    client_id    uuid        not null references client (id),
    question_key varchar(64) not null,
    value        jsonb       not null,
    status       varchar(16) not null
        constraint answer_status_ck check (status in ('PROPOSED', 'CONFIRMED')),
    source       varchar(16) not null
        constraint answer_source_ck check (source in ('DOCUMENT', 'RULE', 'AGENT', 'ADVISOR')),
    proposal_id  uuid references answer_proposal (id),
    updated_at   timestamptz not null,
    confirmed_at timestamptz,
    confirmed_by varchar(100),
    primary key (client_id, question_key)
);

create table hedge_setting (
    client_id      uuid        not null references client (id),
    currency       char(3)     not null,
    start_ratio    int
        constraint hedge_setting_start_ratio_ck check (start_ratio between 0 and 100),
    step_down      int         not null default 10
        constraint hedge_setting_step_down_ck check (step_down in (0, 5, 10, 15)),
    collar_enabled boolean,
    updated_at     timestamptz not null,
    primary key (client_id, currency)
);

create table checklist_state (
    client_id  uuid         not null references client (id),
    item_id    varchar(64)  not null,
    done       boolean      not null,
    changed_at timestamptz  not null,
    changed_by varchar(100) not null,
    primary key (client_id, item_id)
);

create table assessment_snapshot (
    client_id   uuid        not null references client (id),
    version     int         not null,
    inputs_hash char(64)    not null,
    result      jsonb       not null,
    created_at  timestamptz not null,
    primary key (client_id, version)
);

create table rationale (
    client_id          uuid         primary key references client (id),
    assessment_version int          not null,
    text               text         not null,
    source             varchar(16)  not null
        constraint rationale_source_ck check (source in ('AGENT_DRAFT', 'ADVISOR')),
    saved_at           timestamptz  not null,
    saved_by           varchar(100) not null
);

create table approval (
    id                 uuid         primary key,
    client_id          uuid         not null references client (id),
    assessment_version int          not null,
    approved_at        timestamptz  not null,
    approved_by        varchar(100) not null,
    protocol           jsonb        not null,
    protocol_text      text         not null,
    revoked_at         timestamptz,
    revoked_by         varchar(100),
    revocation_type    varchar(20)
        constraint approval_revocation_type_ck check (revocation_type in ('REVOKED_BY_CHANGE', 'WITHDRAWN')),
    revocation_reason  varchar(300)
);
-- Höchstens eine aktive Freigabe pro Kunde.
create unique index approval_active_uq on approval (client_id) where revoked_at is null;

create table audit_event (
    id                 bigserial    primary key,
    client_id          uuid         not null references client (id),
    occurred_at        timestamptz  not null,
    actor_type         varchar(10)  not null
        constraint audit_event_actor_type_ck check (actor_type in ('ADVISOR', 'SYSTEM', 'RULE', 'AGENT')),
    actor_name         varchar(100) not null,
    event_type         varchar(40)  not null,
    message            text         not null,
    rule_id            varchar(16),
    legal_basis        varchar(100),
    assessment_version int,
    details            jsonb
);
create index audit_event_client_occurred_idx on audit_event (client_id, occurred_at desc);

-- Audit-Trail ist append-only (INV-7): UPDATE, DELETE und TRUNCATE werden abgelehnt.
create function audit_event_reject_change() returns trigger
    language plpgsql as
$$
begin
    raise exception 'audit_event ist append-only: % ist nicht erlaubt', tg_op
        using errcode = 'insufficient_privilege';
end;
$$;

create trigger audit_event_no_update_delete
    before update or delete on audit_event
    for each row execute function audit_event_reject_change();

create trigger audit_event_no_truncate
    before truncate on audit_event
    for each statement execute function audit_event_reject_change();
