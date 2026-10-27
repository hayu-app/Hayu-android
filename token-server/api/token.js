// Token server Agora untuk HAYU.
// Env (diisi di Vercel, JANGAN di repo): AGORA_APP_ID, AGORA_APP_CERT, FIREBASE_PROJECT_ID
const { RtcTokenBuilder, RtcRole } = require("agora-token");
const { createRemoteJWKSet, jwtVerify } = require("jose");

const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

module.exports = async (req, res) => {
  try {
    const appId = process.env.AGORA_APP_ID;
    const cert = process.env.AGORA_APP_CERT;
    const projectId = process.env.FIREBASE_PROJECT_ID;
    if (!appId || !cert || !projectId) {
      return res.status(500).json({ error: "server belum dikonfigurasi" });
    }

    // 1) Pastikan peminta adalah user Firebase yang sudah login
    const h = req.headers.authorization || "";
    const idToken = h.startsWith("Bearer ") ? h.slice(7) : "";
    if (!idToken) return res.status(401).json({ error: "tidak ada token login" });
    await jwtVerify(idToken, JWKS, {
      issuer: "https://securetoken.google.com/" + projectId,
      audience: projectId,
    });

    // 2) Validasi nama channel (= roomId)
    const channel = String((req.query && req.query.channel) || "");
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(channel)) {
      return res.status(400).json({ error: "channel tidak valid" });
    }

    // 3) Buat token (uid 0 = Agora yang menentukan uid), berlaku 1 jam
    const ttl = 3600;
    const token = RtcTokenBuilder.buildTokenWithUid(
      appId, cert, channel, 0, RtcRole.PUBLISHER, ttl, ttl
    );
    res.setHeader("Cache-Control", "no-store");
    return res.status(200).json({ token, expiresIn: ttl });
  } catch (e) {
    return res.status(401).json({ error: "login tidak valid" });
  }
};
