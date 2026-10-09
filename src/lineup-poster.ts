type PosterPlayer={name:string;side:string;starter:boolean;position:string;x:number;y:number};
type PosterSettings={homeName:string;awayName:string;homeFormation:string;awayFormation:string};
type MatchDetails=Record<string,unknown>;
async function loadImage(src:string){const image=new Image();image.src=src;await image.decode();return image}
export async function exportPoster(players:PosterPlayer[],settings:PosterSettings,match:MatchDetails){
 if(!players.some(p=>p.starter&&['HOME','AWAY'].includes(p.side)))throw new Error('Assign players to a team before exporting the poster.');
 const background=await loadImage(`${import.meta.env.BASE_URL}qfk-poster-template.png`);
 const canvas=document.createElement('canvas');canvas.width=1536;canvas.height=1024;
 const ctx=canvas.getContext('2d')!;ctx.drawImage(background,0,0,1536,1024);
 function text(value:string,x:number,y:number,width:number,size:number,color='#f7f8fa',align:CanvasTextAlign='center'){
  ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillStyle=color;ctx.font=`800 ${size}px "Arial Narrow", Arial, sans-serif`;
  while(ctx.measureText(value).width>width&&size>8){size--;ctx.font=`800 ${size}px "Arial Narrow", Arial, sans-serif`}
  ctx.fillText(value,x,y,width);
 }
 function rounded(x:number,y:number,w:number,h:number,color:string,r=6){ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill()}
 function shirt(x:number,y:number,size:number,color:string){
  ctx.save();ctx.translate(x-size/2,y);ctx.scale(size/48,size/48);ctx.shadowColor='#0009';ctx.shadowBlur=4;ctx.shadowOffsetY=3;
  const path=new Path2D('M13 3 L20 6 H28 L35 3 L47 14 L38 23 L34 19 V45 H14 V19 L10 23 L1 14 Z');
  const gradient=ctx.createLinearGradient(0,0,48,0);gradient.addColorStop(0,color);gradient.addColorStop(.38,color==='#f5f5f5'?'#fff':'#3b4147');gradient.addColorStop(1,color);ctx.fillStyle=gradient;ctx.fill(path);ctx.shadowBlur=0;ctx.strokeStyle=color==='#f5f5f5'?'#222':'#be9d50';ctx.lineWidth=.8;ctx.stroke(path);ctx.beginPath();ctx.moveTo(19,5);ctx.quadraticCurveTo(24,13,29,5);ctx.strokeStyle='#111';ctx.lineWidth=1.5;ctx.stroke();ctx.restore();
 }
 const start=new Date(String(match.starts_at)),end=new Date(String(match.ends_at));
 const date=start.toLocaleDateString('en-GB',{timeZone:'Asia/Qatar',day:'2-digit',month:'short',year:'numeric',weekday:'long'}).toUpperCase();
 const time=(d:Date)=>d.toLocaleTimeString('en-US',{timeZone:'Asia/Qatar',hour:'numeric',minute:'2-digit'});
 text(String(match.match_number??''),930,52,160,88,'#f6c850');
 text(date,583,121,275,18);text(`${time(start)} – ${time(end)}`,864,121,225,20);text(String(match.venue??''),1142,121,217,20);
 (['HOME','AWAY'] as const).forEach((side,index)=>{
  const left=index===0?42:792,team=players.filter(p=>p.side===side),formation=index===0?settings.homeFormation:settings.awayFormation;
  // Team names replace the original TEAM A/B lettering inside the existing header.
  rounded(left+8,163,185,42,index===0?'#08141c':'#eff1f6',0);
  const name=index===0?settings.homeName:settings.awayName;
  text(name==='Home squad'?'TEAM A':name==='Away squad'?'TEAM B':name.toUpperCase(),left+100,187,179,30,index===0?'#fff':'#07131d');
  text(formation,left+624,189,104,22,index===0?'#f4cd63':'#101820');
  const starters=team.filter(p=>p.starter),maxRow=Math.max(1,...formation.split('-').map(Number)),size=maxRow>=5?65:maxRow===4?75:88;
  starters.forEach(p=>{
   const x=left+45+Math.max(9,Math.min(91,p.x))/100*610,y=210+Math.max(5,Math.min(95,p.y))/100*520;
   shirt(x,y,size,p.position==='GOALKEEPER'?(index===0?'#008a63':'#efc83e'):index===0?'#11171c':'#f5f5f5');
   const width=maxRow>=5?105:118,labelY=y+size*.80;
   rounded(x-width/2,labelY,width,26,'#051725');text(p.name,x,labelY+13,width-8,18);
   const abbreviation=p.position==='GOALKEEPER'?'GK':p.position==='DEFENDER'?(p.x<40?'LB':p.x>60?'RB':'CB'):p.position==='MIDFIELDER'?(p.x<40?'LM':p.x>60?'RM':'CM'):p.position==='FORWARD'?'ST':'UT';
   rounded(x-28,labelY+27,56,21,'#c2d9be');text(abbreviation,x,labelY+38,50,15,'#17311e');
  });
  const bench=team.filter(p=>!p.starter),columns=bench.length>4?2:1,rows=Math.max(1,Math.ceil(bench.length/columns)),height=78/rows;
  if(!bench.length)text('—',left+340,871,280,22);
  bench.forEach((p,i)=>{const col=i%columns,row=Math.floor(i/columns),x=left+193+col*160,y=830+row*height;shirt(x+18,y,Math.min(43,height-2),index===0?'#11171c':'#f5f5f5');text(p.name,x+45,y+height/2,columns===1?240:112,Math.min(22,height*.6),'#fff','left')});
 });
 return canvas.toDataURL('image/png');
}

