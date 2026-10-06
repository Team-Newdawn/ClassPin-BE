begin;

select plan(19);

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at, is_anonymous
) values
  ('00000000-0000-0000-0000-000000000000', '13000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'lifecycle-owner@test.local', '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now(), false),
  ('00000000-0000-0000-0000-000000000000', '23000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', null, '', now(), '{"provider":"anonymous","providers":["anonymous"]}', '{}', now(), now(), true),
  ('00000000-0000-0000-0000-000000000000', '23000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', null, '', now(), '{"provider":"anonymous","providers":["anonymous"]}', '{}', now(), now(), true);

insert into public.courses (id, owner_id, title)
values ('43000000-0000-0000-0000-000000000004', '13000000-0000-0000-0000-000000000001', 'Lifecycle fixture');
insert into public.lectures (id, course_id, title, join_code)
values ('53000000-0000-0000-0000-000000000005', '43000000-0000-0000-0000-000000000004', 'Lifecycle fixture', 'LIFE01');
insert into public.materials (id, course_id, lecture_id, type, file_name)
values ('63000000-0000-0000-0000-000000000006', '43000000-0000-0000-0000-000000000004', '53000000-0000-0000-0000-000000000005', 'pdf', 'fixture.pdf');
insert into public.material_versions (id, material_id, source_path)
values ('73000000-0000-0000-0000-000000000007', '63000000-0000-0000-0000-000000000006', 'fixture.pdf');
insert into public.slides (id, material_version_id, page_index, image_path)
values
  ('83000000-0000-0000-0000-000000000008', '73000000-0000-0000-0000-000000000007', 0, 'fixture-0.jpg'),
  ('83000000-0000-0000-0000-000000000009', '73000000-0000-0000-0000-000000000007', 1, 'fixture-1.jpg');

select is(
  (select status::text from public.lectures where id = '53000000-0000-0000-0000-000000000005'),
  'before',
  'new lectures default to before'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '23000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

select is(
  public.ohpin_participant_lecture_status(null, 'LIFE01'),
  'before',
  'participant status lookup distinguishes a before lecture'
);
select is(
  (select count(*)::integer from public.lectures where id = '53000000-0000-0000-0000-000000000005'),
  0,
  'participant still cannot read before lecture data'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '13000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

select lives_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"pending"}')$$,
  'before can transition to pending'
);
select throws_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"ended"}')$$,
  '22023', 'Invalid lecture status transition',
  'pending cannot transition directly to ended'
);
select lives_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"before"}')$$,
  'pending can return to before'
);
select lives_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"live","current_page":1}')$$,
  'before can start live on a valid slide'
);
select ok(
  (select started_at is not null and ended_at is null and current_page = 1 from public.lectures where id = '53000000-0000-0000-0000-000000000005'),
  'starting records started_at and preserves a valid page'
);
select throws_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"current_page":2}')$$,
  '22023', 'Page index is outside the lecture slides',
  'page index must remain within the slide count'
);
select throws_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"pending"}')$$,
  '22023', 'Invalid lecture status transition',
  'live cannot transition back to pending'
);
select lives_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"ended"}')$$,
  'live can end'
);
select ok(
  (select ended_at is not null from public.lectures where id = '53000000-0000-0000-0000-000000000005'),
  'ending records ended_at'
);
select lives_ok(
  $$select public.ohpin_update_lecture_settings('53000000-0000-0000-0000-000000000005', '{"status":"live"}')$$,
  'ended can restart live'
);
select ok(
  (select started_at is not null and ended_at is null from public.lectures where id = '53000000-0000-0000-0000-000000000005'),
  'restarting refreshes the live window and clears ended_at'
);

reset role;
insert into public.region_anchors (id, slide_id, material_version_id, kind, coords, created_by)
values ('93000000-0000-0000-0000-000000000009', '83000000-0000-0000-0000-000000000008', '73000000-0000-0000-0000-000000000007', 'point', '{"x":0.4,"y":0.6}', 'user');
insert into public.questions (id, course_id, lecture_id, slide_id, region_id, author_id, category, marker, raw_text)
values ('a3000000-0000-0000-0000-000000000010', '43000000-0000-0000-0000-000000000004', '53000000-0000-0000-0000-000000000005', '83000000-0000-0000-0000-000000000008', '93000000-0000-0000-0000-000000000009', '23000000-0000-0000-0000-000000000002', 'concept', 'pin', 'Delete me');

set local role authenticated;
select set_config('request.jwt.claim.sub', '23000000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select lives_ok(
  $$select public.ohpin_delete_participant_question('a3000000-0000-0000-0000-000000000010')$$,
  'author can delete an unanswered question during live'
);
reset role;
select ok(
  not exists (select 1 from public.questions where id = 'a3000000-0000-0000-0000-000000000010')
  and not exists (select 1 from public.region_anchors where id = '93000000-0000-0000-0000-000000000009'),
  'question deletion also removes its orphaned anchor'
);

insert into public.questions (id, course_id, lecture_id, slide_id, author_id, category, marker, raw_text)
values ('a3000000-0000-0000-0000-000000000011', '43000000-0000-0000-0000-000000000004', '53000000-0000-0000-0000-000000000005', '83000000-0000-0000-0000-000000000008', '23000000-0000-0000-0000-000000000002', 'concept', 'pin', 'React to me');
update public.lectures set allow_question_reactions = false
where id = '53000000-0000-0000-0000-000000000005';

set local role authenticated;
select set_config('request.jwt.claim.sub', '23000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select throws_ok(
  $$select public.set_question_reaction('a3000000-0000-0000-0000-000000000011', true)$$,
  '42501', 'Question cannot be reacted to',
  'question reactions are rejected when disabled'
);
reset role;

select ok(
  exists (
    select 1 from pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname = 'participants can send lecture emoji broadcasts'
      and with_check like '%allow_emoji_reactions%'
  ),
  'emoji broadcasts have a lecture setting authorization policy'
);

select is(
  (select count(*)::integer from unnest(enum_range(null::public.lecture_status)) as status
   where status::text in ('before','pending','live','ended','archived')),
  5,
  'lecture status enum contains the complete lifecycle'
);

select * from finish();

rollback;
