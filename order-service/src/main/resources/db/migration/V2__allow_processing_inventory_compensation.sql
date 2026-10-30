-- The live database had a Hibernate-generated status check before PROCESSING
-- was added to CompensationStatus. Keep the legacy baseline accurate, then
-- widen the constraint for the current retry worker state.
ALTER TABLE public.inventory_compensations
    DROP CONSTRAINT IF EXISTS inventory_compensations_status_check;

ALTER TABLE public.inventory_compensations
    ADD CONSTRAINT inventory_compensations_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));
