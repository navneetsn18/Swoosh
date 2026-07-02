<div align="center">

# 📨 Swoosh

### *Because your parents deserve a bodyguard for their inbox.*

**Swoosh** quietly forwards SMS messages from one phone to another, so you can keep an eye
out for the "CONGRATULATIONS YOU WON ₹10,00,000, CLICK HERE" nonsense before your mom does.

No cloud. No servers. No shady SDK phoning home to a data broker in who-knows-where.
Just an SMS going from Phone A to Phone B, the way nature intended.

</div>

---

## 🤔 Why does this exist?

There are a hundred SMS-forwarding apps on the Play Store. Most of them want the internet,
your contacts, your location, your firstborn, and a 4.1-star review.

I didn't trust any of them with my parents' bank OTPs. So I read the code of this one very
carefully — because I wrote it. You can too. It's all right here. That's the whole pitch.

**The honest sales copy:**
- 🔒 **No internet permission.** Literally can't leak your data online. Check the manifest, I'll wait.
- 🧠 **Rules engine.** Forward only what matters — by sender, by keyword, or everything.
- ⏳ **Self-destructing rules.** Set a rule to expire in a day, week, month, year, or never.
- 🧼 **Word-stripping.** Carriers love to eat messages containing "OTP" and "verification code."
  Swoosh yanks those words out so your forward actually arrives. Sneaky. Effective.
- 🎨 **Actually looks nice.** Blue gradients, a pixel-art icon, a little animation. We fancy now.

---

## 📥 Just want the app? (You, the non-nerd)

1. Go to the [**Releases**](../../releases) page.
2. Download the latest `Swoosh.apk`.
3. Open it on the phone. Android will clutch its pearls and say *"Blocked by Play Protect."*
   This is normal — an app that forwards SMS looks *exactly* like spyware to Google's scanner.
   It can't tell that you're a good person. Tap **More details → Install anyway.**
4. Open Swoosh, grant the SMS permissions, add a rule, done.

> ⚠️ **Set the battery to "Unrestricted"** (Settings → Apps → Swoosh → Battery), or your phone
> will eventually murder the app in its sleep and forwarding will stop. Phones are dramatic.

---

## 🧑‍🍳 Want to build it yourself? (You, the nerd)

```bash
git clone git@github.com:navneetsn18/Swoosh.git
cd Swoosh
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

Or just open it in **Android Studio** and smash the green ▶ button. Requires JDK 17+.

---

## 🛠️ How it works (the 30-second version)

```
Incoming SMS ──▶ SmsReceiver ──▶ checks every active rule
                                       │
                        does sender/text match? not expired? enabled?
                                       │ yes
                                       ▼
                        strip filtered words ──▶ SmsManager sends it to your number(s)
```

- **One `BroadcastReceiver`** catches `SMS_RECEIVED`.
- **Rules** live as JSON in `SharedPreferences` (no database, we're not animals... or lazy... yes lazy).
- **Sending** is plain old `SmsManager`. Same SMS network your grandma uses.

---

## 🧩 A rule, explained

| Field | What it does |
|-------|--------------|
| **Forward to** | One or more numbers. Comma-separated. |
| **Sender contains** | Matches the number *or* service ID (`HDFCIA-S`), not a contact name. |
| **Message text contains** | Substring match on the body. |
| **Words to remove** | Deletes filter-bait words (`OTP`, `verification`, `code`…) before sending. |
| **Active for** | 1 day / 1 week / 1 month / 1 year / forever. Expired rules go quiet automatically. |

Leave the conditions blank to forward **everything**. Live dangerously.

---

## 🚧 Honest limitations

- **SMS only.** WhatsApp, RCS, iMessage — not SMS, not forwarded. That's where OTPs live anyway.
- **Costs real SMS.** Every forward is a text sent from the sender phone's SIM. Check the plan.
- **iPhone?** No. iOS doesn't let apps read SMS. This is an Android party.
- **You'll see their OTPs.** That's the point. Keep *your* phone locked, hero.

---

## 📜 License & credits

Do whatever you want with it — it's your family's safety, not mine.
Font: [Poppins](https://fonts.google.com/specimen/Poppins) (OFL). Animations: home-grown Lottie.

Built with love, paranoia, and a healthy distrust of "free" apps. 💙
