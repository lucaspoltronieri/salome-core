const state={view:'clientes',rows:[],autoApproval:false};
const $=s=>document.querySelector(s);
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function request(url,options){const r=await fetch(url,options);if(!r.ok)throw new Error((await r.text())||`HTTP ${r.status}`);return r.json()}
function metrics(summary){$('#metrics').innerHTML=Object.entries(summary).map(([k,v])=>`<div class="metric"><span>${esc(k.replace(/([A-Z])/g,' $1'))}</span><strong>${esc(v)}</strong></div>`).join('')}
async function status(){try{const data=await request('/api/hub-crm/status');state.autoApproval=!!data.autoApprovalEnabled;metrics(data.summary);const h=$('#health');h.textContent=(data.pollingEnabled?'Polling ativo • 30s':'Polling desativado')+(state.autoApproval?' • Aprovação por CT-e ligada':' • Aprovação por CT-e desligada');h.className='pill '+(data.enabled?'ok':'error');if(data.scheduler&&data.scheduler.lastError)message('Último erro do polling: '+data.scheduler.lastError,true);toolbar()}catch(e){$('#health').textContent='Indisponível';$('#health').className='pill error';message(e.message,true)}}

// Colunas com nome amigável por aba (as demais abas mostram as colunas cruas da API).
const COLUMNS={
  'aprovacoes-cte':[['created_at','Quando'],['status','Situação'],['legacy_quote_id','Cotação'],['quote_responsavel','Responsável'],['quote_status_anterior','Status anterior'],['cte_numero','CT-e'],['cte_serie','Série'],['cte_emissao','Emissão'],['pagador_cnpj','Pagador'],['quote_frete','Frete cotação'],['cte_frete','Frete CT-e'],['criterios','Critérios'],['divergencias','Divergências']],
  'logs':[['created_at','Quando'],['entity_type','Tipo'],['entity_id','ID'],['event_type','Evento'],['status','Situação'],['response_summary','Resultado'],['last_error','Erro']],
  'lote':[['acao','Ação'],['resultado','Resultado'],['cotacao','Cotação'],['responsavel','Responsável'],['statusAnterior','Status anterior'],['criada','Criada'],['cte','CT-e'],['emissao','Emissão'],['freteCotacao','Frete cotação'],['freteCte','Frete CT-e'],['detalhe','Detalhe']]
};
const STATUS_CLASS={APROVADA_AUTO:'ok',PROCESSADO:'ok',INTEGRADO:'ok',APROVADA:'ok',NAO_APROVADA:'neutral',SIMULADO:'neutral',AMBIGUO:'warn',REVISAO:'warn',CONCORRENCIA:'warn',ERRO:'error'};
const HINTS={
  'aprovacoes-cte':'CT-es que aprovaram (ou quase aprovaram) uma cotação no legado. AMBIGUO/CONCORRENCIA pedem conferência manual.',
  'logs':'Histórico de tudo que o Hub fez: aprovações por CT-e, cotações e clientes enviados ao ArpaSuite, com o erro quando houver.',
  'lote':'Lote único: aprova toda cotação que tem CT-e correspondente (CT-es desde 2021) e marca como NÃO APROVADA (motivo Preço) as ABERTAS anteriores a 31/08/2026 sem CT-e. Simule primeiro, confira a lista e só então execute.'
};
function cell(key,value){if((key==='status'||key==='sync_status'||key==='resultado')&&value){return `<span class="pill ${STATUS_CLASS[value]||'neutral'}">${esc(value)}</span>`}const text=esc(value);return key==='criterios'||key==='divergencias'||key==='response_summary'||key==='last_error'?`<span class="wrap">${text}</span>`:text}
function render(){const term=$('#search').value.trim().toLowerCase();const rows=term?state.rows.filter(r=>Object.values(r).some(v=>String(v??'').toLowerCase().includes(term))):state.rows;const cols=COLUMNS[state.view]||(state.rows.length?Object.keys(state.rows[0]).map(k=>[k,k]):[]);$('#head').innerHTML=`<tr>${cols.map(([,l])=>`<th>${esc(l)}</th>`).join('')}</tr>`;$('#rows').innerHTML=rows.map(row=>`<tr>${cols.map(([k])=>`<td>${cell(k,row[k])}</td>`).join('')}</tr>`).join('')||`<tr><td colspan="${Math.max(cols.length,1)}">Nenhum registro.</td></tr>`}
function url(){if(state.view==='lote')return '/api/hub-crm/lote';if(state.view==='logs'){const p=new URLSearchParams({limit:'300'});if($('#logType').value)p.set('tipo',$('#logType').value);if($('#logErrors').checked)p.set('erros','true');return `/api/hub-crm/logs?${p}`}return `/api/hub-crm/${state.view}?limit=200`}
async function table(){try{state.rows=await request(url());render()}catch(e){message(e.message,true)}}
function toolbar(){const logs=state.view==='logs';const lote=state.view==='lote'&&state.autoApproval;$('#logType').hidden=!logs;$('#logErrorsWrap').hidden=!logs;$('#approveNow').hidden=state.view!=='aprovacoes-cte'||!state.autoApproval;$('#batchSimulate').hidden=!lote;$('#batchRun').hidden=!lote;if(state.view!=='lote')$('#hint').textContent=HINTS[state.view]||''}
function totalsText(t){const names={'APROVAR:SIMULADO':'aprovaria','APROVAR:APROVADA':'aprovadas','NAO_APROVAR:SIMULADO':'não aprovaria (Preço)','NAO_APROVAR:NAO_APROVADA':'não aprovadas (Preço)','AMBIGUO:REVISAO':'ambíguas (conferir)'};return Object.entries(t||{}).map(([k,v])=>`${names[k]||k}: ${v}`).join(' • ')}
async function batchStatus(){try{const b=await request('/api/hub-crm/lote/status');const mode=b.execute?'EXECUÇÃO':'SIMULAÇÃO';const head=b.startedAt?`${mode} — ${b.phase}${b.running?' …':''}`:b.phase;$('#hint').textContent=`${HINTS.lote}\n${head}${b.totals&&Object.keys(b.totals).length?' — '+totalsText(b.totals):''}${b.error?' — ERRO: '+b.error:''}`;if(b.running){clearTimeout(state.batchTimer);state.batchTimer=setTimeout(async()=>{await batchStatus();},5000)}else if(state.batchWasRunning){state.batchWasRunning=false;await table();await status()}state.batchWasRunning=b.running;return b}catch(e){message(e.message,true)}}
async function startBatch(executar){const msg=executar?'EXECUTAR o lote? Isto grava no legado: aprova as cotações com CT-e e marca como NÃO APROVADA (Preço) as abertas anteriores a 31/08/2026 sem CT-e. Os cards da Fernanda/Jaci viram ganho/perdido no Cubo.':'Simular o lote? Nada é gravado; leva alguns minutos.';if(!confirm(msg))return;try{await request(`/api/hub-crm/acoes/lote?executar=${executar}`,{method:'POST'});state.batchWasRunning=true;await batchStatus()}catch(e){message(e.message,true)}}
function message(text,error=false){$('#message').textContent=text;$('#message').style.color=error?'#9b1225':'#18723d'}
async function action(url,confirmation){if(confirmation&&!confirm(confirmation))return;document.querySelectorAll('button').forEach(b=>b.disabled=true);try{const result=await request(url,{method:'POST'});message(JSON.stringify(result));await status();await table()}catch(e){message(e.message,true)}finally{document.querySelectorAll('button').forEach(b=>b.disabled=false)}}
document.querySelectorAll('.tab').forEach(b=>b.onclick=()=>{document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));b.classList.add('active');state.view=b.dataset.view;$('#search').value='';toolbar();table();if(state.view==='lote')batchStatus()});
$('#batchSimulate').onclick=()=>startBatch(false);
$('#batchRun').onclick=()=>startBatch(true);
$('#search').oninput=render;
$('#logType').onchange=table;
$('#logErrors').onchange=table;
$('#approveNow').onclick=()=>action('/api/hub-crm/acoes/aprovar-por-cte','Ler os CT-es recentes e aprovar agora as cotações correspondentes no legado?');
$('#validate').onclick=()=>action('/api/hub-crm/acoes/validar-arpa');
$('#sync').onclick=()=>action('/api/hub-crm/acoes/sincronizar');
$('#retry').onclick=()=>{const tipo=prompt('Tipo: CLIENTE ou COTACAO');if(!tipo)return;const id=prompt('ID no legado');if(!id)return;action(`/api/hub-crm/acoes/reprocessar?tipo=${encodeURIComponent(tipo)}&id=${encodeURIComponent(id)}`)};
$('#load').onclick=()=>action('/api/hub-crm/acoes/carga-inicial','O piloto cria até 10 organizações, pessoas e cards reais no ArpaSuite. Continuar?');
$('#loadAll').onclick=()=>action('/api/hub-crm/acoes/carga-completa','O lote completo cria todos os registros pendentes no ArpaSuite. Execute somente após validar o piloto. Continuar?');
toolbar();status();table();setInterval(()=>{status();table()},30000);
