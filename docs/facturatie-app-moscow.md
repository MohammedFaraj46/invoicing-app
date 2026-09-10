# Facturatie-app voor zzp'ers — Feature-prioritering (MoSCoW)

## Inleiding
Het doel van dit project is om een indrukwekkend, afgerond fullstack-project te maken en dit te laten zien aan potentiële werkgevers. Ik richt me op een junior Java-backendrol, dus ik leg de nadruk bewust op technische diepgang aan de backendkant.

De app die ik bouw is een facturatie-applicatie voor zzp'ers. Een zelfstandige kan er klanten in beheren, facturen aanmaken met factuurregels, daar een PDF van genereren, die versturen en de betaalstatus bijhouden.

Ik richt me in eerste instantie op de Nederlandse zzp'er — dat is mijn hoogste prioriteit, inclusief KvK-nummer, BTW-nummer en de Nederlandse factuureisen. Tegelijk wil ik de opzet zo bouwen dat hij later uitbreidbaar is naar Europees/internationaal gebruik. Ik wil daarbij nadrukkelijk géén volledig EU-belastingconform platform maken; dat is voor mij als solo-developer een bodemloze put. In plaats daarvan bouw ik een NL-first applicatie met een uitbreidbaar BTW-datamodel, zodat ik EU B2B-scenario's (met name BTW verlegd) er later bij kan zetten zonder alles opnieuw te hoeven bouwen.

## Techniekstack
Ik heb voor de volgende technieken gekozen:

- **Backend:** Java met Spring Boot (Spring Web, Spring Data JPA, Spring Security)
- **Database:** PostgreSQL
- **Frontend:** React met TypeScript
- **Auth:** JWT (stateless), opgeslagen in een httpOnly cookie met `SameSite=Lax/Strict` en een CSRF-token voor state-changing requests
- **PDF:** ik render een HTML-template naar PDF (bijvoorbeeld met Thymeleaf + OpenHTMLtoPDF/Flying Saucer). Een factuur is in feite een document/template, en ik vind de lay-out in HTML/CSS veel onderhoudbaarder dan een PDF programmatisch "tekenen" in Java. Bovendien houd ik zo de domeinlogica los van de opmaak.
- **AI:** een integratie met een LLM-API, maar dat is voor mij een optionele feature (Could-have)
- **DevOps:** Docker, GitHub Actions voor CI/CD, en uitrol op een VPS met Nginx en HTTPS

## Technische uitgangspunten
Dit zijn de bewuste ontwerpkeuzes waarmee ik de technische diepgang wil laten zien:

- **Geld** reken ik altijd met `BigDecimal` en nooit met `double`. Ik stel expliciet een `RoundingMode` in en leg vast hoe ik afrond (per regel versus op totaalniveau). De berekening zet ik in een aparte, goed geteste `InvoiceCalculator`-service in plaats van verspreid door controllers en entities.
- **Factuurnummering:** een definitief nummer ken ik pas toe bij de overgang van concept naar verzonden, niet al bij het aanmaken van een concept. Zo veroorzaken verwijderde concepten geen gaten. De nummers zijn uniek en oplopend binnen een boekjaar, ik hergebruik ze niet, en de toekenning maak ik concurrency-safe (bijvoorbeeld met een dedicated sequence-tabel met locking of een eigen generator).
- **Onveranderlijkheid:** zodra een factuur verzonden is, is hij onwijzigbaar — de regels, bedragen en BTW liggen dan vast. Alleen concepten kan ik nog bewerken.
- **BTW historisch vastleggen:** het gebruikte BTW-tarief en -type sla ik op de factuurregel zelf op, in plaats van ze live af te leiden uit een productconfiguratie. Een factuur uit 2026 mag niet veranderen als ik in 2027 mijn BTW-instellingen aanpas.
- **BTW-model uitbreidbaar:** ik werk met een `VatType` (bijvoorbeeld `STANDARD`, `REDUCED`, `ZERO`, `REVERSE_CHARGE`) plus een percentage, in plaats van losse booleans. NL-first, maar klaar voor de EU.
- **Data-ownership:** de autorisatie dwing ik af op de backend. Een gebruiker kan uitsluitend zijn eigen bedrijfs-, klant- en factuurdata benaderen; ik scherm dit nooit alleen in de React-frontend af.
- **Auth-opslag:** ik bewaar de JWT in een httpOnly cookie, zodat JavaScript hem niet kan uitlezen en token-diefstal via XSS wordt tegengegaan. Om CSRF af te vangen combineer ik dit met een `SameSite`-cookie en een CSRF-token op state-changing requests. Mijn afweging is dat XSS-token-diefstal het catastrofalere risico is.

## MUST HAVE

### Functioneel (MVP-domein)
Dit is de kern die de app tot een werkende, demonstreerbare applicatie maakt:

- Registratie en inloggen van gebruikers (authenticatie)
- Een bedrijfs-/gebruikersprofiel (naam, adres, KvK-nummer, BTW-nummer)
- Klantenbeheer (aanmaken, bewerken, verwijderen, bekijken)
- Facturen aanmaken met meerdere factuurregels (omschrijving, aantal, tarief)
- Factuurdatum, betaaltermijn en vervaldatum, waarbij de backend de vervaldatum bepaalt
- Automatische berekening van subtotaal, BTW (NL: 21% / 9% / 0%) en totaal met `BigDecimal` en expliciete afronding
- Een factuurnummer dat wordt toegekend bij verzending, uniek en oplopend per boekjaar, concurrency-safe
- Een statusworkflow: concept → verzonden → betaald, waarbij een verzonden factuur onwijzigbaar is
- Een PDF genereren en downloaden in de browser
- Creditnota's: een verzonden factuur kunnen crediteren, met negatieve bedragen, een koppeling naar de originele factuur, een eigen nummering, BTW en PDF
- Facturen bekijken in een overzicht en filteren op status
- Gegevens opslaan in PostgreSQL via JPA
- Een REST-API vanuit de backend
- Een React/TypeScript-frontend die dit alles aanstuurt

### Engineering / kwaliteit
Dit zijn de kwaliteitseisen die ik mezelf bewust opleg; hierin zit voor mij een groot deel van de diepgang-demonstratie:

- Autorisatie en data-ownership afgedwongen op de backend (gebruiker A kan een factuur van gebruiker B niet opvragen → 403/404)
- Degelijke invoervalidatie en een consistent API-foutmodel
- Unit tests voor de kritieke domeinlogica (geld/afronding, nummering, status-overgangen)
- Integratietests voor de kritieke API- en security-flows, bij voorkeur met Testcontainers (een echte PostgreSQL in plaats van H2)
- Databasemigraties met Flyway, vanaf dag 1
- Basis-logging
- De applicatie gedockerized en uitgerold als publiek toegankelijke demo, met CI die bij elke push bouwt en test

## SHOULD HAVE
Deze onderdelen zijn belangrijk en waardevol. De app werkt zonder, maar hiermee wordt hij compleet en portfolio-waardig:

- Een geplande taak die facturen automatisch op "verlopen" zet na de vervaldatum (`@Scheduled`). Interessant gesprekspunt: idempotentie/locking als de app op meerdere instances draait (bijvoorbeeld met ShedLock).
- Facturen per e-mail versturen naar de klant, met de PDF als bijlage. Gesprekspunt: transactionele consistentie — wat gebeurt er als de mail slaagt maar de database-update faalt, of andersom?
- Een volledige CI/CD-pijplijn, naast de basis-CI uit de Must-have
- Een dashboard met statistieken (openstaand bedrag, betaald deze maand, aantal verlopen). Binnen Should-have geef ik dit een lagere prioriteit, omdat ik er relatief weinig nieuws van leer.
- Zoeken en filteren uitbreiden (op klant, datum, bedrag)
- API-documentatie (OpenAPI / Swagger)
- Een logo op de factuur (afbeelding uploaden)
- Meerdere BTW-tarieven en BTW verlegd (reverse charge), voortbouwend op het `VatType`-model, met de NL-tarieven als basis
- BTW-nummer validatie via VIES (een externe EU-dienst) — een leuke externe-API-case met foutafhandeling
- Export naar CSV voor de boekhouder

## COULD HAVE
Deze dingen zijn leuk als er tijd voor is, maar ik kan ze zonder problemen uitstellen zonder de kern aan te tasten:

- AI-feature: een ruwe omschrijving ("3 uur ontwerpwerk à €60 + reiskosten") omzetten naar gestructureerde factuurregels. Als ik dit doe, doe ik het netjes: LLM → gevalideerde JSON → domeinmodel → `InvoiceCalculator`, en nooit rechtstreeks van de LLM naar de database.
- AI-feature: een beleefde betalingsherinnering genereren in natuurlijke taal
- De factuur-PDF ook in het Engels kunnen genereren, voor internationale klanten
- Offertes aanmaken en omzetten naar een factuur
- Terugkerende / periodieke facturen
- Gedeeltelijke betalingen bijhouden
- Aanpasbare factuursjablonen / meerdere lay-outs
- Meerdere valuta
- Automatische escalatie van betalingsherinneringen

## WON'T HAVE (nu niet)
Deze onderdelen laat ik bewust buiten scope voor deze versie:

- Echte betaalintegratie of betaallinks (iDEAL, Stripe live, echte geldstromen)
- Een volledige boekhoud-/administratiesuite
- Volledige EU-belastingconformiteit voor meerdere landen
- Een koppeling met de bank (PSD2 / automatische transactie-import)
- Native mobiele apps (iOS / Android)
- Teams of meerdere gebruikers per bedrijf (multi-user organisaties)
- Een meertalige interface (i18n van de UI zelf)

## Interview-hooks
Als ik de volgende onderwerpen goed implementeer en test, geven ze me sterke gespreksstof tijdens sollicitaties:

1. Hoe ik race conditions voorkom bij het toekennen van factuurnummers.
2. Hoe ik financiële berekeningen correct afrond (per regel versus op totaalniveau).
3. Hoe ik voorkom dat gebruikers elkaars facturen kunnen inzien (ownership op backendniveau).
4. Hoe ik garandeer dat een verzonden factuur niet achteraf ongemerkt verandert.

## Nog te beslissen
1. Mijn eerste release-scope: de Must-have plus welke drie Should-haves? Mijn eigen neiging is om te beginnen met de `@Scheduled` verlopen-status, e-mail, en de volledige CI/CD, en het dashboard en OpenAPI eventueel later te doen.
2. Of ik de AI-factuurregels überhaupt bouw. Die staan laag geprioriteerd (Could), dus hooguit als ik ná de Must-have en de gekozen Should-haves nog tijd overhoud.

## Uitgangspunten bij de uitvoering
Een paar dingen die ik voor mezelf wil vasthouden tijdens het bouwen:

- Ik bouw niet alles. Ik maak eerst de functionele Must-kern werkend, daarna de engineering-requirements, en pas dan de Should-haves en eventueel één stretch-feature.
- Ik maak het project niet groter, maar dieper. Vier goed uitgewerkte technische hooks zijn meer waard dan tien half-af features.
- Een afgeronde, uitgerolde app waar iemand op kan klikken en denkt "dit is echt af" is meer waard dan een lange feature-lijst.
