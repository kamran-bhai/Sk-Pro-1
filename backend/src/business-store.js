const crypto = require("crypto");
const { pool } = require("./db");

const customers = new Map();
const agreements = new Map();
const enach = new Map();

function id(prefix){ return prefix+"-"+Date.now()+"-"+crypto.randomBytes(3).toString("hex"); }

async function listCustomers(){
  if(!pool) return [...customers.values()];
  const {rows}=await pool.query("SELECT * FROM customers ORDER BY created_at DESC");
  return rows.map(r=>({id:r.id,name:r.name,phone:r.phone,address:r.address,createdAt:new Date(r.created_at).toISOString()}));
}
async function saveCustomer(v){
  const c={id:v.id||id("cus"),name:String(v.name||"").trim(),phone:String(v.phone||"").trim(),address:String(v.address||"").trim(),createdAt:v.createdAt||new Date().toISOString()};
  if(!c.name||!c.phone) throw new Error("name and phone are required");
  if(!pool){customers.set(c.id,c);return c}
  const {rows}=await pool.query("INSERT INTO customers(id,name,phone,address,created_at) VALUES($1,$2,$3,$4,$5) RETURNING *",[c.id,c.name,c.phone,c.address,c.createdAt]);
  const r=rows[0]; return {id:r.id,name:r.name,phone:r.phone,address:r.address,createdAt:new Date(r.created_at).toISOString()};
}
async function listAgreements(){
  if(!pool) return [...agreements.values()];
  const {rows}=await pool.query("SELECT * FROM agreements ORDER BY created_at DESC");
  return rows.map(r=>agreementRow(r));
}
function agreementRow(r){return {id:r.id,customerId:r.customer_id,deviceId:r.device_id,totalAmount:Number(r.total_amount),downPayment:Number(r.down_payment),installmentAmount:Number(r.installment_amount),numberOfInstallments:r.number_of_installments,paidInstallments:r.paid_installments,remainingAmount:Number(r.remaining_amount),nextDueDate:r.next_due_date,createdAt:new Date(r.created_at).toISOString()}}
async function saveAgreement(v){
  const a={id:v.id||id("agr"),customerId:String(v.customerId||""),deviceId:String(v.deviceId||""),totalAmount:Number(v.totalAmount||0),downPayment:Number(v.downPayment||0),installmentAmount:Number(v.installmentAmount||0),numberOfInstallments:Number(v.numberOfInstallments||0),paidInstallments:Number(v.paidInstallments||0),remainingAmount:Number(v.remainingAmount ?? Math.max(0,Number(v.totalAmount||0)-Number(v.downPayment||0))),nextDueDate:String(v.nextDueDate||""),createdAt:v.createdAt||new Date().toISOString()};
  if(!a.customerId||!a.deviceId||a.totalAmount<=0||a.installmentAmount<=0||a.numberOfInstallments<=0) throw new Error("invalid agreement");
  if(!pool){agreements.set(a.id,a);return a}
  const {rows}=await pool.query("INSERT INTO agreements(id,customer_id,device_id,total_amount,down_payment,installment_amount,number_of_installments,paid_installments,remaining_amount,next_due_date,created_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *",[a.id,a.customerId,a.deviceId,a.totalAmount,a.downPayment,a.installmentAmount,a.numberOfInstallments,a.paidInstallments,a.remainingAmount,a.nextDueDate,a.createdAt]);
  return agreementRow(rows[0]);
}
async function markInstallmentPaid(id){
  if(!pool){const a=agreements.get(id);if(!a)return null;a.paidInstallments=Math.min(a.numberOfInstallments,a.paidInstallments+1);a.remainingAmount=Math.max(0,a.remainingAmount-a.installmentAmount);agreements.set(id,a);return a}
  const {rows}=await pool.query("UPDATE agreements SET paid_installments=LEAST(number_of_installments,paid_installments+1),remaining_amount=GREATEST(0,remaining_amount-installment_amount) WHERE id=$1 RETURNING *",[id]);
  return rows[0]?agreementRow(rows[0]):null;
}
async function listEnach(){
  if(!pool)return [...enach.values()];
  const {rows}=await pool.query("SELECT * FROM enach ORDER BY created_at DESC");
  return rows.map(r=>({id:r.id,customerId:r.customer_id,agreementId:r.agreement_id,mandateRef:r.mandate_ref,status:r.status,createdAt:new Date(r.created_at).toISOString()}));
}
async function saveEnach(v){
  const e={id:v.id||id("nach"),customerId:String(v.customerId||""),agreementId:String(v.agreementId||""),mandateRef:String(v.mandateRef||"").trim(),status:String(v.status||"PENDING").toUpperCase(),createdAt:v.createdAt||new Date().toISOString()};
  if(!e.customerId||!e.agreementId||!e.mandateRef) throw new Error("customerId, agreementId and mandateRef are required");
  if(!pool){enach.set(e.id,e);return e}
  const {rows}=await pool.query("INSERT INTO enach(id,customer_id,agreement_id,mandate_ref,status,created_at) VALUES($1,$2,$3,$4,$5,$6) RETURNING *",[e.id,e.customerId,e.agreementId,e.mandateRef,e.status,e.createdAt]);
  const r=rows[0];return {id:r.id,customerId:r.customer_id,agreementId:r.agreement_id,mandateRef:r.mandate_ref,status:r.status,createdAt:new Date(r.created_at).toISOString()};
}
async function updateEnach(id,status){
  if(!["PENDING","ACTIVE","CANCELLED","FAILED"].includes(status)) throw new Error("invalid eNACH status");
  if(!pool){const e=enach.get(id);if(!e)return null;e.status=status;enach.set(id,e);return e}
  const {rows}=await pool.query("UPDATE enach SET status=$2 WHERE id=$1 RETURNING *",[id,status]);const r=rows[0];return r?{id:r.id,customerId:r.customer_id,agreementId:r.agreement_id,mandateRef:r.mandate_ref,status:r.status,createdAt:new Date(r.created_at).toISOString()}:null;
}
module.exports={listCustomers,saveCustomer,listAgreements,saveAgreement,markInstallmentPaid,listEnach,saveEnach,updateEnach};
