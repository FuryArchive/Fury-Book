\set ON_ERROR_STOP on
BEGIN;
\set ACQUISITION_OUTER_TRANSACTION true
\ir ../migrations/20261012222846_acquisition_ledger_v2.sql
\ir acquisition_v2.sql
