import {useEffect,useState,useRef,type CSSProperties,type ReactNode,type FormEvent} from 'react';

import {api,type Session} from './api-client';

import './operations.css';
import {ManagementFeatures,Notifications,exportCsv} from './management-features';

import {Menu,CalendarDays,Users,Shield,Trophy,Wallet,ChartColumn,Vote,Megaphone} from 'lucide-react';

const sectionIcons={Matches:CalendarDays,Members:Users,Teams:Shield,Tournaments:Trophy,Finance:Wallet,Statistics:ChartColumn,Polls:Vote,Announcements:Megaphone};



export const sections=['Matches','Members','Teams','Tournaments','Finance','Statistics','Polls','Announcements'] as const;

export type Section=typeof sections[number];

export type Row=Record<string,unknown>;

export type Field={key:string;label:string;type?:string;options?:[string,string][];optional?:boolean;min?:number;max?:number;value?:string;step?:string};

const text=(r:Row,k:string)=>String(r[k]??'');

const num=(r:Row,k:string)=>Number(r[k]??0);

const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Qatar'}).format(new Date());

const time=(value:string)=>value?new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Qatar',dateStyle:'medium',timeStyle:'short'}).format(new Date(value)):'—';

const localTime=(value:string)=>value?new Date(new Date(value).getTime()+3*3600000).toISOString().slice(0,16):'';

const opts=(...values:string[]):[string,string][]=>values.map(x=>[x,x.replaceAll('_',' ')]);

const choices=(rows:Row[],name:string):[string,string][]=>rows.map(r=>[text(r,'id'),text(r,name)]);



export function Form({title,fields,initial={},onSave,onCancel,busy}:{title:string;fields:Field[];initial?:Row;onSave:(data:Row)=>void;onCancel?:()=>void;busy:boolean}){

 const [error,setError]=useState('');

 function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();setError('');const data:Row={};const form=new FormData(e.currentTarget);for(const f of fields){const value=String(form.get(f.key)??'');data[f.key]=f.type==='checkbox'?form.has(f.key):f.type==='number'?Number(value):f.type==='datetime-local'?(value?new Date(value+'+03:00').toISOString():null):value||null;}try{onSave(data)}catch(e){setError(e instanceof Error?e.message:'Check your inputs.')}}

 return <form className="ops-form" onSubmit={submit}><h2>{title}</h2><div className="ops-fields">{fields.map(f=>{const raw=initial[f.key]??f.value??'';const value=f.type==='datetime-local'?localTime(String(raw)):String(raw);return <label key={f.key}>{f.label}{f.type==='checkbox'?<input name={f.key} type="checkbox" defaultChecked={Boolean(raw)}/>:f.options?<select aria-label={f.label} name={f.key} defaultValue={value} required={!f.optional}><option value="">Select…</option>{f.options.map(([id,label])=><option key={id} value={id}>{label}</option>)}</select>:f.type==='textarea'?<textarea name={f.key} defaultValue={value} maxLength={f.max??10000} required={!f.optional}/>:<input name={f.key} type={f.type??'text'} defaultValue={value} required={!f.optional} min={f.min} max={f.type==='number'?f.max:undefined} maxLength={f.type!=='number'?f.max:undefined} step={f.step??(f.type==='number'?'1':undefined)}/>}</label>})}</div>{error&&<p role="alert">{error}</p>}<div className="ops-actions"><button className="primary" disabled={busy}>{busy?'Saving…':'Save'}</button>{onCancel&&<button className="secondary" type="button" onClick={onCancel}>Cancel</button>}</div></form>;

}

export function Table({rows,columns,actions}:{rows:Row[];columns:[string,string][];actions?:(row:Row)=>ReactNode}){return !rows.length?<p className="ops-empty">No records yet.</p>:<div className="ops-table"><table><thead><tr>{columns.map(([k,label])=><th key={k}>{label}</th>)}{actions&&<th>Actions</th>}</tr></thead><tbody>{rows.map((r,i)=><tr key={text(r,'id')||text(r,'member_id')||i}>{columns.map(([k])=><td key={k}>{typeof r[k]==='boolean'?(r[k]?'Yes':'No'):text(r,k)||'—'}</td>)}{actions&&<td className="ops-row-actions">{actions(r)}</td>}</tr>)}</tbody></table></div>}



export function Operations({session,section,onSection,brand,onHome,onLogin,onCommand}:{session:Session|null;section:Section;onSection:(section:Section)=>void;brand:ReactNode;onHome:()=>void;onLogin:()=>void;onCommand?:()=>void}){

 const [search,setSearch]=useState(''),[filter,setFilter]=useState('ALL'),[pollEdit,setPollEdit]=useState<Row|null>(null);
 const [menuOpen,setMenuOpen]=useState(false);
 const headerRef=useRef<HTMLElement>(null);const [headerHeight,setHeaderHeight]=useState(78);
 useEffect(()=>{const header=headerRef.current;if(!header)return;const measure=()=>setHeaderHeight(header.getBoundingClientRect().height);measure();const observer=new ResizeObserver(measure);observer.observe(header);return()=>observer.disconnect()},[]);

 const manage=Boolean(session&&['ADMIN','SUPER_ADMIN','ORGANIZER'].includes(session.role));

 const admin=Boolean(session&&['ADMIN','SUPER_ADMIN'].includes(session.role));

 const permitted=Boolean(session&&(section!=='Members'||manage)&&(section!=='Finance'||admin));

 const [rows,setRows]=useState<Row[]>([]);const [members,setMembers]=useState<Row[]>([]);const [teams,setTeams]=useState<Row[]>([]);const [matches,setMatches]=useState<Row[]>([]);

 const [loading,setLoading]=useState(true);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('');const [refresh,setRefresh]=useState(0);

 const [edit,setEdit]=useState<Row|null>(null);const [selected,setSelected]=useState<Row|null>(null);const [detail,setDetail]=useState<Row[]>([]);const [enrolled,setEnrolled]=useState<Row[]>([]);const [standing,setStanding]=useState<Row[]>([]);const [summary,setSummary]=useState<Row>({});const [detailLoading,setDetailLoading]=useState(false);

 useEffect(()=>{setEdit(null);setSelected(null);setNotice('');setSearch('');setFilter('ALL');setPollEdit(null);},[section]);

 useEffect(()=>{

  if(!permitted){setLoading(false);return;}let active=true;setLoading(true);setError('');

  async function load(){

   const data=await api.operations<unknown>(section==='Matches'?'/matches':'/operations/'+section.toLowerCase());

   if(!active)return;if(section==='Finance'){const f=data as {entries:Row[];summary:Row};setRows(f.entries);setSummary(f.summary);}else {setRows(data as Row[]);setSelected(previous=>previous?(data as Row[]).find(row=>row.id===previous.id)??previous:null);}

   if(manage){const [m,t,x]=await Promise.all([api.operations<Row[]>('/operations/members'),api.operations<Row[]>('/operations/teams'),api.operations<Row[]>('/matches')]);if(active){setMembers(m);setTeams(t);setMatches(x);}}

  }

  load().catch(e=>{if(active)setError(e instanceof Error?e.message:'Unable to load records.');}).finally(()=>{if(active)setLoading(false)});return()=>{active=false};

 },[section,permitted,manage,refresh]);

 useEffect(()=>{

  if(!selected)return;let active=true;setDetailLoading(true);setDetail([]);setEnrolled([]);setStanding([]);setError('');

  const id=text(selected,'id');

  async function load(){if(section==='Matches'){const data=await api.operations<Row[]>(`/matches/${id}/registrations`);if(active)setDetail(data);}else if(section==='Teams'){const data=await api.operations<Row[]>(`/operations/teams/${id}/members`);if(active)setDetail(data);}else if(section==='Tournaments'){const [f,t,s]=await Promise.all([api.operations<Row[]>(`/operations/tournaments/${id}/fixtures`),api.operations<Row[]>(`/operations/tournaments/${id}/teams`),api.operations<Row[]>(`/operations/tournaments/${id}/standings`)]);if(active){setDetail(f);setEnrolled(t);setStanding(s);}}else if(section==='Statistics'){const data=await api.operations<Row[]>(`/operations/statistics/matches/${id}`);if(active)setDetail(data);}}

  load().catch(e=>{if(active)setError(e instanceof Error?e.message:'Unable to load details.')}).finally(()=>{if(active)setDetailLoading(false)});return()=>{active=false};

 },[selected,section,refresh]);

 async function save(path:string,method:string,data?:unknown){if(busy)return;setBusy(true);setError('');setNotice('');try{await api.operations(path,method,data);setNotice('Saved successfully.');setEdit(null);setRefresh(x=>x+1);}catch(e){setError(e instanceof Error?e.message:'Unable to save.');}finally{setBusy(false)}}

 const mutation=(path:string,method='POST',data?:unknown)=>void save(path,method,data);

 const matchFields:Field[]=[{key:'matchNumber',label:'Match number',type:'number',min:1},{key:'title',label:'Title',max:180},{key:'type',label:'Type',options:opts('COMMUNITY_MATCH','FRIENDLY','TOURNAMENT','PRACTICE','INTERNAL','EXTERNAL')},{key:'status',label:'Status',options:opts('DRAFT','REGISTRATION_OPEN','REGISTRATION_CLOSED','FULL','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED')},{key:'startsAt',label:'Start (Qatar time)',type:'datetime-local'},{key:'endsAt',label:'End (Qatar time)',type:'datetime-local'},{key:'venue',label:'Venue',max:180},{key:'maximumPlayers',label:'Capacity',type:'number',min:2,value:'16'},{key:'registrationFee',label:'Fee (QAR)',type:'number',min:0,step:'0.01',value:'0'}];

 const memberFields:Field[]=[{key:'fullName',label:'Full name',max:160},{key:'nickname',label:'Nickname',optional:true,max:80},{key:'mobile',label:'Mobile',optional:true,max:40},{key:'location',label:'Location',optional:true,max:120},{key:'joinedOn',label:'Joined on',type:'date',value:today()},{key:'active',label:'Active',type:'checkbox'}];

 const teamFields:Field[]=[{key:'name',label:'Team name',max:160},{key:'shortName',label:'Short name',max:30},{key:'description',label:'Description',type:'textarea',optional:true,max:3000},{key:'foundedOn',label:'Founded on',type:'date',value:today()},{key:'active',label:'Active',type:'checkbox'}];

 const tournamentFields:Field[]=[{key:'name',label:'Tournament name',max:180},{key:'startsOn',label:'Start date',type:'date'},{key:'endsOn',label:'End date',type:'date'},{key:'venue',label:'Venue',max:180},{key:'status',label:'Status',options:opts('DRAFT','OPEN','IN_PROGRESS','COMPLETED','CANCELLED')},{key:'description',label:'Description',type:'textarea',optional:true,max:5000}];

 const announcementFields:Field[]=[{key:'title',label:'Title',max:180},{key:'body',label:'Announcement',type:'textarea'},{key:'priority',label:'Priority',options:opts('NORMAL','IMPORTANT')},{key:'status',label:'Status',options:opts('DRAFT','PUBLISHED','ARCHIVED')},{key:'publishAt',label:'Publish at (Qatar time, optional)',type:'datetime-local',optional:true},{key:'audienceTeamId',label:'Audience team (optional: all members)',options:choices(teams,'name'),optional:true}];

 const normalized=(r:Row)=>({...r,fullName:r.full_name,shortName:r.short_name,joinedOn:r.joined_on,foundedOn:r.founded_on,startsOn:r.starts_on,endsOn:r.ends_on,publishAt:r.publish_at,audienceTeamId:r.audience_team_id});

 const pollFields:Field[]=[{key:'question',label:'Question',max:300},{key:'status',label:'Poll state',options:opts('DRAFT','OPEN'),value:'OPEN'},{key:'opensAt',label:'Opens (Qatar time)',type:'datetime-local',optional:true},{key:'closesAt',label:'Closes (Qatar time)',type:'datetime-local'},{key:'eligibleTeamId',label:'Eligible team (optional: all active members)',options:choices(teams,'name'),optional:true},{key:'options',label:'Choices (one per line, 2–8)',type:'textarea',max:1300}];
 const filteredRows=rows.filter(r=>Object.values(r).some(v=>String(v??'').toLowerCase().includes(search.toLowerCase()))&&(filter==='ALL'||String(r.status??r.active??r.kind)===filter));
 const filterOptions=Array.from(new Set(rows.map(r=>String(r.status??r.active??r.kind??'')).filter(Boolean)));
 const editFields=section==='Matches'?matchFields:section==='Members'?memberFields:section==='Teams'?teamFields:section==='Tournaments'?tournamentFields:announcementFields;

 const editAction=(r:Row)=><button className="secondary" disabled={busy} onClick={()=>setEdit(normalized(r))}>Edit</button>;

 const detailAction=(r:Row)=><button className="secondary" onClick={()=>setSelected(r)}>Open</button>;

 const columns:Record<Section,[string,string][]>= {

  Matches:[['matchNumber','#'],['title','Match'],['status','Status'],['venue','Venue'],['confirmedPlayers','Confirmed'],['maximumPlayers','Capacity']],

  Members:[['full_name','Member'],['email','Account email'],['mobile','Mobile'],['location','Location'],['active','Active'],['account_role','Role']],

  Teams:[['name','Team'],['short_name','Short name'],['member_count','Members'],['active','Active']],

  Tournaments:[['name','Tournament'],['starts_on','Starts'],['ends_on','Ends'],['venue','Venue'],['status','Status'],['team_count','Teams']],

  Finance:[['occurred_on','Date'],['kind','Type'],['amount','QAR'],['category','Category'],['description','Description'],['member_name','Member'],['match_title','Match'],['voided_at','Voided']],

  Statistics:[['full_name','Player'],['appearances','Appearances'],['goals','Goals'],['assists','Assists'],['attendance_percent','Attendance %']],

  Polls:[],Announcements:[]

 };

 return <div className="portal-page ops-portal" style={{'--ops-header-height':`${headerHeight}px`} as CSSProperties}><header ref={headerRef} className="portal-nav">{brand}<div><Notifications/>{manage&&onCommand&&<button className="secondary" onClick={onCommand}>Command Center</button>}<button className="secondary" onClick={onHome}>Home</button></div></header><main className="ops-main"><aside className={'ops-sidebar'+(menuOpen?' is-open':'')} onPointerEnter={e=>{if(e.pointerType==='mouse')setMenuOpen(true)}} onPointerLeave={e=>{if(e.pointerType==='mouse')setMenuOpen(false)}} onFocus={e=>{if(e.target.matches(':focus-visible'))setMenuOpen(true)}} onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget))setMenuOpen(false)}} onKeyDown={e=>{if(e.key==='Escape')setMenuOpen(false)}}><button className="ops-menu-toggle" aria-label={menuOpen?'Collapse menu':'Expand menu'} aria-expanded={menuOpen} aria-controls="ops-section-menu" onClick={()=>setMenuOpen(x=>!x)}><Menu size={21}/><span>QFK menu</span></button><nav id="ops-section-menu" className="ops-nav" aria-label="QFK sections">{sections.filter(s=>s!=='Members'||manage).filter(s=>s!=='Finance'||admin).map(s=>{const Icon=sectionIcons[s];return <button key={s} aria-label={s} aria-current={s===section?'page':undefined} title={menuOpen?undefined:s} className={s===section?'active':''} onClick={()=>{setRows([]);setSelected(null);setEdit(null);setNotice('');setLoading(true);onSection(s);if(window.matchMedia('(hover: none)').matches)setMenuOpen(false)}}><Icon size={21} aria-hidden="true"/><span>{s}</span></button>})}</nav></aside><div className="ops-heading"><div><small>QFK COMMUNITY</small><h1>{section}</h1><p>{section==='Finance'?'Manually recorded income and expenses in QAR.':section==='Statistics'?'Goals, assists and attendance from saved match records.':'Manage your community records in one place.'}</p></div>{permitted&&<button className="secondary" disabled={loading||busy} onClick={()=>setRefresh(x=>x+1)}>Refresh</button>}</div>

 {!session?<section className="ops-empty"><h2>Member login required</h2><button className="primary" onClick={onLogin}>Log in</button></section>:!permitted?<section className="ops-empty"><h2>Admin access required</h2><p>This section is restricted to authorized accounts.</p></section>:<>

 {error&&<div className="form-error" role="alert">{error}<button className="secondary" onClick={()=>setRefresh(x=>x+1)}>Retry</button></div>}{notice&&<div className="status-message success" role="status">{notice}</div>}

 {loading?<div className="loading-card">Loading records… The free service may need a minute to wake up.</div>:<>

 {manage&&['Matches','Members','Teams','Tournaments','Announcements'].includes(section)&&!edit&&<button className="primary ops-create" onClick={()=>setEdit({active:true,status:section==='Announcements'?'DRAFT':section==='Matches'?'REGISTRATION_OPEN':'DRAFT',priority:'NORMAL',type:'COMMUNITY_MATCH'})}>Create {section==='Members'?'member':section==='Teams'?'team':section==='Matches'?'match':section==='Tournaments'?'tournament':'announcement'}</button>}

 <div className="ops-filters"><label>Search records<input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Name, date, venue or category…"/></label><label>Filter<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All</option>{filterOptions.map(v=><option key={v} value={v}>{v==='true'?'Active':v==='false'?'Inactive':v}</option>)}</select></label>{!['Polls','Announcements'].includes(section)&&<button className="secondary" disabled={!filteredRows.length} onClick={()=>exportCsv('qfk-'+section.toLowerCase(),filteredRows)}>Export CSV</button>}</div>
 {edit&&<Form key={section+text(edit,'id')} title={edit.id?'Edit record':'Create record'} fields={editFields} initial={edit} busy={busy} onCancel={()=>setEdit(null)} onSave={data=>mutation(section==='Matches'?'/matches'+(edit.id?'/'+text(edit,'id'):''):'/operations/'+section.toLowerCase()+(edit.id?'/'+text(edit,'id'):''),edit.id?'PUT':'POST',{...data,...(['Teams','Tournaments'].includes(section)?{description:data.description??''}:{})})}/>}

 {section==='Finance'&&<><div className="ops-metrics">{[['income','Income'],['expenses','Expenses'],['balance','Balance']].map(([k,label])=><article key={k}><span>{label}</span><strong>{num(summary,k).toFixed(2)} QAR</strong></article>)}</div><Form title="Record income or expense" busy={busy} fields={[{key:'kind',label:'Type',options:opts('INCOME','EXPENSE')},{key:'amount',label:'Amount (QAR)',type:'number',min:0.01,step:'0.01'},{key:'category',label:'Category',max:80},{key:'description',label:'Description',max:500},{key:'occurredOn',label:'Date',type:'date',value:today()},{key:'memberId',label:'Member (optional)',options:choices(members,'full_name'),optional:true},{key:'matchId',label:'Match (optional)',options:choices(matches,'title'),optional:true}]} onSave={data=>mutation('/operations/finance','POST',data)}/><p className="ops-note">Marking a match fee as paid does not add income here. Record received money once to avoid double counting.</p></>}

 {section==='Polls'?<>{manage&&<Form title="Create a poll" busy={busy} fields={pollFields} onSave={data=>mutation('/operations/polls','POST',{...data,options:String(data.options).split('\n').map(s=>s.trim()).filter(Boolean)})}/>} {!rows.length&&<p className="ops-empty">No polls yet.</p>}{filteredRows.map(p=><article className="ops-card" key={text(p,'id')}><h2>{text(p,'question')}</h2><p>{p.closed?'Closed':p.status==='DRAFT'?'Draft':new Date(String(p.opens_at)).getTime()>Date.now()?'Scheduled':p.finished?'Closed':'Open'} · closes {time(text(p,'closes_at'))}</p>{((p.options??[]) as Row[]).map(o=><button key={text(o,'id')} className={'ops-vote '+(o.selected?'selected':'')} disabled={busy||Boolean(p.finished)} onClick={()=>mutation(`/operations/polls/${text(p,'id')}/vote`,'POST',{optionId:o.id})}>{text(o,'label')} {o.selected?'✓':''}<span>{num(o,'votes')} votes</span></button>)}{manage&&p.status==='DRAFT'&&<button className="secondary" onClick={()=>setPollEdit(p)}>Edit draft</button>}{pollEdit?.id===p.id&&<Form key={String(p.id)} title="Edit poll draft" busy={busy} fields={pollFields} initial={{question:p.question,status:p.status,opensAt:p.opens_at,closesAt:p.closes_at,eligibleTeamId:p.eligible_team_id,options:((p.options??[]) as Row[]).map(o=>o.label).join('\n')}} onCancel={()=>setPollEdit(null)} onSave={data=>{mutation(`/operations/polls/${p.id}`,'PUT',{...data,options:String(data.options).split('\n').map(x=>x.trim()).filter(Boolean)});setPollEdit(null)}}/>}{manage&&!p.finished&&<button className="secondary" disabled={busy} onClick={()=>mutation(`/operations/polls/${text(p,'id')}/close`)}>Close poll</button>}</article>)}</>:section==='Announcements'?<>{!rows.length&&<p className="ops-empty">No announcements yet.</p>}{filteredRows.map(a=><article className="ops-card" key={text(a,'id')}><small>{text(a,'priority')} · {text(a,'status')}</small><h2>{text(a,'title')}</h2><p className="ops-body">{text(a,'body')}</p><small>{time(text(a,'created_at'))}</small>{manage&&editAction(a)}</article>)}</>:<Table rows={filteredRows} columns={columns[section]} actions={r=>section==='Finance'?!r.voided_at?<button className="secondary" onClick={()=>setSelected(r)}>Correct / void</button>:<span>{text(r,'void_reason')}</span>:section==='Statistics'?null:<>{['Matches','Members','Teams','Tournaments'].includes(section)&&detailAction(r)}{manage&&editAction(r)}</>}/>}

 {section==='Statistics'&&manage&&<section className="ops-card"><h2>Record match statistics</h2><label className="ops-select">Match<select value={selected?text(selected,'id'):''} onChange={e=>setSelected(matches.find(m=>text(m,'id')===e.target.value)??null)}><option value="">Select match…</option>{matches.map(m=><option key={text(m,'id')} value={text(m,'id')}>{text(m,'title')}</option>)}</select></label></section>}

 {selected&&<section className="ops-detail"><div className="ops-heading"><h2>{text(selected,'title')||text(selected,'name')||text(selected,'full_name')||'Record details'}</h2><button className="secondary" onClick={()=>setSelected(null)}>Close details</button></div>{detailLoading?<p>Loading details…</p>:<>

 {section==='Matches'&&<><p>{time(text(selected,'startsAt'))} – {time(text(selected,'endsAt'))} · Qatar time</p>{manage&&<Form title="Add member to match" busy={busy} fields={[{key:'memberId',label:'Member',options:choices(members.filter(m=>m.active),'full_name')}]} onSave={data=>mutation(`/matches/${text(selected,'id')}/members`,'POST',data)}/>}<Table rows={detail} columns={[['memberName','Player'],['guest','Guest'],...(manage?[['guestMobile','Guest mobile'] as [string,string]]:[]),['status','Registration'],['waitlistPosition','Waitlist'],['paymentStatus','Fee status'],['attendanceStatus','Attendance']]} actions={r=><>{manage&&<><label>Fee<select aria-label={'Fee status for '+text(r,'memberName')} disabled={busy} value={text(r,'paymentStatus')} onChange={e=>mutation(`/matches/registrations/${text(r,'id')}/payment`,'PATCH',{status:e.target.value})}>{opts('PENDING','PAID','WAIVED','REFUNDED').map(([v,label])=><option key={v}>{label}</option>)}</select></label><label>Attendance<select aria-label={'Attendance for '+text(r,'memberName')} disabled={busy} value={text(r,'attendanceStatus')} onChange={e=>mutation(`/matches/registrations/${text(r,'id')}/attendance`,'PATCH',{status:e.target.value})}>{opts('NOT_MARKED','PRESENT','ABSENT','EXCUSED').map(([v,label])=><option key={v} value={v}>{label}</option>)}</select></label></>}{['CONFIRMED','WAITLIST'].includes(text(r,'status'))&&(manage||r.memberId===session.memberId)&&<button className="secondary" disabled={busy} onClick={()=>mutation(`/matches/registrations/${text(r,'id')}`,'DELETE')}>Cancel registration</button>}</>}/>{!manage&&<button className="primary" disabled={busy} onClick={()=>mutation(`/matches/${text(selected,'id')}/registrations`)}>Register / join waitlist</button>}</>}

 {section==='Teams'&&<>{manage&&<Form title="Add team member" busy={busy} fields={[{key:'memberId',label:'Member',options:choices(members.filter(m=>m.active),'full_name')},{key:'role',label:'Team role',options:opts('PLAYER','CAPTAIN','COACH')}]} onSave={data=>mutation(`/operations/teams/${text(selected,'id')}/members`,'POST',data)}/>}<Table rows={detail} columns={[['full_name','Member'],['role','Team role']]} actions={manage?r=><button className="secondary" disabled={busy} onClick={()=>mutation(`/operations/teams/${text(selected,'id')}/members/${text(r,'id')}`,'DELETE')}>Remove from team</button>:undefined}/></>}

 {section==='Tournaments'&&<>{manage&&<><Form title="Enroll team" busy={busy} fields={[{key:'teamId',label:'Team',options:choices(teams.filter(t=>t.active),'name')}]} onSave={data=>mutation(`/operations/tournaments/${text(selected,'id')}/teams`,'POST',data)}/><Form title="Schedule fixture" busy={busy} fields={[{key:'homeTeamId',label:'Home team',options:choices(enrolled,'name')},{key:'awayTeamId',label:'Away team',options:choices(enrolled,'name')},{key:'startsAt',label:'Kickoff (Qatar time)',type:'datetime-local'}]} onSave={data=>mutation(`/operations/tournaments/${text(selected,'id')}/fixtures`,'POST',data)}/></>}<h3>Enrolled teams</h3><Table rows={enrolled} columns={[['name','Team'],['short_name','Short name']]}/><h3>Fixtures and results</h3>{!detail.length&&<p>No fixtures yet.</p>}{detail.map(f=><article className="ops-card" key={text(f,'id')}><h3>{text(f,'home_name')} vs {text(f,'away_name')}</h3><p>{time(text(f,'starts_at'))} · {f.home_score==null?'Not played':`${f.home_score} – ${f.away_score}`}</p>{manage&&<Form key={text(f,'id')+text(f,'home_score')+text(f,'away_score')} title="Save result" busy={busy} fields={[{key:'homeScore',label:'Home goals',type:'number',min:0},{key:'awayScore',label:'Away goals',type:'number',min:0}]} initial={{homeScore:f.home_score,awayScore:f.away_score}} onSave={data=>mutation(`/operations/fixtures/${text(f,'id')}/score`,'PUT',data)}/>}</article>)}<h3>Standings</h3><Table rows={standing} columns={[['name','Team'],['played','Played'],['won','Won'],['drawn','Drawn'],['lost','Lost'],['goals_for','GF'],['goals_against','GA'],['goal_difference','GD'],['points','Points']]}/></>}

 {section==='Finance'&&<Form title="Void incorrect entry (history is retained)" busy={busy} fields={[{key:'reason',label:'Reason',max:500}]} onSave={data=>mutation(`/operations/finance/${text(selected,'id')}/void`,'POST',data)}/>}

 {section==='Statistics'&&detail.map(r=><Form key={text(r,'member_id')+text(r,'goals')+text(r,'assists')} title={text(r,'full_name')} busy={busy} fields={[{key:'goals',label:'Goals',type:'number',min:0},{key:'assists',label:'Assists',type:'number',min:0}]} initial={r} onSave={data=>mutation('/operations/statistics','PUT',{...data,matchId:selected.id,memberId:r.member_id})}/>)}

 </>}</section>}

 </>}

 </>}

 <ManagementFeatures section={section} selected={selected} rows={filteredRows} members={members} manage={manage} admin={admin} refresh={refresh} onSaved={()=>setRefresh(x=>x+1)}/>
 </main></div>;

}

