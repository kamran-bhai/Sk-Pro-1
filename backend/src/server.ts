import express from "express";
import cors from "cors";
import jwt from "jsonwebtoken";

const app = express();
app.use(cors());
app.use(express.json());

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "bd-pro-change-this-secret";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || "admin@bdpro.local";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "ChangeMe123!";

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "bd-pro-backend" });
});

app.post("/api/v1/auth/login", (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const password = String(req.body?.password || "");

  if (!email || !password) {
    return res.status(400).json({ message: "Email and password are required" });
  }

  if (email !== ADMIN_EMAIL.toLowerCase() || password !== ADMIN_PASSWORD) {
    return res.status(401).json({ message: "Invalid admin credentials" });
  }

  const token = jwt.sign(
    { sub: "admin-1", email: ADMIN_EMAIL, role: "ADMIN" },
    JWT_SECRET,
    { expiresIn: "12h" }
  );

  return res.json({
    token,
    email: ADMIN_EMAIL,
    role: "ADMIN",
    expiresIn: 43200
  });
});

app.get("/api/v1/auth/me", (req, res) => {
  const header = req.header("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";

  if (!token) {
    return res.status(401).json({ message: "Missing access token" });
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET) as jwt.JwtPayload;
    return res.json({
      id: payload.sub,
      email: payload.email,
      role: payload.role
    });
  } catch {
    return res.status(401).json({ message: "Invalid or expired access token" });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`BD Pro backend listening on port ${PORT}`);
});