(self.webpackChunk_N_E=self.webpackChunk_N_E||[]).push([[931],{8964:function(e,t,s){Promise.resolve().then(s.bind(s,7243))},7243:function(e,t,s){"use strict";s.r(t),s.d(t,{default:function(){return c}});var i=s(7437),l=s(2265),n=function(e){let{index:t,ip:s,messages:l,onDisconnectClick:n}=e;return(0,i.jsxs)("div",{className:"border border-gray-900 rounded-md pt-[20px] text-left p-[24px] shadow-2xl mt-[30px]",children:[(0,i.jsxs)("div",{className:"flex flex-row items-center mb-[2px]",children:[(0,i.jsxs)("h6",{className:"uppercase font-bold text-xl text-white",children:["Client #",t,"\xa0"]}),(0,i.jsx)("div",{className:"bg-[#4cae50] text-white inline-block  p-[5px] px-[10px] rounded-[15px] text-[12px] font-bold ml-[12px]",children:"Connected"})]}),(0,i.jsxs)("div",{className:"flex flex-row items-center mb-[12px]",children:[(0,i.jsx)("div",{className:"mt-[8px] text-left text-[14px] text-gray-500",children:s}),(0,i.jsxs)("div",{className:"flex-1 text-right text-gray-500 text-[11px]",children:[l," requests/min"]})]}),(0,i.jsx)("div",{className:"bg-black w-full h-0.5 my-5 rounded-2xl"}),(0,i.jsx)("button",{onClick:()=>n(),className:"flex w-full items-center px-4 py-2 font-medium tracking-wide text-white capitalize transition-colors duration-300 transform bg-rose-600 rounded-lg hover:bg-rose-500 focus:outline-none focus:ring focus:ring-rose-300 focus:ring-opacity-80",children:(0,i.jsx)("span",{className:"mx-1 justify-center flex-1",children:"Disconnect"})})]})},a=s(4365),o=s.n(a),r=e=>{let{children:t}=e,[s,n]=(0,l.useState)(!1),[a,r]=(0,l.useState)(!1);return(0,l.useEffect)(()=>{let e=()=>{setTimeout(()=>n(!0),800)};return"complete"===document.readyState?e():(window.addEventListener("load",e),document.addEventListener("DOMContentLoaded",e)),()=>{window.removeEventListener("load",e),document.removeEventListener("DOMContentLoaded",e)}},[]),(0,i.jsxs)(i.Fragment,{children:[(0,i.jsxs)("div",{style:{position:"fixed",top:0,left:0,right:0,bottom:0,display:"flex",flexDirection:"column",justifyContent:"center",alignItems:"center",opacity:s&&a?0:1,visibility:s&&a?"hidden":"visible",transition:"opacity 1s ease, visibility 1s ease"},className:"bg-gradient-to-r from-slate-900 to-slate-700",children:[(0,i.jsx)("img",{src:"/logo.png",alt:"Logo",className:"xl:w-[30rem] lg:w-[25rem] sm:w-[20rem] w-[15rem]",style:{marginBottom:"16px"}}),(0,i.jsx)("div",{className:"font-extrabold text-xl text-white",style:{textAlign:"center"},children:(0,i.jsx)(o(),{onInit:e=>{e.changeDelay(3).typeString("XTABLES DASHBOARD").pauseFor(300).callFunction(()=>{r(!0)}).start()}})})]}),(0,i.jsx)("div",{style:{opacity:s&&a?1:0,transition:"opacity 1s ease"},children:t})]})};s(7449);var c=function(){var e,t,s,a,o,c,d;let x={status:"OFFLINE",ip:"Disconnected",totalMessages:0,clientDataList:[]},m=async()=>{await fetch("/api/reboot",{method:"POST",headers:{"Content-Type":"application/json"}})},u=async e=>{await fetch("/api/disconnect?uuid="+e,{method:"POST",headers:{"Content-Type":"application/json"}})},[f,g]=(0,l.useState)(x),[h,v]=(0,l.useState)(0),[p,w]=(0,l.useState)(0),[N,j]=(0,l.useState)({diff:0,prevTime:Date.now()}),[b,y]=(0,l.useState)(0),[S,E]=(0,l.useState)(0);return((0,l.useEffect)(()=>{let e=setInterval(async()=>{try{let e=await fetch("/api/get"),t=await e.json();g(t),j(e=>{let t=Date.now();return{diff:t-e.prevTime,prevTime:t}})}catch(e){g(x),v(0),w(0),console.error("Error fetching data:",e)}},150);return()=>clearInterval(e)},[]),(0,l.useEffect)(()=>{if(N.diff>0&&void 0!==f.framesForwarded&&void 0!==f.totalMessages){let e=f.framesForwarded-b,t=f.totalMessages-S,s=e/N.diff*1e3,i=t/N.diff*1e3;v(s),w(i),y(f.framesForwarded),E(f.totalMessages)}},[f,N]),f)?(0,i.jsx)(r,{children:(0,i.jsx)("div",{className:" flex min-h-screen w-screen bg-gradient-to-r from-slate-500 to-slate-800 overflow-hidden",children:(0,i.jsxs)("div",{className:"p-6 bg-gray-800 shadow-lg shadow-black/10 lg:rounded-md max-w-7xl w-full lg:my-6 mx-auto flex flex-grow flex-col ",children:[(0,i.jsx)("div",{className:"flex justify-center",children:(0,i.jsx)("img",{src:"/logo.png",className:"xl:w-[30rem] lg:w-[25rem] sm:w-[20rem]",alt:"Logo"})}),(0,i.jsx)("div",{className:"my-6 mx-auto w-full",children:(0,i.jsxs)("div",{className:"shadow-2xl border border-gray-900 rounded-md p-6 mt-6",children:[(0,i.jsxs)("div",{className:"flex flex-row items-center mb-2",children:[(0,i.jsx)("h6",{className:"uppercase font-extrabold text-xl text-white",children:"XTABLES\xa0"}),(0,i.jsx)("div",{className:"text-white inline-block p-2 px-4 rounded-full text-xs font-bold ml-3 "+((null==f?void 0:f.status)==="ONLINE"?"bg-green-500":(null==f?void 0:f.status)==="STARTING"?"bg-yellow-500":(null==f?void 0:f.status)==="REBOOTING"?"bg-amber-100":"bg-red-500"),children:(null==f?void 0:f.status)==="ONLINE"?"Fully Operational":(null==f?void 0:f.status)==="STARTING"?"Starting Service":(null==f?void 0:f.status)==="REBOOTING"?"Rebooting":"Offline"})]}),(0,i.jsxs)("div",{className:"flex flex-col md:flex-row items-center justify-between mb-3 space-y-2 md:space-y-0 md:space-x-4",children:[(0,i.jsx)("div",{className:"text-sm text-gray-500 md:text-left text-center w-full md:w-auto",children:null!==(o=null==f?void 0:f.ip)&&void 0!==o?o:"Disconnected"}),(0,i.jsxs)("div",{className:"text-xs text-gray-500 text-center w-full md:w-auto",children:[null!==(c=null==f?void 0:null===(e=f.framesForwarded)||void 0===e?void 0:e.toLocaleString())&&void 0!==c?c:0,"\xa0FIM\xa0|\xa0",60*h<0?"RESET":(60*h).toLocaleString(void 0,{minimumFractionDigits:0,maximumFractionDigits:0}),"\xa0FPM\xa0|\xa0",h<0?"RESET":h.toLocaleString(void 0,{minimumFractionDigits:0,maximumFractionDigits:0}),"\xa0FPS"]}),(0,i.jsxs)("div",{className:"text-xs text-gray-500 text-center md:text-right w-full md:w-auto",children:[null!==(d=null==f?void 0:null===(t=f.totalMessages)||void 0===t?void 0:t.toLocaleString())&&void 0!==d?d:0,"\xa0RIM\xa0|\xa0",60*p<0?"RESET":(60*p).toLocaleString(void 0,{minimumFractionDigits:0,maximumFractionDigits:0}),"\xa0RPM\xa0|\xa0",p<0?"RESET":p.toLocaleString(void 0,{minimumFractionDigits:0,maximumFractionDigits:0}),"\xa0RPS"]})]}),(0,i.jsx)("div",{className:"bg-black w-full h-0.5 my-5 rounded-2xl"}),(0,i.jsx)("button",{onClick:m,disabled:"ONLINE"!==f.status,className:"flex w-full items-center px-4 py-2 font-medium tracking-wide text-white capitalize transition-colors duration-300 transform bg-rose-600 rounded-lg hover:bg-rose-500 focus:outline-none focus:ring focus:ring-rose-300 focus:ring-opacity-80 "+("ONLINE"!==f.status?"cursor-not-allowed opacity-45":"cursor-pointer"),children:"STARTING"===f.status||"REBOOTING"===f.status?(0,i.jsxs)("span",{className:"flex items-center mx-1 justify-center flex-1",children:[(0,i.jsxs)("svg",{className:"animate-spin h-5 w-5 mr-2 text-white",xmlns:"http://www.w3.org/2000/svg",fill:"none",viewBox:"0 0 24 24",children:[(0,i.jsx)("circle",{className:"opacity-25",cx:"12",cy:"12",r:"10",stroke:"currentColor",strokeWidth:"4"}),(0,i.jsx)("path",{className:"opacity-75",fill:"currentColor",d:"M4 12a8 8 0 018-8v8h8a8 8 0 01-16 0z"})]}),"Starting..."]}):(0,i.jsx)("span",{className:"mx-1 justify-center flex-1",children:"Reboot"})})]})}),(null==f?void 0:null===(s=f.clientDataList)||void 0===s?void 0:s.length)>0&&(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)("h1",{className:"font-bold flex justify-center text-2xl text-white",children:"Clients"}),(0,i.jsx)("div",{className:"bg-black w-full h-0.5 mb-3 mt-2 rounded-2xl"}),(0,i.jsx)("div",{className:"flex-1 w-full overflow-y-auto no-scrollbar min-h-0 flex-grow max-h-[40vh]",children:null==f?void 0:null===(a=f.clientDataList)||void 0===a?void 0:a.map((e,t)=>(0,i.jsx)(n,{index:t+1,ip:e.clientIP,messages:e.messages.toLocaleString(),onDisconnectClick:()=>u(e.identifier)},t+1))})]})]})})}):(0,i.jsx)("div",{children:"Loading..."})}}},function(e){e.O(0,[563,971,23,744],function(){return e(e.s=8964)}),_N_E=e.O()}]);