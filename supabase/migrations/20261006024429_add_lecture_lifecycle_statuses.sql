-- PostgreSQL requires newly added enum values to be committed before later
-- migrations can use them in defaults, functions, or data changes.
alter type public.lecture_status add value if not exists 'before' before 'live';
alter type public.lecture_status add value if not exists 'pending' after 'before';
