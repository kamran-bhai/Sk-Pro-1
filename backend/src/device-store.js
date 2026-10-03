const crypto = require("crypto");
const { pool } = require("./db");

const devices = new Map();
const commands = new Map();

function normalizeDevice(input) {
  return {
    id: input.id || "dev-" + Date.now() + "-" + Math.random().toString(36).slice(2, 7),
    deviceId: String(input.deviceId || "").trim(),
    imei: String(input.imei || "").trim(),
    model: String(input.model || "").trim(),
    customerName: String(input.customerName || "").trim(),
    customerPhone: String(input.customerPhone || "").trim(),
    status: input.status || "ACTIVE",
    controlKey: input.controlKey || crypto.randomBytes(24).toString("hex"),
    createdAt: input.createdAt || new Date().toISOString(),
    lastSeenAt: input.lastSeenAt || null
  };
}
function rowToDevice(r){return r?{id:r.id,deviceId:r.device_id,imei:r.imei,model:r.model,customerName:r.customer_name,customerPhone:r.customer_phone,status:r.status,controlKey:r.control_key,createdAt:new Date(r.created_at).toISOString(),lastSeenAt:r.last_seen_at?new Date(r.last_seen_at).toISOString():null}:null}
function rowToCommand(r){return r?{id:r.id,deviceId:r.device_id,command:r.command,payload:r.payload||{},status:r.status,createdAt:new Date(r.created_at).toISOString(),updatedAt:new Date(r.updated_at).toISOString(),result:r.result??null}:null}

async function listDevices(){
  if(!pool)return Array.from(devices.values());
  const {rows}=await pool.query(`
    SELECT *,
      CASE
        WHEN last_seen_at IS NOT NULL AND last_seen_at >= NOW() - INTERVAL '30 seconds' THEN 'ONLINE'
        WHEN last_seen_at IS NOT NULL THEN 'OFFLINE'
        ELSE status
      END AS live_status
    FROM devices
    ORDER BY created_at DESC
  `);
  return rows.map(r => ({...rowToDevice(r), status: r.live_status}));
}
async function getDeviceByDeviceId(deviceId){
  if(!pool)return Array.from(devices.values()).find(d=>d.deviceId===deviceId)||null;
  const {rows}=await pool.query("SELECT * FROM devices WHERE device_id=$1",[deviceId]);
  return rows[0] ? rowToDevice(rows[0]) : null;
}
async function getDevice(id){
  if(!pool)return devices.get(id)||null;
  const {rows}=await pool.query(`
    SELECT *,
      CASE
        WHEN last_seen_at IS NOT NULL AND last_seen_at >= NOW() - INTERVAL '30 seconds' THEN 'ONLINE'
        WHEN last_seen_at IS NOT NULL THEN 'OFFLINE'
        ELSE status
      END AS live_status
    FROM devices WHERE id=$1
  `,[id]);
  return rows[0] ? {...rowToDevice(rows[0]), status: rows[0].live_status} : null;
}
async function saveDevice(d){
  if(!pool){devices.set(d.id,d);return d}
  const {rows}=await pool.query(`INSERT INTO devices(id,device_id,imei,model,customer_name,customer_phone,status,control_key,created_at,last_seen_at)
    VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`,
    [d.id,d.deviceId,d.imei,d.model,d.customerName,d.customerPhone,d.status,d.controlKey,d.createdAt,d.lastSeenAt]);
  return rowToDevice(rows[0]);
}
async function markDeviceOnline(d){
  d.status="ONLINE"; d.lastSeenAt=new Date().toISOString();
  if(!pool){devices.set(d.id,d);return d}
  const {rows}=await pool.query("UPDATE devices SET status='ONLINE',last_seen_at=$2 WHERE id=$1 RETURNING *",[d.id,d.lastSeenAt]);
  return rowToDevice(rows[0])||d;
}
async function queueCommand(deviceId,command,payload={}){
  const id="cmd-"+Date.now()+"-"+Math.random().toString(36).slice(2,7),now=new Date().toISOString();
  const item={id,deviceId,command,payload,status:"QUEUED",createdAt:now,updatedAt:now,result:null};
  if(!pool){commands.set(id,item);return item}
  const {rows}=await pool.query(`INSERT INTO commands(id,device_id,command,payload,status,created_at,updated_at,result)
    VALUES($1,$2,$3,$4::jsonb,$5,$6,$7,$8) RETURNING *`,
    [id,deviceId,command,JSON.stringify(payload),"QUEUED",now,now,null]);
  return rowToCommand(rows[0]);
}
async function getQueuedCommands(deviceId,limit=10){
  if(!pool)return Array.from(commands.values()).filter(c=>c.deviceId===deviceId&&c.status==="QUEUED").sort((a,b)=>a.createdAt.localeCompare(b.createdAt)).slice(0,limit);
  const client=await pool.connect();
  try{
    await client.query("BEGIN");
    const {rows}=await client.query("SELECT * FROM commands WHERE device_id=$1 AND status='QUEUED' ORDER BY created_at ASC LIMIT $2 FOR UPDATE SKIP LOCKED",[deviceId,limit]);
    const out=[];
    for(const row of rows){
      const u=await client.query("UPDATE commands SET status='SENT',updated_at=NOW() WHERE id=$1 RETURNING *",[row.id]);
      out.push(rowToCommand(u.rows[0]));
    }
    await client.query("COMMIT"); return out;
  }catch(e){await client.query("ROLLBACK");throw e}finally{client.release()}
}
async function expireStaleCommands(){
  const now=Date.now();
  const queuedTimeoutMs=10*60*1000;
  const sentTimeoutMs=90*1000;
  if(!pool){
    for(const item of commands.values()){
      const age=now-Date.parse(item.updatedAt||item.createdAt);
      const limit=item.status==="SENT"?sentTimeoutMs:queuedTimeoutMs;
      if((item.status==="SENT"||item.status==="QUEUED")&&age>=limit){
        item.status="FAILED";
        item.result=item.status==="SENT" ? "Agent acknowledgement timeout" : "Command delivery timeout";
        item.updatedAt=new Date().toISOString();
      }
    }
    return;
  }
  await pool.query(`
    UPDATE commands
    SET status='FAILED',
        result=CASE WHEN status='SENT' THEN 'Agent acknowledgement timeout' ELSE 'Command delivery timeout' END,
        updated_at=NOW()
    WHERE status IN ('QUEUED','SENT')
      AND ((status='SENT' AND updated_at < NOW()-INTERVAL '90 seconds')
        OR (status='QUEUED' AND created_at < NOW()-INTERVAL '10 minutes'))
  `);
}
async function listCommands(deviceId=null,limit=50){
  await expireStaleCommands();
  if(!pool){
    return Array.from(commands.values())
      .filter(c=>!deviceId||c.deviceId===deviceId)
      .sort((a,b)=>b.createdAt.localeCompare(a.createdAt)).slice(0,limit);
  }
  const params=[];
  let sql="SELECT * FROM commands";
  if(deviceId){params.push(deviceId);sql+=" WHERE device_id=$1";}
  sql+=" ORDER BY created_at DESC LIMIT "+Math.min(Math.max(Number(limit)||50,1),200);
  const {rows}=await pool.query(sql,params);
  return rows.map(rowToCommand);
}
async function getCommand(id){
  if(!pool)return commands.get(id)||null;
  const {rows}=await pool.query("SELECT * FROM commands WHERE id=$1",[id]);return rowToCommand(rows[0]);
}
async function updateCommand(id,status,result=null){
  if(!pool){const item=commands.get(id);if(!item)return null;item.status=status;item.result=result;item.updatedAt=new Date().toISOString();commands.set(id,item);return item}
  const {rows}=await pool.query("UPDATE commands SET status=$2,result=$3,updated_at=NOW() WHERE id=$1 RETURNING *",[id,status,result]);return rowToCommand(rows[0]);
}
async function deleteDevice(id){
  if(!pool){devices.delete(id);for(const [cid,c] of commands)if(c.deviceId===id)commands.delete(cid);return}
  await pool.query("DELETE FROM devices WHERE id=$1",[id]);
}
module.exports={devices,commands,normalizeDevice,listDevices,getDevice,getDeviceByDeviceId,saveDevice,markDeviceOnline,queueCommand,getQueuedCommands,listCommands,expireStaleCommands,getCommand,updateCommand,deleteDevice};