# Generates app/src/main/assets/german_bank.json (A2/B1 vocabulary + grammar).
# Vocab line: "german|english" (append " *" for B1). Grammar line: "LEVEL|question|answer;wrong1;wrong2;wrong3".
import json, hashlib
VOCAB = """
der Bahnhof|train station
die Haltestelle|bus stop
der Fahrplan|timetable
die Fahrkarte|ticket
das Gepäck|luggage
der Flughafen|airport
die Reise|trip
der Urlaub|vacation
die Unterkunft|accommodation *
das Zimmer|room
die Wohnung|apartment
die Miete|rent
der Vermieter|landlord *
die Küche|kitchen
das Schlafzimmer|bedroom
das Badezimmer|bathroom
der Kühlschrank|fridge
die Waschmaschine|washing machine
der Schrank|cupboard
das Regal|shelf
der Teppich|carpet
der Spiegel|mirror
die Treppe|stairs
der Aufzug|elevator
der Nachbar|neighbour
die Rechnung|bill
die Kasse|checkout
das Angebot|special offer
der Preis|price
die Größe|size
die Tüte|bag
das Geschäft|shop
die Apotheke|pharmacy
das Krankenhaus|hospital
die Krankheit|illness
das Fieber|fever
die Erkältung|cold (illness)
der Schmerz|pain
das Rezept|prescription
die Versicherung|insurance
der Termin|appointment
die Sprechstunde|consultation hours
die Stelle|job position
der Beruf|profession
die Bewerbung|job application
das Vorstellungsgespräch|job interview
der Lebenslauf|CV
das Gehalt|salary
der Kollege|colleague
die Firma|company
die Besprechung|meeting
die Aufgabe|task
die Erfahrung|experience
die Ausbildung|vocational training
das Studium|university studies
die Prüfung|exam
das Zeugnis|certificate
der Unterricht|lessons
die Hausaufgabe|homework
das Wörterbuch|dictionary
die Sprache|language
die Nachricht|message
die Zeitung|newspaper
die Sendung|TV show
der Bildschirm|screen
die Taste|key (on a keyboard)
das Passwort|password
die Anmeldung|registration
das Formular|form
die Unterschrift|signature
der Ausweis|ID card
die Behörde|public authority *
die Gebühr|fee *
der Umzug|move (to a new home)
die Hochzeit|wedding
das Geschenk|gift
die Einladung|invitation
die Feier|celebration
der Gast|guest
das Gewitter|thunderstorm
die Jahreszeit|season
der Frühling|spring
der Herbst|autumn
die Umwelt|environment *
der Müll|rubbish
die Kleidung|clothing
die Jacke|jacket
die Hose|trousers
das Hemd|shirt
der Handschuh|glove
die Brille|glasses
die Freizeit|free time
der Verein|club
die Mannschaft|team
der Wettbewerb|competition *
die Ausstellung|exhibition
die Eintrittskarte|admission ticket
der Eingang|entrance
der Ausgang|exit
die Ecke|corner
die Kreuzung|crossroads
die Ampel|traffic light
der Stau|traffic jam
der Unfall|accident
die Feuerwehr|fire brigade
der Notfall|emergency
die Gefahr|danger
die Sicherheit|safety
der Vorteil|advantage *
der Nachteil|disadvantage *
die Meinung|opinion
der Grund|reason
die Lösung|solution
die Möglichkeit|possibility
die Bedingung|condition *
die Voraussetzung|prerequisite *
die Verantwortung|responsibility *
die Beziehung|relationship *
die Ehe|marriage
die Trennung|separation *
das Gefühl|feeling
die Angst|fear
die Freude|joy
der Ärger|annoyance
die Sorge|worry
die Gewohnheit|habit
die Geduld|patience
die Ablenkung|distraction *
das Ziel|goal
der Erfolg|success
die Entscheidung|decision
die Ernährung|nutrition *
das Gemüse|vegetables
das Obst|fruit
die Speisekarte|menu
das Trinkgeld|tip (money)
die Mahlzeit|meal
die Zutat|ingredient *
der Topf|pot
die Pfanne|pan
das Messer|knife
die Gabel|fork
der Löffel|spoon
der Teller|plate
abfahren|to depart
ankommen|to arrive
umsteigen|to change (trains)
einsteigen|to get on
aussteigen|to get off
sich bewerben|to apply (for a job)
kündigen|to hand in one's notice
verdienen|to earn
sparen|to save (money)
ausgeben|to spend (money)
leihen|to lend
mieten|to rent
umziehen|to move house
einladen|to invite
feiern|to celebrate
gratulieren|to congratulate
sich freuen|to be pleased
sich ärgern|to be annoyed
sich beschweren|to complain *
sich entschuldigen|to apologise
sich erinnern|to remember
sich entscheiden|to decide
sich gewöhnen|to get used to *
sich kümmern|to take care of *
sich unterhalten|to have a conversation *
sich verabreden|to arrange to meet *
vorschlagen|to suggest *
empfehlen|to recommend
erklären|to explain
beschreiben|to describe
vergleichen|to compare *
entwickeln|to develop *
verbessern|to improve *
erreichen|to achieve
verlieren|to lose
gewinnen|to win
vermeiden|to avoid *
verbieten|to forbid
erlauben|to allow
versprechen|to promise
vorbereiten|to prepare
aufhören|to stop (doing something)
aufräumen|to tidy up
putzen|to clean
reparieren|to repair
ausfüllen|to fill in (a form)
unterschreiben|to sign
bestellen|to order
abholen|to pick up
begleiten|to accompany *
hoffen|to hope
behaupten|to claim *
bemerken|to notice *
erfahren|to find out *
teilnehmen|to take part
sich beeilen|to hurry
sich ausruhen|to rest
wachsen|to grow
heiraten|to marry
erziehen|to bring up (children) *
schützen|to protect *
schaffen|to manage (to do something)
bestehen|to pass (an exam)
durchfallen|to fail (an exam)
wiederholen|to repeat
übersetzen|to translate
pünktlich|punctual
gemütlich|cosy
ruhig|quiet
sauber|clean
schmutzig|dirty
bequem|comfortable
eng|narrow
breit|wide
günstig|inexpensive
kostenlos|free of charge
ehrlich|honest
höflich|polite
neugierig|curious
zufrieden|satisfied
enttäuscht|disappointed
überrascht|surprised
stolz|proud
fleißig|hard-working
faul|lazy
geduldig|patient
ordentlich|tidy
gefährlich|dangerous
notwendig|necessary *
selten|rarely
häufig|frequently
ungefähr|approximately
sofort|immediately
bereits|already *
trotzdem|nevertheless *
deshalb|therefore
außerdem|moreover *
eigentlich|actually
wahrscheinlich|probably
leider|unfortunately
schließlich|finally *
gleichzeitig|at the same time *
allerdings|however *
sogar|even
kaum|hardly *
nass|wet
trocken|dry
bewölkt|cloudy
verheiratet|married
ledig|single (unmarried)
berufstätig|employed *
arbeitslos|unemployed
selbstständig|self-employed *
erfolgreich|successful
anstrengend|exhausting
spannend|exciting
langweilig|boring
lecker|delicious
scharf|spicy
satt|full (after eating)
durstig|thirsty
fremd|foreign
beliebt|popular
verboten|forbidden
"""
GRAMMAR = """
A2|Ich warte ___ den Bus.|auf;an;für;über
A2|Sie interessiert sich ___ Kunst.|für;an;über;auf
B1|Wir freuen uns schon ___ die Ferien. (in the future)|auf;über;an;für
B1|Er hat sich sehr ___ das Geschenk gefreut. (received)|über;auf;an;von
A2|Ich denke oft ___ dich.|an;über;auf;von
A2|Kannst du mir ___ den Hausaufgaben helfen?|bei;mit;zu;für
A2|Sie hat Angst ___ Hunden.|vor;von;für;über
B1|Er träumt ___ einem neuen Auto.|von;über;an;auf
B1|Wir diskutieren ___ das Problem.|über;an;von;auf
B1|Ich habe mich ___ die Stelle beworben.|um;für;auf;an
B1|Er kümmert sich ___ seine Mutter.|um;für;über;an
B1|Ich habe mich ___ den Lärm geärgert.|über;auf;von;an
A2|Sie nimmt ___ dem Kurs teil.|an;in;bei;zu
B1|Das hängt ___ dem Wetter ab.|von;an;auf;mit
B1|Ich habe mich ___ das Leben hier gewöhnt.|an;auf;zu;mit
B1|Er bittet mich ___ Hilfe.|um;für;nach;auf
A2|Die Touristin fragt ___ dem Weg.|nach;um;für;von
A2|Wir gratulieren dir ___ Geburtstag.|zum;für den;am;beim
A2|Ich entschuldige mich ___ die Verspätung.|für;über;wegen;um
B1|Er beschwert sich ___ das kalte Essen.|über;von;gegen;auf
B1|Sie verabredet sich ___ ihrer Freundin.|mit;zu;bei;an
B1|Ich erinnere mich gern ___ meine Kindheit.|an;von;über;auf
B1|Du musst dich gut ___ die Prüfung vorbereiten.|auf;für;zu;an
B1|Er ist stolz ___ seine Tochter.|auf;über;von;an
A2|Wir sind ___ dem Ergebnis zufrieden.|mit;von;über;auf
A2|Ich lege das Buch auf ___ Tisch.|den;dem;der;des
A2|Das Buch liegt auf ___ Tisch.|dem;den;der;das
A2|Wir gehen heute in ___ Kino.|das;dem;den;der
A2|Die Kinder sind in ___ Schule.|der;die;den;das
A2|Er hängt das Bild an ___ Wand.|die;der;den;dem
A2|Das Bild hängt an ___ Wand.|der;die;den;dem
A2|Ich fahre mit ___ Bus zur Arbeit.|dem;den;der;das
A2|Das Geschenk ist für ___ Vater.|meinen;meinem;mein;meines
A2|Wir gehen durch ___ Park.|den;dem;der;das
A2|Seit ___ Jahr lerne ich Deutsch.|einem;ein;einen;eines
A2|Nach ___ Arbeit gehe ich einkaufen.|der;die;den;dem
B1|Trotz ___ Regens gehen wir spazieren.|des;dem;den;der
B1|Wegen ___ Streiks fährt heute kein Zug.|des;dem;den;das
A2|Ohne ___ Jacke ist es zu kalt.|eine;einer;einem;einen
A2|Ich wohne noch bei ___ Eltern.|meinen;meine;meiner;meinem
A2|Mein Kollege kommt aus ___ Türkei.|der;die;dem;den
A2|Im Sommer fahren wir ___ Schweiz.|in die;nach;zur;in der
A2|Ich fliege morgen ___ Berlin.|nach;in;zu;bei
A2|Ich gehe heute Nachmittag ___ Arzt.|zum;beim;nach;in den
A2|Sie ist gerade ___ Arzt.|beim;zum;im;am
B1|Während ___ Films ist er eingeschlafen.|des;dem;den;der
A2|Stell die Milch bitte in ___ Kühlschrank!|den;dem;der;das
A2|Die Katze schläft unter ___ Bett.|dem;das;den;der
A2|Gestern ___ ich ins Kino gegangen.|bin;habe;war;hatte
A2|Wir ___ lange auf den Bus gewartet.|haben;sind;werden;waren
A2|Er ist mit dem Auto nach Hause ___.|gefahren;gefahrt;gefuhren;fuhr
A2|Hast du die E-Mail schon ___?|geschrieben;geschreibt;geschriebt;schrieb
A2|Das habe ich nicht ___.|gewusst;gewisst;gewissen;wusste
A2|Als Kind ___ ich jeden Tag Fußball.|spielte;spiele;gespielt;spielt
A2|Früher ___ er in Köln.|wohnte;wohnt;gewohnt;wohne
A2|___ du mir bitte kurz helfen?|Könntest;Konntest;Könnt;Gekonnt
B1|Wenn ich mehr Zeit ___, würde ich öfter reisen.|hätte;habe;hatte;hätten
B1|Wenn ich reich ___, würde ich ein Haus am Meer kaufen.|wäre;bin;war;würde
B1|An deiner Stelle ___ ich zum Arzt gehen.|würde;werde;wurde;wäre
A2|Morgen ___ es wahrscheinlich regnen.|wird;würde;wurde;werden
B1|Das Haus ___ 1990 gebaut.|wurde;wird;würde;worden
B1|Hier ___ nicht geraucht.|wird;werden;würde;ist
B1|Die Briefe ___ gestern verschickt.|wurden;wurde;werden;worden
A2|Ich ___ jeden Tag um 7 Uhr auf.|stehe;steht;stehst;stehen
A2|Der Zug ___ um 8 Uhr ab.|fährt;fahrt;fahren;fährst
A2|___ du Deutsch?|Sprichst;Sprechst;Spricht;Sprecht
A2|Er ___ jeden Morgen die Zeitung.|liest;lest;lese;lesen
A2|Ich ___ heute leider nicht kommen.|kann;könne;kannst;können
A2|Du ___ hier nicht parken.|darfst;darf;dürft;dürfst
A2|Wir ___ morgen früh aufstehen.|müssen;muss;müsst;musst
A2|Als ich jung war, ___ ich nicht schwimmen.|konnte;kann;könnte;gekonnt
B1|Er hat sein Auto reparieren ___.|lassen;gelassen;lässt;ließ
B1|Nachdem er gegessen ___, ging er spazieren.|hatte;hat;war;habe
A2|Ich bleibe zu Hause, weil ich krank ___.|bin;sein;ist;bist
A2|Er sagt, dass er morgen ___.|kommt;kommen;komm;kommst
A2|Ich lerne Deutsch, ___ ich in Berlin arbeiten möchte.|weil;denn;deshalb;trotzdem
A2|Ich bin müde, ___ gehe ich früh ins Bett.|deshalb;weil;denn;obwohl
B1|___ er krank ist, geht er zur Arbeit.|Obwohl;Weil;Trotzdem;Deshalb
B1|Es regnet. ___ gehen wir spazieren.|Trotzdem;Obwohl;Weil;Damit
B1|Ich spare Geld, ___ ich mir ein Auto kaufen kann.|damit;um;weil;dass
B1|Ich spare Geld, ___ mir ein Auto zu kaufen.|um;damit;für;weil
A2|Ich weiß nicht, ___ er morgen kommt.|ob;wenn;als;denn
A2|___ ich ein Kind war, wohnte ich in Wien.|Als;Wenn;Wann;Ob
A2|Immer ___ ich nach Hause komme, koche ich Tee.|wenn;als;wann;ob
A2|Kannst du mir sagen, ___ der Zug abfährt?|wann;wenn;als;denn
A2|Which sentence is correct?|Heute Abend gehe ich ins Kino.;Heute Abend ich gehe ins Kino.;Heute Abend ins Kino ich gehe.;Heute Abend gehe ins Kino ich.
A2|Which sentence is correct?|Ich weiß, dass du Recht hast.;Ich weiß, dass du hast Recht.;Ich weiß, dass hast du Recht.;Ich weiß, du dass Recht hast.
A2|Which sentence is correct?|Er ruft seine Mutter an.;Er anruft seine Mutter.;Er ruft an seine Mutter.;Er seine Mutter anruft.
B1|Which sentence is correct?|Ich habe keine Lust, ins Kino zu gehen.;Ich habe keine Lust, zu ins Kino gehen.;Ich habe keine Lust, ins Kino gehen zu.;Ich habe keine Lust, zu gehen ins Kino.
A2|Which sentence is correct?|Kannst du mich morgen anrufen?;Kannst du mich morgen rufen an?;Kannst du anrufen mich morgen?;Kannst du mich morgen anzurufen?
B1|Which sentence is correct?|Weißt du, wo der Bahnhof ist?;Weißt du, wo ist der Bahnhof?;Weißt du, wo der Bahnhof sein?;Weißt du, der Bahnhof wo ist?
B1|Which sentence is correct?|Weil es regnet, bleiben wir zu Hause.;Weil es regnet, wir bleiben zu Hause.;Weil regnet es, bleiben wir zu Hause.;Weil es regnet, zu Hause wir bleiben.
A2|Ich habe einen ___ Hund. (klein)|kleinen;kleiner;kleine;kleinem
A2|Das ist ein ___ Auto. (neu)|neues;neue;neuer;neuen
A2|Sie trägt eine ___ Jacke. (rot)|rote;roten;roter;rotes
B1|Mit dem ___ Zug sind wir in zwei Stunden da. (schnell)|schnellen;schnelle;schneller;schnelles
A2|Der ___ Mann dort ist mein Lehrer. (alt)|alte;alten;alter;altes
B1|Ich trinke morgens gern ___ Kaffee. (heiß)|heißen;heißer;heißes;heiße
A2|Wir wohnen in einer ___ Wohnung. (groß)|großen;große;großer;großes
B1|Das sind meine ___ Freunde. (gut – superlative)|besten;beste;bester;bestes
B1|Ich gebe dem ___ Kind ein Eis. (klein)|kleinen;kleine;kleines;kleinem
A2|So ein ___ Wetter heute! (schön)|schönes;schöne;schöner;schönen
A2|Berlin ist ___ als München. (groß)|größer;großer;mehr groß;am größten
A2|Im Juli ist es am ___. (warm)|wärmsten;wärmer;warmsten;wärmste
A2|Ich trinke ___ Tee als Kaffee. (gern)|lieber;gerner;mehr gern;am liebsten
A2|Er läuft so schnell ___ sein Bruder.|wie;als;dann;wenn
A2|Sie ist zwei Jahre älter ___ ich.|als;wie;dann;so
A2|Das ist das ___ Restaurant der Stadt. (gut)|beste;besste;gute;bessere
A2|Ich helfe ___ alten Mann.|dem;den;der;des
A2|Kannst du ___ bitte helfen? (ich)|mir;mich;mein;ich
A2|Ich besuche ___ morgen. (du)|dich;dir;du;dein
A2|Wie geht es ___? (Sie, formal)|Ihnen;Sie;Ihr;Ihre
A2|Das Buch gehört ___. (er)|ihm;ihn;er;sein
A2|Ich schenke ___ Blumen. (sie, singular)|ihr;sie;ihn;ihre
B1|Der Mann, ___ dort steht, ist mein Vater.|der;den;dem;die
B1|Die Frau, ___ ich geholfen habe, war sehr nett.|der;die;den;dem
B1|Das Buch, ___ ich gerade lese, ist spannend.|das;dem;der;den
B1|Der Freund, mit ___ ich Tennis spiele, heißt Tom.|dem;den;der;dessen
B1|Die Gäste, ___ ich eingeladen habe, kommen um acht.|die;denen;den;der
B1|Das ist der Nachbar, ___ Auto gestohlen wurde.|dessen;deren;dem;der
B1|Die Kollegen, mit ___ ich arbeite, sind sehr nett.|denen;den;die;deren
A2|Ich wasche ___ die Hände.|mir;mich;mein;ich
A2|Er freut ___ auf den Urlaub.|sich;ihn;ihm;er
A2|Wir treffen ___ um acht vor dem Kino.|uns;unser;wir;euch
A2|Beeil ___! Der Zug fährt gleich.|dich;dir;du;sich
B1|___ Buch ist das? – Das ist meins.|Wessen;Wem;Wen;Wer
A2|___ hast du das Geschenk gegeben?|Wem;Wen;Wer;Wessen
A2|___ hast du gestern getroffen?|Wen;Wem;Wer;Wessen
A2|Tut mir leid, ich habe heute ___ Zeit.|keine;nicht;kein;keinen
A2|Er hat ___ Bruder.|keinen;kein;keine;nicht
A2|Das ist ___ mein Problem.|nicht;kein;keine;keinen
B1|___ mehr du übst, ___ besser wird dein Deutsch.|Je … desto;Wenn … dann;Als … desto;Je … so viel
B1|Ich trinke ___ Kaffee ___ Tee. (neither … nor)|weder … noch;entweder … oder;sowohl … als auch;nicht … sondern
B1|Du kannst ___ heute ___ morgen kommen. (either … or)|entweder … oder;weder … noch;sowohl … als auch;je … desto
B1|Er spricht ___ Englisch ___ Spanisch. (both … and)|sowohl … als auch;weder … noch;entweder … oder;je … desto
A2|Das Auto ist nicht rot, ___ blau.|sondern;aber;oder;denn
B1|___ des schlechten Wetters sind wir zu Hause geblieben.|Wegen;Weil;Obwohl;Denn
B1|Statt ins Kino ___ gehen, bleibe ich heute zu Hause.|zu;um;für;an
B1|Er tut so, ___ ob er nichts wüsste.|als;wie;wenn;dass
A2|Es ist wichtig, genug Wasser zu ___.|trinken;trinkt;getrunken;trinke
B1|Ich wünschte, ich ___ mehr Zeit.|hätte;habe;hatte;haben
B1|Die Tür ___ nur von innen geöffnet werden.|kann;können;kannst;könnt
B1|Das Paket ist gestern ___ worden.|geliefert;liefern;geliefern;lieferte
A2|Der Film hat schon ___.|angefangen;geanfangen;anfangt;anfing
A2|Ich bin heute sehr früh ___.|aufgestanden;aufgesteht;geaufstanden;aufstand
A2|Wir ___ in Frankfurt umgestiegen.|sind;haben;hat;ist
A2|Was ist denn ___?|passiert;gepassiert;passieren;passierte
A2|Hast du die Rechnung schon ___?|bezahlt;gebezahlt;bezahlen;bezahlte
A2|Ich habe meinen Schlüssel ___.|verloren;verliert;geverloren;verlierte
A2|Er ___ im Urlaub sehr viel geschlafen.|hat;ist;war;wird
A2|Das Kind ___ in einem Jahr 10 cm gewachsen.|ist;hat;wird;habe
B1|Ich habe vergessen, dich ___.|anzurufen;zu anrufen;anrufen;angerufen
B1|Es fängt gleich an ___ regnen.|zu;um;—;für
B1|Er hat versprochen, pünktlich ___ sein.|zu;um;—;für
A2|Wie spät ist es? – Es ist Viertel ___ drei. (2:45)|vor;nach;um;bis
A2|Der Kurs dauert ___ Montag bis Freitag.|von;ab;seit;um
A2|Das Geschäft ist ___ 9 Uhr geöffnet.|ab;an;von;bei
A2|Ich lerne ___ drei Jahren Deutsch.|seit;vor;ab;für
A2|___ zwei Tagen war ich beim Arzt.|Vor;Seit;Ab;Nach
A2|Ich habe am Wochenende ___ Hause gearbeitet.|zu;nach;im;bei
A2|Nach dem Kino gehen wir ___ Hause.|nach;zu;in;bei
A2|Wir treffen uns ___ Samstag.|am;im;um;in
A2|Der Unterricht beginnt ___ 9 Uhr.|um;am;im;an
A2|Ich habe ___ Juni Geburtstag.|im;am;um;in dem Monat
A2|Hier ist ___ Hemd, das dir gefällt.|das;der;die;den
A2|Hast du ___ neue Nachbarin schon kennengelernt?|die;der;den;das
B1|Ich muss eine Entscheidung ___.|treffen;machen;tun;nehmen
B1|Sie hat mir einen guten Rat ___.|gegeben;gemacht;gesagt;genommen
B1|Wir müssen auf die Umwelt Rücksicht ___.|nehmen;geben;machen;halten
B1|Kannst du mir einen Gefallen ___?|tun;machen;geben;halten
A2|Ich habe Hunger. ___ wir etwas essen?|Sollen;Soll;Sollt;Sollst
A2|Mein Bruder ___ Arzt werden.|will;willst;wollen;wollt
A2|Ihr ___ leise sein, das Baby schläft.|müsst;müssen;musst;muss
A2|Wo ___ du geboren?|bist;hast;wirst;warst du
A2|Wann ___ der Zug in Hamburg an?|kommt;kommst;ankommt;kommen
B1|Obwohl er viel gelernt hat, ___ er durchgefallen.|ist;hat;wird;war es
B1|Er sieht aus, ___ wäre er müde.|als;wie;ob;wenn
B1|Das Essen war ___ lecker, ___ ich noch eine Portion bestellt habe.|so … dass;zu … dass;so … damit;sehr … dass
B1|Je länger ich warte, ___ nervöser werde ich.|desto;als;wie;so
"""

def gid(prefix, text):
    return prefix + hashlib.sha1(text.encode()).hexdigest()[:10]

items = []
seen = set()
for line in VOCAB.strip().splitlines():
    de, en = line.split("|")
    lvl = "B1" if en.endswith(" *") else "A2"
    en = en.removesuffix(" *").strip()
    assert de not in seen, de; seen.add(de)
    items.append({"id": "v:" + de, "kind": "VOCAB", "level": lvl, "prompt": de, "answer": en, "options": []})
    art, _, noun = de.partition(" ")
    if art in ("der", "die", "das") and noun:
        items.append({"id": "a:" + noun, "kind": "ARTICLE", "level": lvl, "prompt": noun, "answer": art, "options": ["der", "die", "das"]})
for line in GRAMMAR.strip().splitlines():
    lvl, q, opts = line.split("|")
    options = opts.split(";")
    assert len(options) == 4 and len(set(options)) == 4, line
    items.append({"id": gid("g:", q + options[0]), "kind": "GRAMMAR", "level": lvl, "prompt": q, "answer": options[0], "options": options})

assert len({i["id"] for i in items}) == len(items)
out = "/Users/dtraihn/Documents/Github/PUC/app/src/main/assets/german_bank.json"
json.dump(items, open(out, "w"), ensure_ascii=False, separators=(",", ":"))
from collections import Counter
print(len(items), Counter(i["kind"] for i in items), Counter(i["level"] for i in items))
