export const shortPositions:Record<string,string>={GK:'GOALKEEPER',DF:'DEFENDER',MD:'MIDFIELDER',FD:'FORWARD'};
export function parsePlayerLine(line:string){
 const match=line.trim().match(/^(.*?)\s*(?:[-,:|]\s*|\s+|\(\s*)(GK|DF|MD|FD)\s*\)?$/i);
 return match&&match[1].trim()?{name:match[1].trim(),position:shortPositions[match[2].toUpperCase()]}:{name:line.trim(),position:'UTILITY'};
}
export type Positioned={registrationId:string;preferredPosition:string;position:string;side:string;starter:boolean;x:number;y:number};
export function formationSlots(value:string,side:string){
 const rows=[1,...value.split('-').map(Number)];
 return rows.flatMap((count,row)=>Array.from({length:count},(_,col)=>({side,starter:true,position:row===0?'GOALKEEPER':row===rows.length-1?'FORWARD':row===1?'DEFENDER':'MIDFIELDER',x:100*(col+1)/(count+1),y:88-row*74/(rows.length-1)})));
}
// Reserve every preferred-position slot before using anyone as a fallback.
export function fillSlots<T extends Positioned>(players:T[],slots:ReturnType<typeof formationSlots>):{assigned:T[];remaining:T[]}{
 const pool=[...players],filled=new Map<number,T>();
 slots.forEach((slot,i)=>{const index=pool.findIndex(p=>p.preferredPosition===slot.position);if(index>=0)filled.set(i,{...pool.splice(index,1)[0],...slot})});
 slots.forEach((slot,i)=>{if(!filled.has(i)&&pool.length)filled.set(i,{...pool.shift()!,...slot})});
 return {assigned:[...filled.values()],remaining:pool};
}
export function balancePlayers<T extends Positioned>(players:T[],home:string,away:string):T[]{
 const a=formationSlots(home,'HOME'),b=formationSlots(away,'AWAY');
 const slots=Array.from({length:Math.max(a.length,b.length)},(_,i)=>[a[i],b[i]]).flat().filter(Boolean);
 const {assigned,remaining}=fillSlots(players,slots);
 const counts={HOME:assigned.filter(p=>p.side==='HOME').length,AWAY:assigned.filter(p=>p.side==='AWAY').length};
 const bench=remaining.map(p=>{const side=counts.HOME<=counts.AWAY?'HOME':'AWAY';counts[side]++;return {...p,side,starter:false}});
 const byId=new Map([...assigned,...bench].map(p=>[p.registrationId,p]));return players.map(p=>byId.get(p.registrationId)!);
}
