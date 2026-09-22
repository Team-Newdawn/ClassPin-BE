-- Application operations retain the caller's RLS context and commit as one transaction.
create or replace function public.ohpin_create_material(draft jsonb)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  owner uuid := (select auth.uid());
  course uuid := (draft->>'courseId')::uuid;
  lecture uuid := (draft->>'id')::uuid;
  material uuid := (draft->>'materialId')::uuid;
  version uuid := (draft->>'materialVersionId')::uuid;
  item jsonb;
  source text := draft->>'sourcePath';
  idx integer := 0;
begin
  if not (select private.is_admin()) then
    raise exception 'Instructor access required' using errcode = '42501';
  end if;
  if source is null or source not like owner::text || '/%' or source ~ '(^|/)\.\.(/|$)|%|\\' then
    raise exception 'Invalid source path' using errcode = '22023';
  end if;
  if jsonb_typeof(draft->'slides') is distinct from 'array' or jsonb_array_length(draft->'slides') < 1 then
    raise exception 'Slides required' using errcode = '22023';
  end if;
  insert into public.courses(id,owner_id,folder_id,title,visibility)
  values(course,owner,(draft->>'folderId')::uuid,draft->>'title','link');
  insert into public.lectures(id,course_id,title,join_code,status,current_page,presentation_interactions,show_question_pins,show_presentation_qr,presentation_qr_position,question_categories,started_at)
  values(lecture,course,draft->>'title',draft->>'code',(draft->>'status')::public.lecture_status,(draft->>'currentSlide')::integer,
    (draft->>'presentationInteractions')::boolean,(draft->>'showQuestionPins')::boolean,(draft->>'showPresentationQr')::boolean,
    draft->>'presentationQrPosition',draft->'questionCategories',now());
  insert into public.materials(id,course_id,lecture_id,type,file_name)
  values(material,course,lecture,case when lower(draft->>'fileName') like '%.pdf' then 'pdf' else 'slide_deck' end,draft->>'fileName');
  insert into public.material_versions(id,material_id,version_no,source_path) values(version,material,1,source);
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

create or replace function public.ohpin_submit_point_question(
  target_id uuid,target_lecture_id uuid,target_slide_id uuid,target_x double precision,target_y double precision,
  target_category text,target_marker text,target_text text
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  course uuid;
  version uuid;
  region uuid := gen_random_uuid();
begin
  if not (select private.is_participant()) then
    raise exception 'Participant access required' using errcode = '42501';
  end if;
  if target_x is null or target_y is null or not (target_x between 0 and 1) or not (target_y between 0 and 1) then
    raise exception 'Invalid point coordinates' using errcode = '22023';
  end if;
  select m.course_id,s.material_version_id into course,version
  from public.slides s join public.material_versions mv on mv.id=s.material_version_id
  join public.materials m on m.id=mv.material_id join public.lectures l on l.id=m.lecture_id
  where s.id=target_slide_id and l.id=target_lecture_id and l.status='live' and l.presentation_interactions;
  if not found then raise exception 'Live slide unavailable' using errcode = '42501'; end if;
  insert into public.region_anchors(id,slide_id,material_version_id,kind,coords,created_by)
  values(region,target_slide_id,version,'point',jsonb_build_object('x',target_x,'y',target_y),'user');
  insert into public.questions(id,course_id,lecture_id,slide_id,region_id,author_id,is_anonymous,category,marker,raw_text,status,occurred_in)
  values(target_id,course,target_lecture_id,target_slide_id,region,(select auth.uid()),true,target_category,target_marker,btrim(target_text),'unanswered','live');
  return target_id;
end;
$$;
revoke all on function public.ohpin_submit_point_question(uuid,uuid,uuid,double precision,double precision,text,text,text) from public,anon,authenticated;
grant execute on function public.ohpin_submit_point_question(uuid,uuid,uuid,double precision,double precision,text,text,text) to authenticated;

-- Durable deletion cleanup: Storage outages must not erase the paths needed for retry.
create table public.storage_cleanup_jobs (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  bucket text not null check (bucket in ('course-materials','lecture-slides')),
  object_path text not null,
  created_at timestamptz not null default now(),
  unique(owner_id,bucket,object_path)
);
alter table public.storage_cleanup_jobs enable row level security;
revoke all on public.storage_cleanup_jobs from public,anon,authenticated;
grant select,insert,delete on public.storage_cleanup_jobs to authenticated;
create policy "owners manage storage cleanup" on public.storage_cleanup_jobs for all to authenticated
using ((select private.is_admin()) and owner_id=(select auth.uid()))
with check ((select private.is_admin()) and owner_id=(select auth.uid()) and object_path like (select auth.uid())::text || '/%');

create or replace function private.enqueue_class_storage_cleanup()
returns trigger language plpgsql security invoker set search_path = '' as $$
declare path text; bucket_name text;
begin
  if tg_table_name='slides' then path := old.image_path; bucket_name := 'lecture-slides';
  else path := old.source_path; bucket_name := 'course-materials'; end if;
  if path is not null and path like (select auth.uid())::text || '/%' then
    insert into public.storage_cleanup_jobs(owner_id,bucket,object_path)
    values((select auth.uid()),bucket_name,path) on conflict do nothing;
  end if;
  return old;
end;
$$;
revoke all on function private.enqueue_class_storage_cleanup() from public,anon,authenticated;
create trigger enqueue_slide_cleanup before delete on public.slides for each row execute function private.enqueue_class_storage_cleanup();
create trigger enqueue_source_cleanup before delete on public.material_versions for each row execute function private.enqueue_class_storage_cleanup();

create or replace function public.ohpin_delete_course(target_course_id uuid)
returns uuid language plpgsql security invoker set search_path = '' as $$
begin
  if not (select private.is_admin()) then raise exception 'Instructor access required' using errcode='42501'; end if;
  delete from public.courses where id=target_course_id and owner_id=(select auth.uid());
  if not found then raise exception 'Course not found' using errcode='42501'; end if;
  return target_course_id;
end;
$$;
revoke all on function public.ohpin_delete_course(uuid) from public,anon,authenticated;
grant execute on function public.ohpin_delete_course(uuid) to authenticated;
