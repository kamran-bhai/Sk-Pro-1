const http=require("http"),crypto=require("crypto");
const {initDb}=require("./db");
const {normalizeDevice,listDevices,getDevice,getDeviceByDeviceId,saveDevice,markDeviceOnline,queueCommand,getQueuedCommands,listCommands,expireStaleCommands,getCommand,updateCommand,deleteDevice}=require("./device-store");
const {listCustomers,saveCustomer,listAgreements,saveAgreement,markInstallmentPaid,listEnach,saveEnach,updateEnach}=require("./business-store");
const PORT=Number(process.env.PORT||10000),JWT_SECRET=process.env.JWT_SECRET||"bd-pro-change-this-secret";
const ADMIN_EMAIL=(process.env.ADMIN_EMAIL||"admin@bdpro.local").toLowerCase(),ADMIN_PASSWORD=process.env.ADMIN_PASSWORD||"ChangeMe123!";
const b64=v=>Buffer.from(v).toString("base64url");
function makeToken(){const h=b64(JSON.stringify({alg:"HS256",typ:"JWT"})),p=b64(JSON.stringify({sub:"admin-1",email:ADMIN_EMAIL,role:"ADMIN",exp:Math.floor(Date.now()/1000)+43200})),s=crypto.createHmac("sha256",JWT_SECRET).update(h+"."+p).digest("base64url");return h+"."+p+"."+s}
function verifyToken(token){try{const parts=String(token||"").split(".");if(parts.length!==3)return null;const [h,p,s]=parts;const expected=crypto.createHmac("sha256",JWT_SECRET).update(h+"."+p).digest("base64url");if(s.length!==expected.length||!crypto.timingSafeEqual(Buffer.from(s),Buffer.from(expected)))return null;const payload=JSON.parse(Buffer.from(p,"base64url").toString("utf8"));if(payload.exp<=Math.floor(Date.now()/1000)||payload.role!=="ADMIN")return null;return payload}catch{return null}}
function send(res,status,data){res.writeHead(status,{"Content-Type":"application/json","Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"Content-Type, Authorization, X-Device-Key","Access-Control-Allow-Methods":"GET, POST, DELETE, OPTIONS"});res.end(JSON.stringify(data))}
function adminAuth(req,res){const h=req.headers.authorization||"";if(!h.startsWith("Bearer ")){send(res,401,{message:"Missing access token"});return false}if(!verifyToken(h.slice(7))){send(res,401,{message:"Invalid or expired access token"});return false}return true}
function deviceAuth(req,res,d){if(!d||req.headers["x-device-key"]!==d.controlKey){send(res,401,{message:"Invalid device credentials"});return false}return true}
function readBody(req,done){let raw="";req.on("data",c=>raw+=c);req.on("end",()=>{try{done(JSON.parse(raw||"{}"))}catch{done({})}})}

const server=http.createServer(async(req,res)=>{
 try{
  if(req.method==="OPTIONS")return send(res,204,{});
  if(req.method==="GET"&&req.url==="/health")return send(res,200,{ok:true,service:"bd-pro-backend"});
  if(req.method==="POST"&&req.url==="/api/v1/auth/login")return readBody(req,b=>{const email=String(b.email||"").trim().toLowerCase(),password=String(b.password||"");if(!email||!password)return send(res,400,{message:"Email and password are required"});if(email!==ADMIN_EMAIL||password!==ADMIN_PASSWORD)return send(res,401,{message:"Invalid admin credentials"});send(res,200,{token:makeToken(),email:ADMIN_EMAIL,role:"ADMIN",expiresIn:43200})});
  if(req.method==="POST"&&req.url==="/api/v1/agent/enroll")return readBody(req,async b=>{const did=String(b.deviceId||"").trim();const d=await getDeviceByDeviceId(did);if(!d)return send(res,404,{message:"Device ID is not registered"});if(!deviceAuth(req,res,d))return;await markDeviceOnline(d);return send(res,200,{enrolled:true,device:{id:d.id,deviceId:d.deviceId,model:d.model,customerName:d.customerName,status:d.status}})});
  const poll=req.url.match(/^\/api\/v1\/agent\/devices\/([^/]+)\/commands$/);
  if(poll&&req.method==="GET"){const d=await getDeviceByDeviceId(poll[1]);if(!deviceAuth(req,res,d))return;await markDeviceOnline(d);return send(res,200,{commands:await getQueuedCommands(d.id,10)})}
  const ack=req.url.match(/^\/api\/v1\/agent\/commands\/([^/]+)\/ack$/);
  if(ack&&req.method==="POST"){const c=await getCommand(ack[1]);if(!c)return send(res,404,{message:"Command not found"});const d=await getDevice(c.deviceId);if(!deviceAuth(req,res,d))return;return readBody(req,async b=>{const status=String(b.status||"FAILED").toUpperCase();if(!["SUCCESS","FAILED"].includes(status))return send(res,400,{message:"Status must be SUCCESS or FAILED"});send(res,200,{command:await updateCommand(c.id,status,b.result??null)})})}
  if(!adminAuth(req,res))return;
  if(req.method==="GET"&&req.url==="/api/v1/customers")return send(res,200,{customers:await listCustomers()});
  if(req.method==="POST"&&req.url==="/api/v1/customers")return readBody(req,async b=>{try{send(res,201,{customer:await saveCustomer(b)})}catch(e){send(res,400,{message:e.message})}});
  if(req.method==="GET"&&req.url==="/api/v1/agreements")return send(res,200,{agreements:await listAgreements()});
  if(req.method==="POST"&&req.url==="/api/v1/agreements")return readBody(req,async b=>{try{send(res,201,{agreement:await saveAgreement(b)})}catch(e){send(res,400,{message:e.message})}});
  const pay=req.url.match(/^\/api\/v1\/agreements\/([^/]+)\/pay$/);
  if(pay&&req.method==="POST"){const a=await markInstallmentPaid(pay[1]);if(!a)return send(res,404,{message:"Agreement not found"});return send(res,200,{agreement:a})}
  if(req.method==="GET"&&req.url==="/api/v1/enach")return send(res,200,{enach:await listEnach()});
  if(req.method==="POST"&&req.url==="/api/v1/enach")return readBody(req,async b=>{try{send(res,201,{enach:await saveEnach(b)})}catch(e){send(res,400,{message:e.message})}});
  const nach=req.url.match(/^\/api\/v1\/enach\/([^/]+)\/status$/);
  if(nach&&req.method==="POST")return readBody(req,async b=>{try{const e=await updateEnach(nach[1],String(b.status||"").toUpperCase());if(!e)return send(res,404,{message:"eNACH record not found"});send(res,200,{enach:e})}catch(e){send(res,400,{message:e.message})}});
  if(req.method==="GET"&&req.url.startsWith("/api/v1/commands")){
    const u=new URL(req.url,"http://localhost");
    const deviceId=u.searchParams.get("deviceId");
    const limit=u.searchParams.get("limit")||"50";
    return send(res,200,{commands:await listCommands(deviceId,limit)});
  }
  if(req.method==="GET"&&req.url==="/api/v1/devices"){const ds=await listDevices();return send(res,200,{devices:ds.map(({controlKey,...d})=>d)})}
  if(req.method==="POST"&&req.url==="/api/v1/devices")return readBody(req,async b=>{if(!b.deviceId||!b.imei)return send(res,400,{message:"deviceId and imei are required"});try{const d=await saveDevice(normalizeDevice(b));send(res,201,{device:d,enrollment:{deviceId:d.deviceId,controlKey:d.controlKey}})}catch(e){if(e.code==="23505")send(res,409,{message:"Device ID or control key already exists"});else throw e}});
  const cm=req.url.match(/^\/api\/v1\/devices\/([^/]+)\/commands$/);
  if(cm&&req.method==="POST")return readBody(req,async b=>{const d=await getDevice(cm[1]);if(!d)return send(res,404,{message:"Device not found"});const allowed=["LOCK","UNLOCK","LOCATION","DIAGNOSTICS","AUTOLOCK_ON","AUTOLOCK_OFF","ANTI_THEFT_ON","ANTI_THEFT_OFF"],command=String(b.command||"").trim().toUpperCase();if(!allowed.includes(command))return send(res,400,{message:"Unsupported command"});send(res,202,{command:await queueCommand(d.id,command,b.payload||{})})});
  const qs=req.url.match(/^\/api\/v1\/commands\/([^/]+)$/);
  if(qs&&req.method==="GET")return send(res,200,{command:await getCommand(qs[1])});
  const m=req.url.match(/^\/api\/v1\/devices\/([^/]+)$/);
  if(m&&req.method==="GET"){const d=await getDevice(m[1]);if(!d)return send(res,404,{message:"Device not found"});const {controlKey,...safe}=d;return send(res,200,{device:safe})}
  if(m&&req.method==="DELETE"){await deleteDevice(m[1]);return send(res,200,{ok:true})}
  send(res,404,{message:"Not found"});
 }catch(e){console.error(e);send(res,500,{message:"Internal server error"})}
});
initDb().then(()=>server.listen(PORT,"0.0.0.0",()=>console.log("BD Pro backend listening on "+PORT))).catch(e=>{console.error("Database initialization failed",e);process.exit(1)});