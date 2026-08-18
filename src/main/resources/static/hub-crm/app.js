const state={view:'clientes'};
const $=s=>document.querySelector(s);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function request(url,options){const r=await fetch(url,options);if(!r.ok)throw new Error((await r.text())||`HTTP ${r.status}`);return r.json()}
function metrics(summary){$('#metrics').innerHTML=Object.entries(summary).map(([k,v])=>`<div class="metric"><span>${esc(k.replace(/([A-Z])/g,' $1'))}</span><strong>${esc(v)}</strong></div>`).join('')}
async function status(){try{const data=await request('/api/hub-crm/status');metrics(data.summary);const h=$('#health');h.textContent=data.pollingEnabled?'Polling ativo • 30s':'Polling desativado';h.className='pill '+(data.enabled?'ok':'error')}catch(e){$('#health').textContent='Indisponível';$('#health').className='pill error';message(e.message,true)}}
async function table(){const data=await request(`/api/hub-crm/${state.view}?limit=200`);const keys=data.length?Object.keys(data[0]):[];$('#head').innerHTML=`<tr>${keys.map(k=>`<th>${esc(k)}</th>`).join('')}</tr>`;$('#rows').innerHTML=data.map(row=>`<tr>${keys.map(k=>`<td>${esc(row[k])}</td>`).join('')}</tr>`).join('')||'<tr><td>Nenhum registro.</td></tr>'}
function message(text,error=false){$('#message').textContent=text;$('#message').style.color=error?'#9b1225':'#18723d'}
async function action(url,confirmation){if(confirmation&&!confirm(confirmation))return;document.querySelectorAll('button').forEach(b=>b.disabled=true);try{const result=await request(url,{method:'POST'});message(JSON.stringify(result));await status();await table()}catch(e){message(e.message,true)}finally{document.querySelectorAll('button').forEach(b=>b.disabled=false)}}
document.querySelectorAll('.tab').forEach(b=>b.onclick=()=>{document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));b.classList.add('active');state.view=b.dataset.view;table()});
$('#validate').onclick=()=>action('/api/hub-crm/acoes/validar-arpa');
$('#sync').onclick=()=>action('/api/hub-crm/acoes/sincronizar');
$('#retry').onclick=()=>{const tipo=prompt('Tipo: CLIENTE ou COTACAO');if(!tipo)return;const id=prompt('ID no legado');if(!id)return;action(`/api/hub-crm/acoes/reprocessar?tipo=${encodeURIComponent(tipo)}&id=${encodeURIComponent(id)}`)};
$('#load').onclick=()=>action('/api/hub-crm/acoes/carga-inicial','O piloto cria até 10 organizações, pessoas e cards reais no ArpaSuite. Continuar?');
$('#loadAll').onclick=()=>action('/api/hub-crm/acoes/carga-completa','O lote completo cria todos os registros pendentes no ArpaSuite. Execute somente após validar o piloto. Continuar?');
status();table();setInterval(()=>{status();table()},30000);
