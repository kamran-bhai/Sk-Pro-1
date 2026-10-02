const http = require("http");
const crypto = require("crypto");
const { devices, normalizeDevice } = require("./device-store");

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "bd-pro-change-this-secret";
const ADMIN_EMAIL = (process.env.ADMIN_EMAIL || "admin@bdpro.local").toLowerCase();
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "ChangeMe123!";

function b64(v){return Buffer.from(v).toString("base64url")}
function makeToken(){const h=b64(JSON.stringify({alg:"HS256",typ:"JWT"}));const p=b64(JSON.stringify({sub:"admin-1",email:ADMIN_EMAIL,role:"ADMIN",exp:Math.floor(Date.now()/1000)+43200}));const s=crypto.createHmac("sha256",JWT_SECRET).update(h+"."+p).digest("base64url");return h+"."+p+"."+s}
function send(res,status,data){res.writeHead(status,{"Content-Type":"application/json","Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"Content-Type, Authorization","Access-Control-Allow-Methods":"GET, POST, OPTIONS"});res.end(JSON.stringify(data))}
function auth(req,res){const h=req.headers.authorization||"";if(!h.startsWith("Bearer ")){send(res,401,{message:"Missing access token"});return false}return true}
function readBody(req,done){let raw="";req.on("data",c=>raw+=c);req.on("end",()=>{try{done(JSON.parse(raw||"{}"))}catch{done({})}})}

http.createServer((req,res)=>{
 if(req.method==="OPTIONS")return send(res,204,{});
 if(req.method==="GET"&&req.url==="/health")return send(res,200,{ok:true,service:"bd-pro-backend"});
 if(req.method==="POST"&&req.url==="/api/v1/auth/login")return readBody(req,b=>{
   const email=String(b.email||"").trim().toLowerCase(),password=String(b.password||"");
   if(!email||!password)return send(res,400,{message:"Email and password are required"});
   if(email!==ADMIN_EMAIL||password!==ADMIN_PASSWORD)return send(res,401,{message:"Invalid admin credentials"});
   send(res,200,{token:makeToken(),email:ADMIN_EMAIL,role:"ADMIN",expiresIn:43200});
 });
 if(!auth(req,res))return;
 if(req.method==="GET"&&req.url==="/api/v1/devices")return send(res,200,{devices:Array.from(devices.values())});
 if(req.method==="POST"&&req.url==="/api/v1/devices")return readBody(req,b=>{
   if(!b.deviceId||!b.imei)return send(res,400,{message:"deviceId and imei are required"});
   const d=normalizeDevice(b);devices.set(d.id,d);send(res,201,{device:d});
 });
 const m=req.url.match(/^\/api\/v1\/devices\/([^/]+)$/);
 if(m&&req.method==="GET")return send(res,200,{device:devices.get(m[1])||null});
 if(m&&req.method==="DELETE"){devices.delete(m[1]);return send(res,200,{ok:true})}
 send(res,404,{message:"Not found"});
}).listen(PORT,"0.0.0.0",()=>console.log("BD Pro backend listening on "+PORT));