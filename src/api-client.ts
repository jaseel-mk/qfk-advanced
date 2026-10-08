const API_URL = import.meta.env.VITE_API_URL || 'https://qfk-api.onrender.com/api';

export type Session = {accessToken:string;refreshToken:string;expiresIn:number;role:string;memberId:string};
export type Match = {id:string;matchNumber:number;title:string;type:string;status:string;startsAt:string;endsAt:string;venue:string;maximumPlayers:number;registrationFee:number;currency:string;confirmedPlayers:number};

function readSession():Session|null{
 try{const stored=JSON.parse(localStorage.getItem('qfk-session')??'null');return stored&&typeof stored.accessToken==='string'&&typeof stored.refreshToken==='string'&&typeof stored.role==='string'&&typeof stored.memberId==='string'?stored:null;}catch{return null;}
}
let session:Session|null=readSession();
let refreshing:Promise<Session>|null=null;
function saveSession(next:Session|null){
 session=next;
 if(next)localStorage.setItem('qfk-session',JSON.stringify(next));else localStorage.removeItem('qfk-session');
 window.dispatchEvent(new Event('qfk-session-changed'));
}
async function refreshSession(previous:Session):Promise<Session>{
 if(!refreshing){
  refreshing=request<Session>('/auth/refresh',{method:'POST',body:JSON.stringify({refreshToken:previous.refreshToken})}).then(next=>{
   if(session!==previous)throw new Error('Your session changed. Please try again.');
   saveSession(next);return next;
  }).finally(()=>{refreshing=null;});
 }
 return refreshing;
}

async function request<T>(path:string, options:RequestInit={},retry=true):Promise<T>{
 const sentSession=session;
 const headers=new Headers(options.headers);headers.set('Content-Type','application/json');if(session && !path.startsWith('/auth/'))headers.set('Authorization',`Bearer ${session.accessToken}`);
 const controller=new AbortController();
 const timer=setTimeout(()=>controller.abort(),150000);
 try{
 const response=await fetch(`${API_URL}${path}`,{...options,headers,signal:controller.signal});
 if(response.status===401 && sentSession && !path.startsWith('/auth/') && retry){
  if(session===sentSession)await refreshSession(sentSession);
  if(!session)throw new Error('Your session expired. Please log in again.');
  return request<T>(path,options,false);
 }
 if(path==='/auth/refresh' && (response.status===400||response.status===401) && session===sentSession)saveSession(null);
 if(!response.ok){const problem=await response.json().catch(()=>({message:response.status===401?'Your session expired. Please log in again.':response.status===403?'Access was denied. Please log in again.':`Request failed (${response.status})`}));throw new Error(problem.message ?? `Request failed (${response.status})`);}
 const responseText=await response.text();
 return responseText?JSON.parse(responseText) as T:undefined as T;
 }catch(error){if(controller.signal.aborted)throw new Error('The server took too long to respond. Please try again shortly.');throw error;}
 finally{clearTimeout(timer);}
}

export const api={
 async register(fullName:string,email:string,password:string){const next=await request<Session>('/auth/register',{method:'POST',body:JSON.stringify({fullName,email,password})});saveSession(next);return next;},
 async login(email:string,password:string){const next=await request<Session>('/auth/login',{method:'POST',body:JSON.stringify({email,password})});saveSession(next);return next;},
 async forgotPassword(email:string){return request<void>('/auth/forgot-password',{method:'POST',body:JSON.stringify({email})});},
 async resetPassword(token:string,password:string){return request<void>('/auth/reset-password',{method:'POST',body:JSON.stringify({token,password})});},
 logout(){saveSession(null);},
 getSession(){return session;},
 matches(){return request<Match[]>('/matches');},
 registrations(matchId:string){return request(`/matches/${matchId}/registrations`);},
 registerForMatch(matchId:string){return request(`/matches/${matchId}/registrations`,{method:'POST'});}
};
