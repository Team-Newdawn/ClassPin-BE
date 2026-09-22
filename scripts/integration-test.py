#!/usr/bin/env python3
"""Local-only HTTP integration checks. Never point this runner at production."""
import argparse, base64, json, re, uuid, urllib.request, urllib.error
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--status-file',required=True,type=Path)
parser.add_argument('--api',default='http://localhost:8080')
parser.add_argument('--keep-browser-fixture',action='store_true')
parser.add_argument('--with-conversion',action='store_true')
args=parser.parse_args()
if not re.fullmatch(r'http://(localhost|127\.0\.0\.1):\d+',args.api): raise SystemExit('Only local APIs are allowed')
config={m.group(1):m.group(2).strip('"') for line in args.status_file.read_text().splitlines() if (m:=re.match(r'^([A-Z_]+)=(.*)$',line))}
sb=config['API_URL']
if not re.fullmatch(r'http://(localhost|127\.0\.0\.1):\d+',sb): raise SystemExit('Only local Supabase is allowed')
key=config.get('PUBLISHABLE_KEY') or config['ANON_KEY']
service=config['SERVICE_ROLE_KEY'] # Used only to provision/delete local test identities.

def http(base,path,method='GET',body=None,token=None,expected=200,content_type=None):
    data=body if isinstance(body,bytes) else json.dumps(body).encode() if body is not None else None
    headers={'apikey':key}
    if token: headers['Authorization']='Bearer '+token
    if data is not None: headers['Content-Type']=content_type or 'application/json'
    request=urllib.request.Request(base+path,data=data,method=method,headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=120) as response: status=response.status;raw=response.read()
    except urllib.error.HTTPError as error: status=error.code;raw=error.read()
    allowed=expected if isinstance(expected,tuple) else (expected,)
    assert status in allowed, f'{method} {path}: expected {allowed}, got {status}: {raw[:250]!r}'
    try:return json.loads(raw) if raw else None
    except json.JSONDecodeError:return raw

def api(path,method='GET',body=None,token=None,expected=200): return http(args.api,path,method,body,token,expected)
def uid(): return str(uuid.uuid4())
def pdf_fixture(pages=3):
    objects=[b'<< /Type /Catalog /Pages 2 0 R >>',f'<< /Type /Pages /Kids [{" ".join(f"{3+i*2} 0 R" for i in range(pages))}] /Count {pages} >>'.encode()]
    for i in range(pages):
        content=f'BT /F1 28 Tf 40 200 Td (OhPin integration slide {i+1}) Tj ET'.encode()
        objects += [f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 640 360] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> /Contents {4+i*2} 0 R >>'.encode(),b'<< /Length '+str(len(content)).encode()+b' >>\nstream\n'+content+b'\nendstream']
    data=b'%PDF-1.4\n';offsets=[0]
    for i,obj in enumerate(objects,1):offsets.append(len(data));data+=f'{i} 0 obj\n'.encode()+obj+b'\nendobj\n'
    start=len(data);data+=f'xref\n0 {len(objects)+1}\n0000000000 65535 f \n'.encode()
    for offset in offsets[1:]:data+=f'{offset:010d} 00000 n \n'.encode()
    return data+f'trailer\n<< /Size {len(objects)+1} /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n'.encode()

users=[];saved=False
try:
    sessions=[]
    for role in ('owner','other'):
        email=f'ohpin-{role}-{uid()}@example.test';password=uid()+'Aa1!'
        user=http(sb,'/auth/v1/admin/users','POST',{'email':email,'password':password,'email_confirm':True},service,(200,201));users.append(user['id'])
        sessions.append(http(sb,'/auth/v1/token?grant_type=password','POST',{'email':email,'password':password}))
    owner,other=sessions;ot=owner['access_token'];xt=other['access_token'];owner_id=owner['user']['id']
    audience=http(sb,'/auth/v1/signup','POST',{},None,(200,201));users.append(audience['user']['id']);at=audience['access_token']
    api('/api/instructor/folders',expected=401)
    api('/api/instructor/folders',token=at,expected=403)
    api('/api/instructor/folders','POST',{'name':'  ','colorIndex':0},ot,400)
    folder=api('/api/instructor/folders','POST',{'name':'  Integration Course  ','colorIndex':4},ot)
    assert folder['name']=='Integration Course'
    api('/api/instructor/folders/'+folder['id'],'PATCH',{'name':'Renamed'},xt,404)
    def draft(code):
        return dict(id=uid(),courseId=uid(),materialId=uid(),materialVersionId=uid(),folderId=folder['id'],title='Integration material',fileName='fixture.pdf',sourcePath=owner_id+'/'+uid()+'/source.pdf',code=code,status='live',currentSlide=0,presentationInteractions=True,showQuestionPins=True,showPresentationQr=True,presentationQrPosition='top-right',questionCategories={'concept':{'label':'','enabled':True,'archived':False}},slides=[{'id':uid(),'pageIndex':i,'sourcePageIndex':i} for i in range(3)])
    d=draft(uuid.uuid4().hex[:8].upper())
    http(sb,'/storage/v1/object/course-materials/'+d['sourcePath'],'POST',pdf_fixture(),ot,(200,201),'application/pdf')
    api('/api/instructor/materials','POST',d,ot)
    if args.with_conversion:
        events=api('/api/convert','POST',{'sourcePath':d['sourcePath'],'fileName':'fixture.pdf'},ot)
        events=[json.loads(line) for line in events.splitlines()]
        assert events[0]=={'type':'meta','total':3} and events[-1]['type']=='done'
        for extension, mime in [('pptx','application/vnd.openxmlformats-officedocument.presentationml.presentation'),('ppt','application/vnd.ms-powerpoint')]:
            fixture_path=Path(__file__).resolve().parent.parent/'src/test/resources/fixtures'/('two-slides.'+extension)
            if not fixture_path.exists(): raise AssertionError('Missing conversion fixture '+extension)
            source=owner_id+'/'+uid()+'/source.'+extension
            http(sb,'/storage/v1/object/course-materials/'+source,'POST',fixture_path.read_bytes(),ot,(200,201),mime)
            raw=api('/api/convert','POST',{'sourcePath':source,'fileName':'fixture.'+extension},ot)
            events=[json.loads(line) for line in raw.splitlines()]
            assert events[0]=={'type':'meta','total':2} and events[-1]['type']=='done',events
            images=[event['slide'] for event in events if event['type']=='slide']
            assert len(images)==2 and all(image['imageUrl'].startswith(sb+'/') for image in images)
            http(sb,'/storage/v1/object/lecture-slides','DELETE',{'prefixes':[image['imagePath'] for image in images]},ot,(200,204))
            http(sb,'/storage/v1/object/course-materials','DELETE',{'prefixes':[source]},ot,(200,204))
        print('PASS: real PDF/PPT/PPTX conversion')

    api('/api/instructor/slides/'+d['slides'][0]['id']+'/note','PUT',{'body':'OWNER ONLY SECRET NOTE'},ot)
    graph=api('/api/instructor/courses',token=ot)
    assert 'OWNER ONLY SECRET NOTE' in json.dumps(graph)
    public=api('/api/participant/join/'+d['code'],token=at)
    assert 'note' not in json.dumps(public).lower()
    assert 'OWNER ONLY SECRET NOTE' not in json.dumps(public)
    assert api('/api/instructor/courses',token=xt)==[]
    api('/api/instructor/lectures/'+d['id'],'PATCH',{'current_page':1},ot)
    api('/api/instructor/lectures/'+d['id'],'PATCH',{'title':'hacked'},ot,400)
    api('/api/instructor/lectures/'+d['id'],'PATCH',{'current_page':1},xt,404)
    # Database RPC failure after the graph has been partly inserted must roll everything back.
    bad=draft(uuid.uuid4().hex[:8].upper());bad['slides'][1]['sourcePageIndex']=-1
    http(sb,'/rest/v1/rpc/ohpin_create_material','POST',{'draft':bad},ot,(400,403))
    assert http(sb,'/rest/v1/courses?id=eq.'+bad['courseId']+'&select=id',token=ot)==[]
    qid=uid();question={'id':qid,'slideId':d['slides'][0]['id'],'x':0.2,'y':0.3,'category':'concept','marker':'pin','text':'What does this mean?'}
    api('/api/participant/lectures/'+d['id']+'/questions','POST',question,at)
    public_questions=api('/api/participant/lectures/'+d['id']+'/questions',token=at)
    assert public_questions[0]['is_mine'] is True and 'author_id' not in public_questions[0]
    bad_q=dict(question,id=uid(),x=2)
    api('/api/participant/lectures/'+d['id']+'/questions','POST',bad_q,at,400)
    # Invalid category fails after anchor insertion; no orphan remains.
    before=http(sb,'/rest/v1/region_anchors?select=id&slide_id=eq.'+d['slides'][0]['id'],token=ot)
    params=dict(target_id=uid(),target_lecture_id=d['id'],target_slide_id=d['slides'][0]['id'],target_x=.4,target_y=.5,target_category='missing',target_marker='pin',target_text='invalid category')
    http(sb,'/rest/v1/rpc/ohpin_submit_point_question','POST',params,at,(400,403))
    assert http(sb,'/rest/v1/region_anchors?select=id&slide_id=eq.'+d['slides'][0]['id'],token=ot)==before
    api('/api/participant/questions/'+qid,'PATCH',{'category':'concept','marker':'idea','text':'Updated question'},at)
    api('/api/participant/questions/'+qid+'/reaction','PUT',{'reacted':True},at,403)
    peer=http(sb,'/auth/v1/signup','POST',{},None,(200,201));users.append(peer['user']['id'])
    api('/api/participant/questions/'+qid+'/reaction','PUT',{'reacted':True},peer['access_token'])
    api('/api/instructor/questions/'+qid+'/answers','POST',{'body':'The instructor answer'},xt,(400,403))
    api('/api/instructor/questions/'+qid+'/answers','POST',{'body':'The instructor answer'},ot)
    api('/api/instructor/questions/'+qid+'/resolved','PUT',None,ot)
    api('/api/participant/questions/'+qid,'PATCH',{'category':'concept','marker':'pin','text':'late edit'},at,404)
    image_path=owner_id+'/'+d['id']+'/appended/'+uid()+'.png'
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXZsAAAAASUVORK5CYII=')
    http(sb,'/storage/v1/object/lecture-slides/'+image_path,'POST',png,ot,(200,201),'image/png')
    appended=api('/api/instructor/versions/'+d['materialVersionId']+'/slides','POST',{'slides':[{'id':uid(),'imagePath':image_path}]},ot)
    assert appended[0]['page_index']==3
    api('/api/instructor/slides/'+appended[0]['id'],'DELETE',None,ot)
    api('/api/participant/experience','POST',{'code':d['code'],'experience':'Useful','improvement':'More examples'},at)
    api('/api/instructor/courses/'+d['courseId']+'/folder','PATCH',{'folderId':None},ot)
    # Keep a known fixture for browser regression only when explicitly requested.
    if args.keep_browser_fixture:
        fixture={'owner':owner,'audience':audience,'draft':d,'folder':folder}
        path=Path('/tmp/ohpin-browser-fixture.json');path.write_text(json.dumps(fixture));path.chmod(0o600)
        Path('/tmp/ohpin-fixture.pdf').write_bytes(pdf_fixture())
        saved=True
    else:
        deleted=api('/api/instructor/courses/'+d['courseId'],'DELETE',None,ot)
        assert deleted['deleted'] is True and deleted['cleanupPending'] is False
        assert api('/api/participant/join/'+d['code'],token=at) is None
        api('/api/instructor/folders/'+folder['id'],'DELETE',None,ot)
    print('PASS: authentication, ownership, folder/material CRUD, private notes, atomic rollback, questions, reactions, answers, slide append/delete, experience, cleanup')
finally:
    if not saved:
        for user_id in users:
            http(sb,'/auth/v1/admin/users/'+user_id,'DELETE',None,service,(200,204))
