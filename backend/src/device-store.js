const devices = new Map();

function normalizeDevice(input) {
  return {
    id: input.id || "dev-" + Date.now(),
    deviceId: String(input.deviceId || "").trim(),
    imei: String(input.imei || "").trim(),
    model: String(input.model || "").trim(),
    customerName: String(input.customerName || "").trim(),
    customerPhone: String(input.customerPhone || "").trim(),
    status: input.status || "ACTIVE",
    createdAt: input.createdAt || new Date().toISOString()
  };
}

module.exports = { devices, normalizeDevice };