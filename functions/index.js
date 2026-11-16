const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

async function notifyUser(userId, title, body, data = {}) {
  const userRef = db.collection("users").doc(userId);
  const snap = await userRef.get();
  if (!snap.exists) return;
  const tokens = Array.isArray(snap.get("fcmTokens")) ? snap.get("fcmTokens") : [];
  if (!tokens.length) return;

  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const result = await getMessaging().sendEachForMulticast({
      tokens: batch,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: { priority: "high", notification: { channelId: "codebihin_messages" } }
    });
    const invalid = [];
    result.responses.forEach((response, index) => {
      const code = response.error && response.error.code;
      if (code === "messaging/invalid-registration-token" ||
          code === "messaging/registration-token-not-registered") invalid.push(batch[index]);
    });
    if (invalid.length) await userRef.update({ fcmTokens: FieldValue.arrayRemove(...invalid) });
  }
}

exports.onNewChatMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message) return;
    const chatId = event.params.chatId;
    const chatSnap = await db.collection("chats").doc(chatId).get();
    if (!chatSnap.exists) return;
    const chat = chatSnap.data();
    const recipients = (chat.members || []).filter(uid => uid !== message.senderId);
    const title = chat.isGroup
      ? (chat.title || "Group message")
      : (message.senderName || "New message");
    const body = message.text || (message.attachmentUrl ? "Shared a file link" : "Sent you a message");
    await Promise.all(recipients.map(uid =>
      notifyUser(uid, title, body.slice(0, 180), { type: "chat", chatId })
    ));
  }
);

exports.onAnnouncementCreated = onDocumentCreated(
  "announcements/{announcementId}",
  async (event) => {
    const announcement = event.data && event.data.data();
    if (!announcement) return;
    const users = await db.collection("users").get();
    const tasks = [];
    users.forEach(doc => tasks.push(
      notifyUser(doc.id, announcement.title || "CodeBihin announcement",
        (announcement.body || "There is a new announcement.").slice(0, 180),
        { type: "announcement", announcementId: event.params.announcementId })
    ));
    await Promise.all(tasks);
  }
);
