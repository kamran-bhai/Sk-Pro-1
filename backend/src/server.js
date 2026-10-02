const http = require("http");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "bd-pro-change-this-secret";
const ADMIN_EMAIL = (process.env.ADMIN_EMAIL || "admin@bdpro.local").toLowerCase();
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "ChangeMe123!";

function b64(value) { return Buffer.from(value).toString("base64url"); }
function token() {
  const h = b64(JSON.stringify({alg:"HS256",typ:"JWT"}));
  const p = b64(JSON.stringify({sub:"admin-1",email:ADMIN_EMAIL,role:"ADMIN",exp:Math.floor(Date.now()/1000)+43200}));
  const s = crypto.createHmac("sha256", JWT_SECRET).update(h+"."+p).digest("base64url");
  return h+"."+p+"."+s;
}
function send(res,status,data) {
  res.writeHead(status,{"Content-Type":"application/json","Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"Content-Type, Authorization","Access-Control-Allow-Methods":"GET, POST, OPTIONS"});
  res.end(JSON.stringify(data));
}
http.createServer((req,res)=>{
  if(req.method==="OPTIONS") return send(res,204,{});
  if(req.method==="GET" && req.url==="/health") return send(res,200,{ok:true,service:"bd-pro-backend"});
  if(req.method==="POST" && req.url==="/api/v1/auth/login"){
    let raw=""; req.on("data",c=>raw+=c); req.on("end",()=>{
      let body={}; try{body=JSON.parse(raw||"{}")}catch{}
      const email=String(body.email||"").trim().toLowerCase(), password=String(body.password||"");
      if(!email||!password) return send(res,400,{message:"Email and password are required"});
      if(email!==ADMIN_EMAIL||password!==ADMIN_PASSWORD) return send(res,401,{message:"Invalid admin credentials"});
      return send(res,200,{token:token(),email:ADMIN_EMAIL,role:"ADMIN",expiresIn:43200});
    }); return;
  }
  return send(res,404,{message:"Not found"});
}).listen(PORT,"0.0.0.0",()=>console.log("BD Pro backend listening on "+PORT));