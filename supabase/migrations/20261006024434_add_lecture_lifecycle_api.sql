-- Keep backend-owned migration history runnable even when the matching frontend
-- migrations have already been applied to a shared Supabase project.
alter table public.lectures
  add column if not exists presentation_autoplay boolean not null default false,
  add column if not exists allow_question_reactions boolean not null default true,
  add column if not exists allow_emoji_reactions boolean not null default true;

alter table public.lectures alter column status set default 'before';

alter table public.session_folders
  add column if not exists purpose text not null default 'qa',
  add column if not exists purpose_label text;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.session_folders'::regclass
      and conname = 'session_folders_purpose_check'
  ) then
    alter table public.session_folders
      add constraint session_folders_purpose_check
      check (purpose in ('qa', 'feedback', 'education', 'brainstorming', 'other'));
  end if;
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.session_folders'::regclass
      and conname = 'session_folders_purpose_label_check'
  ) then
    alter table public.session_folders
      add constraint session_folders_purpose_label_check
      check (
        purpose_label is null
        or (purpose = 'other' and char_length(btrim(purpose_label)) between 1 and 40)
      );
  end if;
end;
$$;

create or replace function public.ohpin_create_material(draft jsonb)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  owner uuid := (select auth.uid());
  course uuid := (draft->>'courseId')::uuid;
  lecture uuid := (draft->>'id')::uuid;
  material uuid := (draft->>'materialId')::uuid;
  version uuid := (draft->>'materialVersionId')::uuid;
  requested_status text := coalesce(draft->>'status', 'before');
  item jsonb;
  source text := draft->>'sourcePath';
  idx integer := 0;
begin
  if not (select private.is_admin()) then
    raise exception 'Instructor access required' using errcode = '42501';
  end if;
  if requested_status <> 'before' then
    raise exception 'New lectures must start before preparation' using errcode = '22023';
  end if;
  if source is null or source not like owner::text || '/%' or source ~ '(^|/)\.\.(/|$)|%|\\' then
    raise exception 'Invalid source path' using errcode = '22023';
  end if;
  if jsonb_typeof(draft->'slides') is distinct from 'array' or jsonb_array_length(draft->'slides') < 1 then
    raise exception 'Slides required' using errcode = '22023';
  end if;

  insert into public.courses(id,owner_id,folder_id,title,visibility)
  values(course,owner,(draft->>'folderId')::uuid,draft->>'title','link');
  insert into public.lectures(
    id,course_id,title,join_code,status,current_page,presentation_interactions,
    show_question_pins,show_presentation_qr,presentation_qr_position,question_categories
  ) values (
    lecture,course,draft->>'title',draft->>'code','before',
    (draft->>'currentSlide')::integer,(draft->>'presentationInteractions')::boolean,
    (draft->>'showQuestionPins')::boolean,(draft->>'showPresentationQr')::boolean,
    draft->>'presentationQrPosition',draft->'questionCategories'
  );
  insert into public.materials(id,course_id,lecture_id,type,file_name)
  values(material,course,lecture,case when lower(draft->>'fileName') like '%.pdf' then 'pdf' else 'slide_deck' end,draft->>'fileName');
  insert into public.material_versions(id,material_id,version_no,source_path)
  values(version,material,1,source);

  for item in select value from jsonb_array_elements(draft->'slides') loop
    if (item->>'pageIndex')::integer is distinct from idx
      or (item->>'imagePath' is not null and (item->>'imagePath' not like owner::text || '/%' or item->>'imagePath' ~ '(^|/)\.\.(/|$)|%|\\'))
      or (item->>'sourcePageIndex' is not null and lower(draft->>'fileName') not like '%.pdf') then
      raise exception 'Invalid slide source or order' using errcode = '22023';
    end if;
    insert into public.slides(id,material_version_id,page_index,image_path,source_page_index)
    values((item->>'id')::uuid,version,idx,item->>'imagePath',(item->>'sourcePageIndex')::integer);
    idx := idx + 1;
  end loop;
  if (draft->>'currentSlide')::integer >= idx then
    raise exception 'Current slide out of range' using errcode = '22023';
  end if;
  return lecture;
end;
$$;

revoke all on function public.ohpin_create_material(jsonb) from public,anon,authenticated;
grant execute on function public.ohpin_create_material(jsonb) to authenticated;

create or replace function public.ohpin_update_lecture_settings(
  target_lecture_id uuid,
  settings jsonb
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  current_lecture public.lectures%rowtype;
  next_status text;
  slide_count integer;
begin
  if not (select private.is_admin()) then
    raise exception 'Instructor access required' using errcode = '42501';
  end if;
  if settings is null or jsonb_typeof(settings) <> 'object' or settings = '{}'::jsonb then
    raise exception 'Lecture settings are required' using errcode = '22023';
  end if;
  if exists (
    select 1 from jsonb_object_keys(settings) as item(key)
    where key not in (
      'current_page','status','presentation_interactions','presentation_autoplay',
      'show_question_pins','show_presentation_qr','presentation_qr_position',
      'question_categories','allow_question_reactions','allow_emoji_reactions'
    )
  ) then
    raise exception 'Unsupported lecture field' using errcode = '22023';
  end if;

  select lecture.* into current_lecture
  from public.lectures as lecture
  join public.courses as course on course.id = lecture.course_id
  where lecture.id = target_lecture_id and course.owner_id = (select auth.uid())
  for update of lecture;
  if not found then
    raise exception 'Lecture not found' using errcode = '42501';
  end if;

  if exists (
    select 1 from jsonb_object_keys(settings) as item(key)
    where key in (
      'presentation_interactions','presentation_autoplay','show_question_pins',
      'show_presentation_qr','allow_question_reactions','allow_emoji_reactions'
    ) and jsonb_typeof(settings->key) <> 'boolean'
  ) then
    raise exception 'Invalid boolean setting' using errcode = '22023';
  end if;

  next_status := current_lecture.status::text;
  if settings ? 'status' then
    if jsonb_typeof(settings->'status') <> 'string' then
      raise exception 'Invalid lecture status' using errcode = '22023';
    end if;
    next_status := settings->>'status';
    if next_status not in ('before','pending','live','ended')
      or (current_lecture.status::text, next_status) not in (
        ('before','pending'),('pending','before'),('before','live'),
        ('pending','live'),('live','ended'),('ended','live')
      ) then
      raise exception 'Invalid lecture status transition' using errcode = '22023';
    end if;
  end if;

  if settings ? 'presentation_qr_position'
    and (
      jsonb_typeof(settings->'presentation_qr_position') <> 'string'
      or settings->>'presentation_qr_position' not in ('top-left','top-right','bottom-left','bottom-right')
    ) then
    raise exception 'Invalid QR position' using errcode = '22023';
  end if;
  if settings ? 'question_categories'
    and jsonb_typeof(settings->'question_categories') <> 'object' then
    raise exception 'Categories must be an object' using errcode = '22023';
  end if;
  if settings ? 'current_page' then
    if jsonb_typeof(settings->'current_page') <> 'number'
      or settings->>'current_page' !~ '^[0-9]+$' then
      raise exception 'Invalid page index' using errcode = '22023';
    end if;
    select count(*) into slide_count
    from public.slides as slide
    where slide.material_version_id = (
      select version.id
      from public.materials as material
      join public.material_versions as version on version.material_id = material.id
      where material.lecture_id = target_lecture_id
      order by material.created_at, version.version_no desc
      limit 1
    );
    if (settings->>'current_page')::integer >= slide_count then
      raise exception 'Page index is outside the lecture slides' using errcode = '22023';
    end if;
  end if;

  update public.lectures set
    current_page = case when settings ? 'current_page' then (settings->>'current_page')::integer else current_page end,
    status = case when settings ? 'status' then next_status::public.lecture_status else status end,
    presentation_interactions = case when settings ? 'presentation_interactions' then (settings->>'presentation_interactions')::boolean else presentation_interactions end,
    presentation_autoplay = case when settings ? 'presentation_autoplay' then (settings->>'presentation_autoplay')::boolean else presentation_autoplay end,
    show_question_pins = case when settings ? 'show_question_pins' then (settings->>'show_question_pins')::boolean else show_question_pins end,
    show_presentation_qr = case when settings ? 'show_presentation_qr' then (settings->>'show_presentation_qr')::boolean else show_presentation_qr end,
    presentation_qr_position = case when settings ? 'presentation_qr_position' then settings->>'presentation_qr_position' else presentation_qr_position end,
    question_categories = case when settings ? 'question_categories' then settings->'question_categories' else question_categories end,
    allow_question_reactions = case when settings ? 'allow_question_reactions' then (settings->>'allow_question_reactions')::boolean else allow_question_reactions end,
    allow_emoji_reactions = case when settings ? 'allow_emoji_reactions' then (settings->>'allow_emoji_reactions')::boolean else allow_emoji_reactions end,
    started_at = case when settings ? 'status' and next_status = 'live' then now() else started_at end,
    ended_at = case
      when settings ? 'status' and next_status = 'live' then null
      when settings ? 'status' and next_status = 'ended' then now()
      else ended_at
    end
  where id = target_lecture_id;
  return target_lecture_id;
end;
$$;

revoke all on function public.ohpin_update_lecture_settings(uuid,jsonb) from public,anon,authenticated;
grant execute on function public.ohpin_update_lecture_settings(uuid,jsonb) to authenticated;

create or replace function private.participant_lecture_status(
  target_id uuid,
  target_join_code text
) returns text language plpgsql stable security definer set search_path = '' as $$
declare
  result text;
begin
  if not (select private.is_participant()) then
    raise exception 'Participant access required' using errcode = '42501';
  end if;
  if (target_id is null) = (target_join_code is null) then
    raise exception 'Exactly one lecture selector is required' using errcode = '22023';
  end if;
  select lecture.status::text into result
  from public.lectures as lecture
  where (target_id is not null and lecture.id = target_id)
     or (target_join_code is not null and lecture.join_code = target_join_code)
  limit 1;
  return result;
end;
$$;

revoke all on function private.participant_lecture_status(uuid,text) from public,anon,authenticated;
grant execute on function private.participant_lecture_status(uuid,text) to authenticated;

create or replace function public.ohpin_participant_lecture_status(
  target_id uuid,
  target_join_code text
) returns text language sql stable security invoker set search_path = '' as $$
  select private.participant_lecture_status(target_id, target_join_code);
$$;

revoke all on function public.ohpin_participant_lecture_status(uuid,text) from public,anon,authenticated;
grant execute on function public.ohpin_participant_lecture_status(uuid,text) to authenticated;

create or replace function private.set_question_reaction(target_question_id uuid, target_reacted boolean)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := (select auth.uid());
  target_lecture_id uuid;
  target_author_id uuid;
  target_lecture_status public.lecture_status;
  target_question_status public.question_status;
  reactions_allowed boolean;
begin
  if actor_id is null or not (select private.is_participant()) then
    raise exception using errcode = '42501', message = 'Participant authentication required';
  end if;
  if target_reacted is null then
    raise exception using errcode = '22004', message = 'Reaction state is required';
  end if;
  select question.lecture_id into target_lecture_id
  from public.questions as question where question.id = target_question_id;
  if not found then
    raise exception using errcode = '42501', message = 'Question cannot be reacted to';
  end if;
  select lecture.status, lecture.allow_question_reactions
  into target_lecture_status, reactions_allowed
  from public.lectures as lecture where lecture.id = target_lecture_id for share;
  select question.author_id, question.status
  into target_author_id, target_question_status
  from public.questions as question
  where question.id = target_question_id and question.lecture_id = target_lecture_id
  for update;
  if not found
    or target_lecture_status is distinct from 'live'::public.lecture_status
    or reactions_allowed is not true
    or target_question_status = 'archived'::public.question_status then
    raise exception using errcode = '42501', message = 'Question cannot be reacted to';
  end if;
  if target_author_id = actor_id then
    raise exception using errcode = '42501', message = 'Authors cannot react to their own question';
  end if;
  if target_reacted then
    insert into public.question_reactions(question_id, reactor_id)
    values (target_question_id, actor_id)
    on conflict (question_id, reactor_id) do nothing;
  else
    delete from public.question_reactions
    where question_id = target_question_id and reactor_id = actor_id;
  end if;
  return target_reacted;
end;
$$;

revoke all on function private.set_question_reaction(uuid,boolean) from public,anon,authenticated;
grant execute on function private.set_question_reaction(uuid,boolean) to authenticated;

create or replace function private.delete_participant_question(target_question_id uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := (select auth.uid());
  target_lecture_id uuid;
  target_region_id uuid;
  target_question_status public.question_status;
  target_lecture_status public.lecture_status;
begin
  if actor_id is null or not (select private.is_participant()) then
    raise exception using errcode = '42501', message = 'Participant authentication required';
  end if;
  select question.lecture_id, question.region_id, question.status
  into target_lecture_id, target_region_id, target_question_status
  from public.questions as question
  where question.id = target_question_id and question.author_id = actor_id
  for update;
  if not found then
    raise exception using errcode = '42501', message = 'Question cannot be deleted';
  end if;
  select lecture.status into target_lecture_status
  from public.lectures as lecture where lecture.id = target_lecture_id for share;
  if target_lecture_status is distinct from 'live'::public.lecture_status
    or target_question_status is distinct from 'unanswered'::public.question_status
    or exists (select 1 from public.answers where question_id = target_question_id) then
    raise exception using errcode = '42501', message = 'Question cannot be deleted';
  end if;
  delete from public.questions where id = target_question_id;
  if target_region_id is not null then
    delete from public.region_anchors where id = target_region_id;
  end if;
  return target_question_id;
end;
$$;

revoke all on function private.delete_participant_question(uuid) from public,anon,authenticated;
grant execute on function private.delete_participant_question(uuid) to authenticated;

create or replace function public.ohpin_delete_participant_question(target_question_id uuid)
returns uuid language sql security invoker set search_path = '' as $$
  select private.delete_participant_question(target_question_id);
$$;

revoke all on function public.ohpin_delete_participant_question(uuid) from public,anon,authenticated;
grant execute on function public.ohpin_delete_participant_question(uuid) to authenticated;

drop policy if exists "lecture emoji broadcasts can be received" on realtime.messages;
create policy "lecture emoji broadcasts can be received"
on realtime.messages for select to authenticated using (
  realtime.messages.extension = 'broadcast'
  and (select realtime.topic()) ~ '^lecture-reactions:[0-9a-fA-F-]{36}$'
  and exists (
    select 1 from public.lectures as lecture
    join public.courses as course on course.id = lecture.course_id
    where lecture.id = split_part((select realtime.topic()), ':', 2)::uuid
      and lecture.status = 'live'
      and lecture.allow_emoji_reactions
      and (
        (select private.is_participant())
        or ((select private.is_admin()) and course.owner_id = (select auth.uid()))
      )
  )
);

drop policy if exists "participants can send lecture emoji broadcasts" on realtime.messages;
create policy "participants can send lecture emoji broadcasts"
on realtime.messages for insert to authenticated with check (
  realtime.messages.extension = 'broadcast'
  and (select realtime.topic()) ~ '^lecture-reactions:[0-9a-fA-F-]{36}$'
  and (select private.is_participant())
  and exists (
    select 1 from public.lectures as lecture
    where lecture.id = split_part((select realtime.topic()), ':', 2)::uuid
      and lecture.status = 'live'
      and lecture.allow_emoji_reactions
  )
);

notify pgrst, 'reload schema';
