CREATE TABLE brukernotifikasjon (
    id              INT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sykmelding_id   uuid        NOT NULL,
    timestamp       timestamptz NOT NULL,
    event           VARCHAR(64) NOT NULL,
    grupperingsId   uuid        NOT NULL
);

create index brukernotifikasjon_sykmelding_id_idx on brukernotifikasjon (sykmelding_id);
