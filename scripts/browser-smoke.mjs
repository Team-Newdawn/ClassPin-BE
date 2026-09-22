import fs from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fixture=JSON.parse(await fs.readFile(process.env.OHPIN_BROWSER_FIXTURE || '/tmp/ohpin-browser-fixture.json','utf8'));
const setupHeaders={'Authorization':'Bearer '+fixture.owner.access_token,'Content-Type':'application/json'};
for (const [path,body] of [
 ['/api/instructor/lectures/'+fixture.draft.id,{current_page:0}],
 ['/api/instructor/courses/'+fixture.draft.courseId+'/folder',{folderId:fixture.folder.id}],
]) {
 const response=await fetch('http://localhost:8080'+path,{method:'PATCH',headers:setupHeaders,body:JSON.stringify(body)});
 if(!response.ok)throw new Error('Browser fixture setup failed: '+response.status);
}
const noteText='Browser verified note '+Date.now();
const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL || 'chrome',headless:true});
try {
 const context=await browser.newContext({viewport:{width:1440,height:1000}});
 const page=await context.newPage();
 await page.goto('http://localhost:3000/');await page.waitForURL('**/login');console.log('PASS signed-out redirect');
 await context.addInitScript(({owner,audience})=>{localStorage.setItem('sb-127-auth-token',JSON.stringify(owner));localStorage.setItem('pin-class-audience-auth-v1',JSON.stringify(audience));},{owner:fixture.owner,audience:fixture.audience});
 await page.goto('http://localhost:3000/');await page.waitForURL('**/admin/dashboard');
 await page.getByText('Integration Course',{exact:true}).first().waitFor();
 console.log('Dashboard:',(await page.locator('body').innerText()).slice(0,1300));
 await page.screenshot({path:'/tmp/ohpin-dashboard.png',fullPage:true});
 await page.goto('http://localhost:3000/admin/folders/'+fixture.folder.id);
 await page.getByRole('tab',{name:/인사이트|Insights/}).waitFor();
 await page.getByRole('button',{name:/목록 보기|List view/}).click();
 await page.getByRole('button',{name:/격자 보기|Grid view/}).click();
 await page.getByRole('tab',{name:/인사이트|Insights/}).click();
 console.log('PASS folder grid/list and insights');
 await page.goto('http://localhost:3000/admin/session/'+fixture.draft.id);
 await page.locator('#speaker-note').waitFor();
 console.log('Player:',(await page.locator('body').innerText()).slice(0,1800));
 await page.screenshot({path:'/tmp/ohpin-player.png',fullPage:true});
 await page.getByRole('button',{name:/폴더 패널 접기|Collapse folder panel/}).click();
 await page.getByRole('button',{name:/폴더 패널 열기|Expand folder panel/}).click();
 const resizer=page.getByRole('separator',{name:/질문 패널 너비 조절|Resize questions panel/});
 const old=await resizer.getAttribute('aria-valuenow');await resizer.focus();await page.keyboard.press('ArrowLeft');
 if(await resizer.getAttribute('aria-valuenow')===old)throw new Error('Panel resize did not change value');
 await page.locator('#speaker-note').fill(noteText);
 await page.getByRole('button',{name:/^메모 저장$|^Save notes$/}).click();
 await page.getByRole('button',{name:/^저장됨$|^Saved$/}).waitFor();
 await page.reload();await page.locator('#speaker-note').waitFor();
 if(await page.locator('#speaker-note').inputValue()!==noteText)throw new Error('Saved note was not persisted');
 const current=await page.locator('.filmstrip [aria-current="page"]').innerText();
 await page.locator('#speaker-note').focus();await page.keyboard.press('ArrowRight');
 if(await page.locator('.filmstrip [aria-current="page"]').innerText()!==current)throw new Error('Editing note moved slide');
 await page.locator('.stage-toolbar').click();await page.keyboard.press('ArrowRight');
 await page.waitForFunction(prev=>document.querySelector('.filmstrip [aria-current="page"]')?.textContent!==prev,current);
 const scroll=await page.locator('.filmstrip').evaluate(el=>getComputedStyle(el).overflowX);
 if(!['auto','scroll'].includes(scroll))throw new Error('Filmstrip scroll missing');
 await page.reload();await page.locator('#speaker-note').waitFor();
 console.log('PASS player panel collapse/resize, note save, scrollbar, keyboard navigation');
 await page.goto('http://localhost:3000/join/'+fixture.draft.code);
 await page.waitForSelector('canvas');
 const body=await page.locator('body').innerText();if(body.includes('Browser verified note') || body.includes('OWNER ONLY SECRET NOTE'))throw new Error('Owner note exposed');
 console.log('PASS participant player without instructor notes');
 await context.close();
} finally {await browser.close();}
