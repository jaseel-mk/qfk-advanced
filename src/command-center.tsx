import {useEffect,useState,type ReactNode} from 'react';
import {CalendarDays,MapPin,Users,Shield,RefreshCw,ChevronRight} from 'lucide-react';
import {api,type Match,type Session} from './api-client';
import './command-center.css';

type Registration={id:string;status:'CONFIRMED'|'WAITLIST'|'CANCELLED'|'REMOVED'};
export const canManageMatches=(session:Session|null)=>Boolean(session&&['ORGANIZER','ADMIN','SUPER_ADMIN'].includes(session.role));
const label=(value:string)=>value.toLowerCase().replaceAll('_',' ');
const date=(value:string)=>new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Qatar',dateStyle:'medium',timeStyle:'short'}).format(new Date(value));
export function CommandCenter({session,brand,onBack}:{session:Session|null;brand:ReactNode;onBack:()=>void}){
 const allowed=canManageMatches(session);
 const [matches,setMatches]=useState<Match[]>([]);
 const [selectedId,setSelectedId]=useState('');
 const [registrations,setRegistrations]=useState<Registration[]>([]);
 const [loading,setLoading]=useState(true);
 const [detailLoading,setDetailLoading]=useState(true);
 const [error,setError]=useState('');
 const [detailError,setDetailError]=useState('');
 const [refresh,setRefresh]=useState(0);
 const [updated,setUpdated]=useState('');
 useEffect(()=>{
  if(!allowed)return;
  let active=true;setLoading(true);setError('');
  api.matches().then(rows=>{
   if(!active)return;
   setMatches(rows);
   setSelectedId(previous=>rows.some(m=>m.id===previous)?previous:
    (rows.find(m=>m.status!=='CANCELLED'&&m.status!=='COMPLETED'&&new Date(m.endsAt).getTime()>Date.now())??rows[rows.length-1])?.id??'');
  }).catch(e=>{if(active)setError(e instanceof Error?e.message:'Matches could not be loaded.');})
   .finally(()=>{if(active)setLoading(false);});
  return()=>{active=false;};
 },[allowed,refresh]);
 useEffect(()=>{
  if(!allowed||!selectedId||loading||error)return;
  let active=true;setDetailLoading(true);setDetailError('');setRegistrations([]);setUpdated('');
  api.registrations(selectedId).then(rows=>{if(active){setRegistrations(rows as Registration[]);setUpdated(new Date().toISOString());}})
   .catch(e=>{if(active)setDetailError(e instanceof Error?e.message:'Registration counts could not be loaded.');})
   .finally(()=>{if(active)setDetailLoading(false);});
  return()=>{active=false;};
 },[allowed,selectedId,loading,error,refresh]);
 const match=matches.find(m=>m.id===selectedId);
 const countsReady=!loading&&!detailLoading&&!detailError;
 const confirmed=registrations.filter(r=>r.status==='CONFIRMED').length;
 const waitlist=registrations.filter(r=>r.status==='WAITLIST').length;
 const available=Math.max(0,(match?.maximumPlayers??0)-confirmed);
 const changeMatch=(id:string)=>{setDetailLoading(true);setRegistrations([]);setDetailError('');setUpdated('');setSelectedId(id);};
 return <div className="portal-page"><header className="portal-nav">{brand}<button className="secondary" onClick={onBack}>Back to matches</button></header>
  <main className="cc-main"><div className="cc-heading"><div><small>MATCH MANAGEMENT</small><h1>Command Center</h1><p>Select a match to see its latest availability.</p></div>{allowed&&<button className="secondary" onClick={()=>setRefresh(x=>x+1)} disabled={loading||Boolean(selectedId)&&detailLoading}><RefreshCw/> Refresh</button>}</div>
  {!allowed?<section className="empty-state"><Shield/><h2>Organizer access required</h2><p>Log in with an organizer or admin account to manage matches.</p></section>:
   error?<section className="cc-error" role="alert"><h2>Matches could not be loaded</h2><p>{error}</p><button className="primary" onClick={()=>setRefresh(x=>x+1)}>Try again</button></section>:
   loading?<div className="loading-card" role="status">Loading matches… The first request may take longer while the service wakes up.</div>:
   !matches.length?<section className="empty-state"><CalendarDays/><h2>No matches yet</h2><p>Published and draft matches will appear here. Match creation will be added in the next stage.</p></section>:
   <><label className="cc-selector">Select match<select value={selectedId} onChange={e=>changeMatch(e.target.value)}>{matches.map(m=><option key={m.id} value={m.id}>#{m.matchNumber} · {m.title} · {date(m.startsAt)}</option>)}</select></label>
   {match&&<><section className="cc-match"><div className="cc-breadcrumb">Match #{match.matchNumber}<ChevronRight/> Overview</div><span className="cc-status">{label(match.status)}</span><h2>{match.title}</h2><div className="cc-meta"><span><CalendarDays/> {date(match.startsAt)} – {date(match.endsAt)} · Qatar time</span><span><MapPin/> {match.venue}</span></div></section>
    {detailError&&<div className="cc-error" role="alert"><p>{detailError}</p><button className="secondary" onClick={()=>setRefresh(x=>x+1)}>Retry counts</button></div>}
    <section className="cc-metrics" aria-label="Registration overview" aria-busy={detailLoading}>
     {[['Confirmed players',countsReady?`${confirmed} / ${match.maximumPlayers}`:'—'],['Available places',countsReady?String(available):'—'],['Waitlist',countsReady?String(waitlist):'—'],['Registration fee',`${match.registrationFee} ${match.currency}`]].map(([name,value])=><article key={name}><span>{name}</span><strong>{value}</strong></article>)}
    </section>
    <section className="cc-summary"><div><Users/><h2>Registration overview</h2></div>{detailLoading?<p role="status">Loading registration counts…</p>:detailError?<p>Counts are unavailable until the service responds.</p>:<><div className="cc-capacity"><span>Confirmed capacity</span><b>{confirmed} of {match.maximumPlayers}</b></div><progress max={Math.max(1,match.maximumPlayers)} value={confirmed} aria-label="Confirmed player capacity"/><p>{available} unfilled {available===1?'place':'places'} · {waitlist} on the waitlist</p><small>Updated {updated?date(updated):'now'}. Use Refresh to check for changes.</small></>}</section>
    <p className="cc-next">Next stage: player registration and waitlist management.</p>
   </>}
   </>}
  </main></div>;
}
