# Holiday data sources

Checked 2026-10-25 by web search only. The cloud container could not open openholidaysapi.org,
date.nager.at or feiertage-api.de (network policy), so the notes on Nager.Date and
feiertage-api.de come from secondary sources and are **not confirmed**.

OpenHolidays is confirmed (2026-10-25): the maintainer sent the ODbL 1.0 text and the FAQ answer: "the
OpenHolidays API project is open data, its use is free of charge and also permitted in commercial
projects. All data comes from public sources ... The processed data is licensed under the ODC Open
Database Licence (ODbL)."

## Decision

- Public holidays (gesetzliche Feiertage): computed by rules in `:core` (Easter rule plus a table
  of Länder rules), covered by unit tests. No data licence involved.
- School holidays (Schulferien): a bundled, versioned data file per year, built from an
  OpenHolidays export and cross-checked with the KMK and Länder ministries each year. Never fetched
  at runtime.
- Nager.Date and feiertage-api.de: only as a cross-check for the public holiday tests, not
  bundled.

## What was found (unconfirmed)

| Source | Covers | Licence found |
| --- | --- | --- |
| OpenHolidays API | DE/AT/CH public and school holidays, subdivisions, JSON and iCal | Open data, free also for commercial use; processed data under ODbL (attribution, share-alike), per a third-party site |
| Nager.Date | Public holidays only | Licence of the data unclear; the NuGet package lists MIT (third-party site); offline use needs a sponsor key |
| feiertage-api.de | German public holidays | No terms found |

## What ODbL means for the bundled school holiday file

Reading of the licence text (not legal advice):

- The bundled file is a Derivative Database (a substantial part of the contents, reformatted), and
  the app is published. So the file stays under ODbL (section 4.4), also in the repo. The GPL-3.0-or-later
  code is a separate work: ODbL does not cover computer programs (section 2.3 a).
- The file sits in its own directory with a copy of the ODbL text (or its URI) and the notices
  kept intact (section 4.2). The file is committed to the public repo, which also covers the
  machine-readable copy for recipients (section 4.6).
- Credit in the app's about screen, and in the ICS export for Produced Works that are shared
  (section 4.3): "Contains information from OpenHolidays API (https://www.openholidaysapi.org),
  which is made available here under the Open Database License (ODbL)."
- Rule-computed public holidays are our own code and need none of this.
- Data that is added to the file (for example corrections from KMK) must stay ODbL compatible
  (section 4.4 d).

## Bundled file (built 2026-10-25)

`data/school-holidays/school-holidays.txt` (2026 to 2029, all 16 Länder, built with
`tools/update-school-holidays.py`), its ODbL text and a README with the provenance, what was left
out and the yearly update steps are in `data/school-holidays/`. The app bundles that directory as
assets, so the licence travels with the APK. `SchoolHolidayData.CREDIT` in `:core` is the credit
line for the about screen and the ICS export (M3 and M4 show it; this PR does not).

OpenHolidaysapi.org and kmk.org were reachable from the cloud container on 2026-10-25. The result
of the KMK cross-check is in the README of that directory.
