# Deployen op een VPS

Deze handleiding gaat over `docker-compose.prod.yml`: de hele stack (database,
backend, frontend) op één kleine server, bereikbaar via poort 80.

De `docker-compose.yml` in de root blijft wat hij is — de lokale
ontwikkelopstelling, waar de backend zijn poort 8080 wél publiceert zodat
`npm run dev` er met de Vite-proxy naartoe kan. Die twee bestanden staan los van
elkaar; er is geen override-relatie.

Uitgangspunt: Ubuntu 24.04, 1,8 GB RAM met 2 GB swap, UFW met 22, 80 en 443
open.

---

## 1. Eenmalig: de server klaarmaken

Docker Engine en de compose-plugin:

```bash
curl -fsSL https://get.docker.com | sudo sh
```

De code:

```bash
git clone https://github.com/MohammedFaraj46/invoicing-app.git
cd invoicing-app
```

### Let op: Docker gaat langs UFW heen

Docker schrijft zijn eigen iptables-regels in de `DOCKER`-keten, die UFW niet
ziet. Een gepubliceerde poort staat daardoor open op het internet terwijl
`ufw status` volhoudt dat alleen 22, 80 en 443 open zijn.

Dat is precies de reden dat in `docker-compose.prod.yml` alleen de frontend een
`ports:`-blok heeft. Zet er geen poort bij voor de database "om even te kunnen
kijken" — dan staat Postgres op dat moment publiek. Kijken doe je van binnenuit:

```bash
docker compose -f docker-compose.prod.yml exec db psql -U invoice -d invoice
```

---

## 2. De `.env` aanmaken

Op de server, naast `docker-compose.prod.yml`. Dit bestand staat in
`.gitignore` en hoort daar te blijven — hij komt nooit in Git.

```bash
cp .env.example .env
```

Genereer de twee geheimen en plak ze erin:

```bash
openssl rand -base64 32
```

```bash
openssl rand -base64 48
```

De eerste is `POSTGRES_PASSWORD`, de tweede `JWT_SECRET`. Verzin ze niet zelf.
Als er een `$` in de gegenereerde waarde staat: Compose behandelt `$` in een
`.env` als variabele-expansie, dus genereer dan gewoon een nieuwe.

`JWT_COOKIE_SECURE` zet je op `false` zolang de site op `http://<server-ip>`
draait, en op `true` zodra er een certificaat is. Zet hem niet te vroeg op
`true`: de browser accepteert de cookie dan wel, maar stuurt hem over http nooit
terug — inloggen lijkt dan stil te mislukken zonder foutmelding.

Rechten dichtzetten, want hier staan de geheimen in:

```bash
chmod 600 .env
```

---

## 3. Starten

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Ontbreekt er een variabele in de `.env`, dan start Compose niet en noemt hij de
variabele die mist. Dat is bedoeld gedrag: liever niet starten dan half
geconfigureerd starten.

Controleren:

```bash
docker compose -f docker-compose.prod.yml ps
```

Alle drie moeten `healthy` zijn, en in de kolom `PORTS` hoort alleen bij de
frontend `0.0.0.0:80->8080/tcp` te staan. Staat er bij `db` of `backend` ook
iets, dan draait er nog een oude stack of is er een poort toegevoegd.

Logs:

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

De eerste start duurt het langst: Flyway maakt dan het schema aan. In de log van
de backend staat `Migrating schema "public" to version "1 - init"`.

### Over bouwen op de server

Het bouwen zelf is het zwaarste moment van deze hele opzet — Maven en npm
gebruiken samen meer geheugen dan de app daarna nodig heeft. Op 1,8 GB lukt het
met de 2 GB swap erbij, maar het duurt een paar minuten en de server is
ondertussen traag. Gaat het ooit mis met een build die door de OOM-killer wordt
afgeschoten, dan is de oplossing de images elders te bouwen (bijvoorbeeld in CI)
en ze via een registry op te halen in plaats van `--build` op de server.

---

## 4. Een nieuwe versie uitrollen

```bash
cd invoicing-app
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Compose bouwt opnieuw, vervangt alleen de containers waarvan de image is
veranderd, en laat de rest staan. De database blijft in het `pgdata`-volume; die
wordt niet aangeraakt. Eventuele nieuwe Flyway-migraties draaien bij het starten
van de backend.

Even meekijken of het goed ging:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=50 backend
```

Oude, ongebruikte images opruimen (de schijf is klein):

```bash
docker image prune -f
```

### Terugdraaien

```bash
git log --oneline -5
git checkout <commit>
docker compose -f docker-compose.prod.yml up -d --build
```

Let op: dit draait alleen de code terug, niet de database. Een migratie die al
gedraaid heeft blijft gedraaid.

---

## 5. Stoppen en back-ups

Stoppen zonder iets weg te gooien:

```bash
docker compose -f docker-compose.prod.yml down
```

`down -v` verwijdert óók het volume, en daarmee de database. Gebruik dat alleen
als je dat echt bedoelt.

Back-up van de database:

```bash
docker compose -f docker-compose.prod.yml exec -T db \
  pg_dump -U invoice invoice | gzip > backup-$(date +%F).sql.gz
```

Terugzetten:

```bash
gunzip -c backup-2026-09-25.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T db psql -U invoice -d invoice
```

Na een serverherstart komt de stack vanzelf terug: alle services staan op
`restart: unless-stopped`.

---

## 6. Later: HTTPS op 443

Nog niet gedaan. Wat er dan moet gebeuren:

1. Een domeinnaam naar het server-IP laten wijzen.
2. Een certificaat regelen (Let's Encrypt).
3. In `frontend/nginx.conf` een `listen 8443 ssl` server-block toevoegen en het
   certificaat in de container beschikbaar maken via een volume.
4. In `docker-compose.prod.yml` de regel `- "443:8443"` uit commentaar halen.
5. In de `.env` `JWT_COOKIE_SECURE=true` zetten.
6. Opnieuw bouwen en starten.

Stap 5 is makkelijk te vergeten en geeft geen foutmelding — de cookie mist dan
alleen zijn `Secure`-vlag.

---

## Geheugenverdeling

De limieten in `docker-compose.prod.yml`, voor als je je afvraagt waar die
getallen vandaan komen:

| Service  | Limiet | Waarom |
|----------|--------|--------|
| backend  | 768 MB | Maakt `-XX:MaxRAMPercentage=75` uit de image zinvol: de heap komt uit op ~576 MB, de rest is metaspace, thread stacks, code cache en de direct buffers van het PDF-renderen. Zonder limiet rekent de JVM op de 1,8 GB van de host en pakt er ~1,3 GB. |
| db       | 384 MB | `shared_buffers` staat standaard op 128 MB, plus `work_mem` en WAL-buffers per verbinding. Ruim voor deze belasting; het is een plafond tegen een ontspoorde query, geen tuning. |
| frontend | 128 MB | nginx met wat statische bestanden heeft hier een fractie van nodig. De marge zit erin omdat dit de voordeur is: een OOM-kill hier haalt de site neer terwijl de app erachter gezond is. |

Samen 1280 MB van de 1,8 GB. De rest is voor de kernel, sshd, de Docker-daemon
en page cache. De 2 GB swap is het vangnet voor een build, niet onderdeel van
deze begroting.

---

## Als er iets niet werkt

**Inloggen lijkt niets te doen, geen foutmelding.** `JWT_COOKIE_SECURE=true`
terwijl de site op http draait. Zet hem op `false` en start opnieuw.

**Compose weigert te starten en noemt een variabele.** Die ontbreekt in de
`.env`. Vergelijk met `.env.example`.

**502 bij het openen van de site.** De backend is niet (meer) gezond:
`docker compose -f docker-compose.prod.yml logs backend`.

**De backend start niet en de log noemt Flyway.** Meestal een migratie die niet
past op het bestaande schema. De log noemt de versie; niets wegdoen voordat je
een back-up hebt.
