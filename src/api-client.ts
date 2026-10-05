const API_URL = import.meta.env.VITE_API_URL || 'https://qfk-api.onrender.com/api';

export type Session = {accessToken:string;refreshToken:string;expiresIn:number;role:string;memberId:string};
export type Match = {id:string;matchNumber:number;title:string;type:string;status:string;startsAt:string;endsAt:string;venue:string;maximumPlayers:number;registrationFee:number;currency:string;confirmedPlayers:number};

let session: Session|null = JSON.parse(localStorage.getItem('qfk-session') ?? 'null');

async function request<T>(path:string, options:RequestInit={}):Promise<T>{
 const headers=new Headers(options.headers);headers.set('Content-Type','application/json');if(session)headers.set('Authorization',`Bearer ${session.accessToken}`);
 const controller=new AbortController();
 const timer=setTimeout(()=>controller.abort(),150000);
 try{
 const response=await fetch(`${API_URL}${path}`,{...options,headers,signal:controller.signal});
 if(!response.ok){const problem=await response.json().catch(()=>({message:'Request failed'}));throw new Error(problem.message ?? `Request failed (${response.status})`);}
 const responseText=await response.text();
 return responseText?JSON.parse(responseText) as T:undefined as T;
 }catch(error){if(controller.signal.aborted)throw new Error('The server took too long to respond. Please try again shortly.');throw error;}
 finally{clearTimeout(timer);}
}

export const api={
 async register(fullName:string,email:string,password:string){session=await request<Session>('/auth/register',{method:'POST',body:JSON.stringify({fullName,email,password})});localStorage.setItem('qfk-session',JSON.stringify(session));return session;},
 async login(email:string,password:string){session=await request<Session>('/auth/login',{method:'POST',body:JSON.stringify({email,password})});localStorage.setItem('qfk-session',JSON.stringify(session));return session;},
 async forgotPassword(email:string){return request<void>('/auth/forgot-password',{method:'POST',body:JSON.stringify({email})});},
 async resetPassword(token:string,password:string){return request<void>('/auth/reset-password',{method:'POST',body:JSON.stringify({token,password})});},
 logout(){session=null;localStorage.removeItem('qfk-session');},
 getSession(){return session;},
 matches(){return request<Match[]>('/matches');},
 registrations(matchId:string){return request(`/matches/${matchId}/registrations`);},
 registerForMatch(matchId:string){return request(`/matches/${matchId}/registrations`,{method:'POST'});}
};
