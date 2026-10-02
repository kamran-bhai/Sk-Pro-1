const crypto = require("crypto");
const devices = new Map();
const commands = new Map();

function normalizeDevice(input) {
  return {
    id: input.id || "dev-" + Date.now(),
    deviceId: String(input.deviceId || "").trim(),
    imei: String(input.imei || "").trim(),
    model: String(input.model || "").trim(),
    customerName: String(input.customerName || "").trim(),
    customerPhone: String(input.customerPhone || "").trim(),
    status: input.status || "ACTIVE",
    controlKey: input.controlKey || crypto.randomBytes(24).toString("hex"),
    createdAt: input.createdAt || new Date().toISOString()
  };
}

function queueCommand(deviceId, command, payload = {}) {
  const id = "cmd-" + Date.now() + "-" + Math.random().toString(36).slice(2, 7);
  const item = { id, deviceId, command, payload, status: "QUEUED", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  commands.set(id, item);
  return item;
}

function updateCommand(id, status, result = null) {
  const item = commands.get(id);
  if (!item) return null;
  item.status = status;
  item.result = result;
  item.updatedAt = new Date().toISOString();
  commands.set(id, item);
  return item;
}

module.exports = { devices, commands, normalizeDevice, queueCommand, updateCommand };