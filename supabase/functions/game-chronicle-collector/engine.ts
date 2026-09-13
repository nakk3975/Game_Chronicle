export type Kind = 'PLAYING' | 'NO_GAME_CANDIDATE' | 'UNOBSERVABLE' | 'FETCH_FAILED';
export type Segment = {start:string;end:string};
export type Play = {id:string;gameId:string;name:string;lifecycle:string;quality:string;closeReason:string|null;firstSeen:string;lastSeen:string;startedAt:string;endedAt:string|null;startLower:string|null;startUpper:string;endLower:string|null;endUpper:string|null;segments:Segment[];excluded:boolean;version:number};
export type State = {status:string;lastAttempt:string|null;lastSuccess:string|null;lastPlaying:string|null;firstNoGame:string|null;gapStart:string|null;failures:number;noGameCount:number;endBoundaryKnown:boolean;active:Play|null};
export type Observation = {at:string;kind:Kind;gameId?:string;name?:string};
export type Gap = {start:string;end:string;reason:string};
export function initial():State {return {status:'IDLE',lastAttempt:null,lastSuccess:null,lastPlaying:null,firstNoGame:null,gapStart:null,failures:0,noGameCount:0,endBoundaryKnown:false,active:null};}
const elapsed=(a:string,b:string)=>Math.floor((Date.parse(b)-Date.parse(a))/1000);
const midpoint=(a:string,b:string)=>new Date(Date.parse(a)+Math.floor(elapsed(a,b)/2)*1000).toISOString();
export const seconds=(p:Play)=>p.segments.reduce((n,s)=>n+elapsed(s.start,s.end),0);
export function apply(input:Partial<State>,o:Observation) {
 const s:State={...initial(),...structuredClone(input)};const changed:Play[]=[];const gaps:Gap[]=[];
 function close(reason:string,end:string,upper:string|null){
  const p=s.active;if(!p)return;
  p.endedAt=end;p.endLower=p.lastSeen;p.endUpper=upper;p.closeReason=reason;p.lifecycle=upper===null?'INTERRUPTED':'FINALIZED';
  if(upper===null)p.quality='PARTIAL';
  if(Date.parse(end)>Date.parse(p.lastSeen)&&p.segments.length)p.segments[p.segments.length-1].end=end;
  p.version++;changed.push(p);s.active=null;s.firstNoGame=null;s.noGameCount=0;
 }
 if(s.lastAttempt&&Date.parse(o.at)<=Date.parse(s.lastAttempt))return {state:s,changed,gaps};
 if(s.lastAttempt&&elapsed(s.lastAttempt,o.at)>180){s.gapStart??=s.lastSuccess??s.lastAttempt;close('STALE',s.lastPlaying!,null);}
 s.lastAttempt=o.at;
 if(o.kind==='FETCH_FAILED'||o.kind==='UNOBSERVABLE'){
  s.failures++;s.noGameCount=0;s.firstNoGame=null;s.gapStart??=s.lastSuccess??o.at;
  if(s.active)s.active.quality='PARTIAL';
  if(s.failures>=3||(s.lastSuccess&&elapsed(s.lastSuccess,o.at)>180))close('UNOBSERVABLE',s.lastPlaying!,null);
  s.status='UNOBSERVABLE';return {state:s,changed,gaps};
 }
 const hadGap=s.gapStart!==null;
 if(hadGap){gaps.push({start:s.gapStart!,end:o.at,reason:'OBSERVATION_GAP'});s.gapStart=null;}
 s.failures=0;
 if(o.kind==='PLAYING'){
  if(s.active&&s.active.gameId!==o.gameId){const boundary=s.firstNoGame??o.at;const known=!hadGap&&(!s.firstNoGame||s.endBoundaryKnown);close('GAME_CHANGED',known?midpoint(s.lastPlaying!,boundary):s.lastPlaying!,known?boundary:null);}
  if(!s.active){const lower=!hadGap?s.lastSuccess:null;const start=lower?midpoint(lower,o.at):o.at;
   s.active={id:crypto.randomUUID(),gameId:o.gameId!,name:o.name!,lifecycle:'ACTIVE',quality:lower?'ESTIMATED':'PARTIAL',closeReason:null,firstSeen:o.at,lastSeen:o.at,startedAt:start,endedAt:null,startLower:lower,startUpper:o.at,endLower:null,endUpper:null,segments:[{start,end:o.at}],excluded:false,version:0};
  }else{
   const p=s.active;if(hadGap||s.firstNoGame){if(s.firstNoGame)gaps.push({start:p.lastSeen,end:o.at,reason:'END_CANDIDATE_REVERTED'});p.segments.push({start:o.at,end:o.at});p.quality='PARTIAL';}else p.segments[p.segments.length-1].end=o.at;
   p.lastSeen=o.at;p.lifecycle='ACTIVE';
  }
  s.active.version++;changed.push(s.active);s.lastPlaying=o.at;s.firstNoGame=null;s.noGameCount=0;s.status='PLAYING';
 }else{
  if(s.active){if(!s.firstNoGame){s.firstNoGame=o.at;s.endBoundaryKnown=!hadGap;s.noGameCount=1;s.active.lifecycle='PENDING_END';changed.push(s.active);}else if(++s.noGameCount>=2)close('NO_GAME_CONFIRMED',s.endBoundaryKnown?midpoint(s.lastPlaying!,s.firstNoGame):s.lastPlaying!,s.endBoundaryKnown?s.firstNoGame:null);}
  s.status=s.active?'PENDING_END':'IDLE';
 }
 s.lastSuccess=o.at;return {state:s,changed,gaps};
}
export function classify(p:any,at:string):Observation {
 if(!p||typeof p!=='object'||p.communityvisibilitystate!==3)return {at,kind:'UNOBSERVABLE'};
 if(p.gameid!==undefined){const g=String(p.gameid);if(!/^[1-9][0-9]{0,9}$/.test(g)||Number(g)>4294967295)return {at,kind:'UNOBSERVABLE'};return {at,kind:'PLAYING',gameId:g,name:typeof p.gameextrainfo==='string'?p.gameextrainfo.slice(0,300):`Steam 게임 ${g}`};}
 return {at,kind:'NO_GAME_CANDIDATE'};
}
