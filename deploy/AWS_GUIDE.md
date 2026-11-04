# Deploying AgriCient to AWS (one server, one database)

Plan: **one EC2 server** runs the app in Docker, **Caddy** in front gives free HTTPS, and **RDS MySQL** is the database.
Why this plan: it is the simplest set-up with a real HTTPS address, and HTTPS is required for the GPS button and push notifications.
Keep to **one server only**: the scheduled alert jobs would run twice with two servers.

> Not tested on AWS yet. I could not create an AWS account or run Docker here. The JAR was tested; the Docker image and these steps are untested. Expect small fixes.

## 0. Before you start
- AWS Free plan (checked on aws.amazon.com/free): new accounts get about $100 in credits, up to $200, valid for 6 months. The account closes itself when the credits run out or after 6 months unless you upgrade. Check the page again when you sign up, because offers change.
- Pick one region near your farmers (for India: **Asia Pacific (Mumbai) ap-south-1**) and use it for everything.
- Create a **budget alert**: Billing → Budgets → "Zero spend budget".
- Turn on MFA for the root user. Do not use the root user for daily work.
- A domain name. If you have none, make a free one at duckdns.org (e.g. `agricient.duckdns.org`).

## 1. Database (RDS MySQL)
1. RDS → Create database → **MySQL**, template **Free tier**, instance `db-t3-micro` (or the smallest allowed).
2. Name `agricient-db`, master user `admin`, set a strong password and keep it.
3. **Public access: No.** Storage 20 GB is enough.
4. Create a security group `db-sg` that allows **port 3306 only from `app-sg`** (made in step 2).
5. When it is ready, copy the **endpoint**. Create the app's database and user (run once from the EC2 server, step 3):
```sql
CREATE DATABASE agricient CHARACTER SET utf8mb4;
CREATE USER 'agricient'@'%' IDENTIFIED BY 'a-strong-password';
GRANT ALL ON agricient.* TO 'agricient'@'%';
```

## 2. Server (EC2)
1. EC2 → Launch instance → **Amazon Linux 2023**, type **t3.small** (2 GB RAM; the app alone uses about 270 MB, the model libraries need room). Create a key pair and keep the `.pem` file safe.
2. Security group `app-sg`: allow **22 (SSH) from My IP only**, **80** and **443** from anywhere. Never open 8080 or 3306 to the world.
3. Allocate an **Elastic IP** and attach it, so the address does not change on restart.
4. Point your domain to that IP (an A record, or the DuckDNS page).

## 3. Install Docker on the server
```bash
ssh -i your-key.pem ec2-user@YOUR-IP
sudo dnf install -y docker mariadb105
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user      # log out and in again afterwards
```
Create the database and user from step 1 with:
`mysql -h YOUR-RDS-ENDPOINT -u admin -p`

## 4. Put the app on the server
From your Mac, copy the backend folder (without `target/`):
```bash
rsync -av --exclude target -e "ssh -i your-key.pem" backend/ ec2-user@YOUR-IP:~/backend/
```
On the server:
```bash
cd ~/backend && docker build -t agricient .
```
The first build takes several minutes and downloads Maven packages.

## 5. Settings and secrets
Copy `deploy/agricient.env.example` to the server as `~/agricient.env`, fill it in, then `chmod 600 ~/agricient.env`.
- `DB_URL` uses your RDS endpoint; `DB_USER=agricient`; `DB_PASSWORD` is the one from step 1.
- Push keys: reuse the ones in `~/.agricient/push.env` on your Mac, or generate new ones. **Do not change them later**, or every phone must subscribe again.
- `OFFICER_INVITE_CODE`: choose a long random value, tell it only to real officers.
- `SMS_PROVIDER=log` for now (simulated). Real SMS with your own words needs a paid provider.
- Never commit this file or paste it into chats. Rotate any Twilio token you showed in screenshots.

## 6. Run it
```bash
docker network create web
docker run -d --name agricient --restart unless-stopped --network web \
  --env-file ~/agricient.env agricient
```
Check: `docker logs -f agricient` (look for "Started" and no errors). Then HTTPS with Caddy:
```bash
cat > ~/Caddyfile <<'C'
YOUR-DOMAIN {
    reverse_proxy agricient:8080
}
C
docker run -d --name caddy --restart unless-stopped --network web \
  -p 80:80 -p 443:443 -v ~/Caddyfile:/etc/caddy/Caddyfile -v caddy_data:/data caddy:2
```
Caddy fetches and renews the certificate by itself (ports 80 and 443 must be open and the domain must already point to the server).

## 7. After the first successful start
- Set `DB_DDL=validate` in `~/agricient.env`, then `docker rm -f agricient` and run step 6's `docker run` again. The tables now exist and the app will not change them by itself.
- **Back up** the database: enable RDS automated backups (retention at least 7 days).

## 8. Test checklist (do all of it on the public HTTPS address)
1. `https://YOUR-DOMAIN/api/health` shows `{"status":"ok"}`.
2. Register a farmer; the "Use my current location" button now works (HTTPS).
3. Upload a coconut leaf photo; you get a diagnosis.
4. "Turn on alerts on this device" on your phone, then confirm a notification arrives with the browser closed. (iPhone: first Add to Home Screen.)
5. Register an officer with the invite code; open the map; confirm the farmer's name and phone are NOT visible.
6. Wrong invite code 5 times → locked for 15 minutes. Note: it is stored in memory, so a restart clears it.
7. Delete your test accounts.

## 9. Updating the app later
Copy the new files, `docker build -t agricient .`, `docker rm -f agricient`, run step 6 again. Users stay logged in because sessions live in the database.

## 10. Known limits (say these honestly)
- One server only (scheduler duplicates otherwise); a server restart means a short outage.
- No automatic tests against a real database in a pipeline, no password reset, no login throttling for passwords (only for the invite code), login token kept in the browser.
- SMS is simulated unless a paid provider is configured.
- The 7-day forecast is no better than "tomorrow equals today"; the forecast target is a weather-risk index, not recorded outbreaks.
- A tidier AWS set-up later: App Runner or ECS behind a load balancer + Secrets Manager. It costs more and needs the scheduler moved to a single worker.

## Rough monthly cost after any credits end (check the AWS calculator, prices change)
EC2 t3.small + RDS db.t3.micro + storage + the Elastic IP + data transfer: a few tens of US dollars per month. Stop the server and database when you are not using them, or delete everything after the demo.
