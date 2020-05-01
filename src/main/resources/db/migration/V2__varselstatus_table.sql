CREATE TABLE varselstatus (
    id                      INT             GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sykmelding_id           uuid            NOT NULL,
    opprettet               timestamptz     NOT NULL,
    mottaker                VARCHAR(64)     NOT NULL,
    varselbestilling_id     uuid            NOT NULL
);

create index varselstatus_sykmelding_id_idx on varselstatus (sykmelding_id);
